package com.synapse.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.synapse.ai.DocumentationService;
import com.synapse.ai.LocalLlm;
import com.synapse.analysis.graph.CodeGraph;
import com.synapse.analysis.graph.GraphBuilder;
import com.synapse.analysis.ingest.FileScanner;
import com.synapse.analysis.ingest.FileScanner.ScanResult;
import com.synapse.analysis.ingest.FileScanner.SourceFile;
import com.synapse.analysis.ingest.SourceFetcher;
import com.synapse.analysis.ingest.SourceFetcher.FetchException;
import com.synapse.analysis.parse.FileModel;
import com.synapse.analysis.parse.SourceParser;
import com.synapse.config.SynapseProperties;
import com.synapse.docs.DocumentRepository;
import com.synapse.graph.GraphRepository;
import com.synapse.project.ProjectRepository;
import com.synapse.project.ProjectRepository.Status;

import tools.jackson.databind.ObjectMapper;

/** Runs the analysis pipeline in the background: fetch, scan, parse, build graph, write docs. */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    public enum Depth { QUICK, STANDARD, DEEP }

    /** Where the code comes from. The token is used for one clone and then discarded. */
    public record Source(String type, String repoUrl, String branch, String token, Path zipFile) {
        @Override
        public String toString() {
            return "Source[" + type + ", " + repoUrl + "]"; // never print the token
        }
    }

    private final Executor executor;
    private final SourceFetcher fetcher;
    private final FileScanner scanner;
    private final ProjectRepository projects;
    private final GraphRepository graphs;
    private final DocumentRepository documents;
    private final DocumentationService documentation;
    private final LocalLlm llm;
    private final SynapseProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public AnalysisService(@Qualifier("analysisExecutor") Executor executor, SourceFetcher fetcher, FileScanner scanner,
                           ProjectRepository projects, GraphRepository graphs, DocumentRepository documents,
                           DocumentationService documentation, LocalLlm llm, SynapseProperties properties) {
        this.executor = executor;
        this.fetcher = fetcher;
        this.scanner = scanner;
        this.projects = projects;
        this.graphs = graphs;
        this.documents = documents;
        this.documentation = documentation;
        this.llm = llm;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void markInterruptedRuns() {
        int count = projects.failInterrupted();
        if (count > 0) log.info("Marked {} interrupted analyses as failed", count);
    }

    public void start(long projectId, String projectName, Depth depth, Source source) {
        executor.execute(() -> run(projectId, projectName, depth, source));
    }

    private void run(long projectId, String projectName, Depth depth, Source source) {
        long started = System.currentTimeMillis();
        Path workspace = null;
        try {
            workspace = fetcher.workspaceFor(projectId);
            if ("GITHUB".equals(source.type())) {
                projects.updateStatus(projectId, Status.FETCHING, 3, "Cloning " + source.repoUrl());
                fetcher.cloneGitHub(source.repoUrl(), source.branch(), source.token(), workspace);
            } else {
                projects.updateStatus(projectId, Status.FETCHING, 3, "Unpacking ZIP archive");
                fetcher.unzip(source.zipFile(), workspace);
            }
            Path root = fetcher.effectiveRoot(workspace);

            projects.updateStatus(projectId, Status.SCANNING, 12, "Scanning files");
            ScanResult scan = scanner.scan(root);
            if (scan.files().isEmpty()) {
                throw new FetchException("No readable source files were found in this repository.");
            }

            List<FileModel> models = parseAll(projectId, scan.files());

            projects.updateStatus(projectId, Status.BUILDING_GRAPH, 52, "Linking " + models.size() + " files into the knowledge graph");
            CodeGraph graph = new GraphBuilder().build(projectName, scan.files(), models);
            documents.deleteAll(projectId);
            graphs.replaceGraph(projectId, graph);

            int filesToSummarise = switch (depth) {
                case QUICK -> properties.analysis().aiFiles().quick();
                case STANDARD -> properties.analysis().aiFiles().standard();
                case DEEP -> properties.analysis().aiFiles().deep();
            };
            projects.updateStatus(projectId, Status.GENERATING_DOCS, 60, "Writing documentation with " + llm.model());
            int[] lastReported = {-1};
            DocumentationService.Outcome outcome = documentation.generate(projectId, projectName, source.repoUrl(), graph, models,
                scan.files(), filesToSummarise, pct -> {
                    int overall = 60 + (int) (pct * 0.39);
                    if (overall != lastReported[0]) {
                        lastReported[0] = overall;
                        projects.updateStatus(projectId, Status.GENERATING_DOCS, overall,
                            pct < 86 ? "Summarising key files with AI" : "Writing project documentation");
                    }
                });

            Map<String, Object> stats = stats(scan, models, graph, outcome, started);
            String message = outcome.aiUsed()
                ? "Analysis complete"
                : "Analysis complete. AI documentation was skipped because Ollama is not running.";
            projects.complete(projectId, json.writeValueAsString(stats), outcome.aiUsed(), message);
            log.info("Project {} analysed in {} s", projectId, (System.currentTimeMillis() - started) / 1000);
        } catch (FetchException e) {
            projects.fail(projectId, e.getMessage());
        } catch (Throwable e) {
            log.error("Analysis of project {} failed", projectId, e);
            projects.fail(projectId, "Analysis failed unexpectedly (" + e.getClass().getSimpleName() + "). Check the server log.");
        } finally {
            try {
                if (workspace != null) fetcher.deleteRecursively(workspace);
                if (source.zipFile() != null) Files.deleteIfExists(source.zipFile());
            } catch (IOException e) {
                log.warn("Could not clean up workspace for project {}", projectId);
            }
        }
    }

    private List<FileModel> parseAll(long projectId, List<SourceFile> files) {
        SourceParser parser = new SourceParser();
        List<FileModel> models = new ArrayList<>(files.size());
        int lastPct = -1;
        for (int i = 0; i < files.size(); i++) {
            SourceFile f = files.get(i);
            models.add(parser.parse(f.path(), f.language(), f.content()));
            int pct = 15 + (int) (35.0 * (i + 1) / files.size());
            if (pct != lastPct) {
                lastPct = pct;
                projects.updateStatus(projectId, Status.PARSING, pct, "Parsing " + (i + 1) + " of " + files.size() + " files");
            }
        }
        return models;
    }

    private Map<String, Object> stats(ScanResult scan, List<FileModel> models, CodeGraph graph,
                                      DocumentationService.Outcome outcome, long started) {
        Map<String, Map<String, Integer>> languages = new TreeMap<>();
        int totalLines = 0;
        int grammarParsed = 0;
        for (FileModel m : models) {
            Map<String, Integer> entry = languages.computeIfAbsent(m.language().displayName(), k -> new LinkedHashMap<>());
            entry.merge("files", 1, Integer::sum);
            entry.merge("lines", m.lineCount(), Integer::sum);
            if (m.language().isCode()) totalLines += m.lineCount();
            if (m.parsedWithGrammar()) grammarParsed++;
        }
        Map<String, Integer> nodeKinds = new TreeMap<>();
        for (CodeGraph.Node n : graph.nodes()) nodeKinds.merge(n.kind, 1, Integer::sum);
        Map<String, Integer> edgeKinds = new TreeMap<>();
        for (CodeGraph.Edge e : graph.edges()) edgeKinds.merge(e.kind(), 1, Integer::sum);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("files", models.size());
        stats.put("linesOfCode", totalLines);
        stats.put("languages", languages);
        stats.put("nodes", nodeKinds);
        stats.put("edges", edgeKinds);
        stats.put("totalNodes", graph.nodes().size());
        stats.put("totalEdges", graph.edgeCount());
        stats.put("syntaxTreeParsedFiles", grammarParsed);
        stats.put("skippedFiles", scan.skipped());
        stats.put("truncated", scan.truncated());
        stats.put("aiModel", llm.model());
        stats.put("aiSummarisedFiles", outcome.summarisedFiles());
        stats.put("aiFailedCalls", outcome.failedCalls());
        stats.put("durationSeconds", (System.currentTimeMillis() - started) / 1000);
        return stats;
    }
}
