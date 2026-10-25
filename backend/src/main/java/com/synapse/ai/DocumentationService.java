package com.synapse.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.synapse.analysis.graph.CodeGraph;
import com.synapse.analysis.graph.CodeGraph.Node;
import com.synapse.analysis.ingest.FileScanner.SourceFile;
import com.synapse.analysis.parse.FileModel;
import com.synapse.docs.DocumentRepository;
import com.synapse.graph.GraphRepository;

import tools.jackson.databind.JsonNode;

/**
 * Produces the AI documentation for an analysed project: per-file and per-symbol summaries stored on
 * the graph, plus README, architecture, business-logic and API documents.
 */
@Service
public class DocumentationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentationService.class);
    private static final int CODE_CHARS = 9_000;

    private static final String SYSTEM = """
        You are a senior software engineer documenting a legacy codebase for new developers.
        Be concrete and accurate. Only describe behaviour that is supported by the code you are shown;
        if something is unclear, say so briefly instead of guessing. Use plain, professional English.
        """;

    private final LocalLlm llm;
    private final GraphRepository graphRepository;
    private final DocumentRepository documents;

    public DocumentationService(LocalLlm llm, GraphRepository graphRepository, DocumentRepository documents) {
        this.llm = llm;
        this.graphRepository = graphRepository;
        this.documents = documents;
    }

    public record Outcome(boolean aiUsed, int summarisedFiles, int failedCalls) {
    }

    private record Insight(String path, String summary, List<String> rules) {
    }

    /** @param progress receives 0-100 for this stage */
    public Outcome generate(long projectId, String projectName, String sourceUrl, CodeGraph graph, List<FileModel> models,
                            List<SourceFile> files, int filesToSummarise, IntConsumer progress) {
        boolean ai = llm.status().ready();
        Map<String, String> contentByPath = files.stream().collect(Collectors.toMap(SourceFile::path, SourceFile::content, (a, b) -> a));
        Map<String, FileModel> modelByPath = models.stream().collect(Collectors.toMap(FileModel::path, m -> m, (a, b) -> a));
        Facts facts = new Facts(graph);

        List<Insight> insights = new ArrayList<>();
        int failed = 0;
        if (ai) {
            List<Node> ranked = facts.rankFiles(modelByPath).stream().limit(filesToSummarise).toList();
            Map<Integer, String> summaries = new HashMap<>();
            for (int i = 0; i < ranked.size(); i++) {
                Node file = ranked.get(i);
                progress.accept(5 + (int) (80.0 * i / Math.max(1, ranked.size())));
                try {
                    Insight insight = summariseFile(projectName, file, modelByPath.get(file.filePath), contentByPath.get(file.filePath),
                        facts, summaries);
                    insights.add(insight);
                } catch (Exception e) {
                    failed++;
                    log.warn("Summary failed for {}: {}", file.filePath, e.toString());
                }
            }
            summaries.forEach((id, text) -> graph.node(id).summary = text);
            graphRepository.updateSummaries(projectId, summaries);
        }

        progress.accept(86);
        String readme = null;
        String architecture = null;
        String business = null;
        if (ai && !insights.isEmpty()) {
            try {
                readme = llm.complete(SYSTEM, readmePrompt(projectName, sourceUrl, facts, insights));
            } catch (Exception e) {
                failed++;
                log.warn("README generation failed: {}", e.toString());
            }
            progress.accept(91);
            try {
                architecture = llm.complete(SYSTEM, architecturePrompt(projectName, facts, insights));
            } catch (Exception e) {
                failed++;
                log.warn("Architecture generation failed: {}", e.toString());
            }
            progress.accept(95);
            try {
                business = llm.complete(SYSTEM, businessPrompt(projectName, facts, insights));
            } catch (Exception e) {
                failed++;
                log.warn("Business logic generation failed: {}", e.toString());
            }
        }

        documents.upsert(projectId, "README", "README", "README.md",
            readme != null ? cleanMarkdown(readme) : fallbackReadme(projectName, sourceUrl, facts), readme != null);
        documents.upsert(projectId, "ARCHITECTURE", "Architecture", "ARCHITECTURE.md",
            (architecture != null ? cleanMarkdown(architecture) : fallbackArchitecture(projectName, facts))
                + "\n\n" + facts.moduleDiagram(), architecture != null);
        documents.upsert(projectId, "BUSINESS_LOGIC", "Business Logic", "BUSINESS_LOGIC.md",
            business != null ? cleanMarkdown(business) : fallbackBusiness(projectName, ai), business != null);
        documents.upsert(projectId, "API", "API Reference", "API.md", apiReference(projectName, facts), false);
        progress.accept(100);
        return new Outcome(ai && !insights.isEmpty(), insights.size(), failed);
    }

    // ------------------------------------------------------------------ per-file summaries

    private Insight summariseFile(String projectName, Node file, FileModel model, String content, Facts facts,
                                  Map<Integer, String> summaries) {
        List<Node> symbols = facts.symbolsIn(file.filePath);
        StringBuilder prompt = new StringBuilder();
        prompt.append("Project: ").append(projectName).append('\n');
        prompt.append("File: ").append(file.filePath).append(" (").append(file.language).append(", ")
            .append(file.endLine).append(" lines)\n");
        if (!symbols.isEmpty()) {
            prompt.append("Declared symbols:\n");
            symbols.stream().limit(40).forEach(s -> prompt.append("- ").append(s.kind.toLowerCase(Locale.ROOT)).append(' ')
                .append(s.name).append(": ").append(oneLine(s.signature, 160)).append('\n'));
        }
        List<String> endpoints = facts.endpointsIn(file.filePath);
        if (!endpoints.isEmpty()) prompt.append("HTTP endpoints: ").append(String.join(", ", endpoints)).append('\n');
        List<String> deps = facts.importNames(file.id);
        if (!deps.isEmpty()) prompt.append("Uses: ").append(String.join(", ", deps.stream().limit(20).toList())).append('\n');
        prompt.append("\nSource code").append(content.length() > CODE_CHARS ? " (truncated)" : "").append(":\n```\n")
            .append(content.length() > CODE_CHARS ? content.substring(0, CODE_CHARS) : content).append("\n```\n\n");
        prompt.append("""
            Respond with a JSON object with exactly these keys:
            {"summary": "2-3 sentences: what this file is responsible for and how it fits into the project",
             "symbols": [{"name": "<a declared symbol name from the list above>", "summary": "one sentence on what it does"}],
             "businessRules": ["a business rule, validation or workflow step this code enforces, in plain language"]}
            Use an empty array for businessRules if the file is purely technical.
            """);

        JsonNode result = llm.completeJson(SYSTEM, prompt.toString());
        String summary = result.path("summary").asText("").trim();
        if (!summary.isEmpty()) summaries.put(file.id, summary);
        Map<String, List<Node>> byName = symbols.stream().collect(Collectors.groupingBy(s -> s.name));
        for (JsonNode s : result.path("symbols")) {
            String name = s.path("name").asText("");
            String text = s.path("summary").asText("").trim();
            for (Node node : byName.getOrDefault(name, List.of())) {
                if (!text.isEmpty()) summaries.put(node.id, text);
            }
        }
        List<String> rules = new ArrayList<>();
        for (JsonNode r : result.path("businessRules")) {
            String text = r.asText("").trim();
            if (!text.isEmpty()) rules.add(text);
        }
        return new Insight(file.filePath, summary, rules);
    }

    // ------------------------------------------------------------------ project documents

    private String readmePrompt(String projectName, String sourceUrl, Facts facts, List<Insight> insights) {
        StringBuilder p = new StringBuilder();
        p.append("Write a README.md for the project \"").append(projectName).append("\".\n");
        if (sourceUrl != null) p.append("Repository: ").append(sourceUrl).append('\n');
        p.append("\nLanguages (files): ").append(facts.languageSummary()).append('\n');
        p.append("\nDeclared dependencies: ").append(String.join(", ", facts.dependencies(40))).append('\n');
        p.append("\nManifest/build files: ").append(String.join(", ", facts.manifestFiles())).append('\n');
        p.append("\nDirectory structure:\n").append(facts.tree(2, 60)).append('\n');
        appendInsights(p, insights, 6_500);
        List<String> endpoints = facts.allEndpoints(30);
        if (!endpoints.isEmpty()) p.append("\nHTTP endpoints:\n").append(String.join("\n", endpoints)).append('\n');
        p.append("""

            Write the README in Markdown with these sections:
            # <Project name>  (followed by a one-paragraph overview of what the software does and for whom)
            ## Features  (bullet list of real capabilities seen in the code)
            ## Tech Stack
            ## Project Structure  (explain the important folders)
            ## Getting Started  (prerequisites, install and run commands inferred from the build files; say if unknown)
            ## Key Components  (the most important modules/classes and their roles)
            ## API Overview  (only if there are HTTP endpoints)
            ## How It Works  (the main flow of data/requests through the system)
            Output only the Markdown document.
            """);
        return p.toString();
    }

    private String architecturePrompt(String projectName, Facts facts, List<Insight> insights) {
        StringBuilder p = new StringBuilder();
        p.append("Write an ARCHITECTURE.md describing the architecture of \"").append(projectName).append("\".\n\n");
        p.append("Modules (folder: number of files):\n").append(facts.moduleList(40)).append('\n');
        p.append("\nDependencies between modules (A -> B means code in A imports or calls code in B, with counts):\n")
            .append(String.join("\n", facts.moduleDependencies(30))).append('\n');
        p.append("\nInheritance relationships:\n").append(String.join("\n", facts.inheritance(25))).append('\n');
        p.append("\nExternal libraries: ").append(String.join(", ", facts.dependencies(40))).append('\n');
        appendInsights(p, insights, 6_000);
        p.append("""

            Write Markdown with these sections:
            # Architecture
            ## Overview  (architectural style, e.g. layered MVC, microservice, CLI, library - justify from the evidence)
            ## Modules and Responsibilities  (one subsection or bullet per important module)
            ## Module Dependencies  (how the modules depend on each other; point out tight coupling or cycles)
            ## Data Flow  (how a typical request or job moves through the modules)
            ## External Integrations  (databases, frameworks, third-party services)
            ## Observations for Maintainers  (risks, hotspots, suggestions grounded in the evidence)
            Do not include a diagram; one is appended automatically. Output only the Markdown document.
            """);
        return p.toString();
    }

    private String businessPrompt(String projectName, Facts facts, List<Insight> insights) {
        StringBuilder p = new StringBuilder();
        p.append("Write BUSINESS_LOGIC.md explaining the business logic of \"").append(projectName).append("\".\n\n");
        List<String> rules = insights.stream().flatMap(i -> i.rules().stream().map(r -> "- " + r + "  (" + i.path() + ")"))
            .distinct().limit(60).toList();
        if (!rules.isEmpty()) p.append("Business rules found in the code:\n").append(String.join("\n", rules)).append('\n');
        p.append("\nDomain classes: ").append(String.join(", ", facts.domainClasses(50))).append('\n');
        List<String> endpoints = facts.allEndpoints(30);
        if (!endpoints.isEmpty()) p.append("\nHTTP endpoints:\n").append(String.join("\n", endpoints)).append('\n');
        appendInsights(p, insights, 5_000);
        p.append("""

            Write Markdown with these sections:
            # Business Logic
            ## Domain Overview  (what business problem the software solves)
            ## Core Entities  (the main domain concepts and what they represent)
            ## Key Workflows  (numbered steps for each important use case, naming the code that performs each step)
            ## Business Rules and Validations  (bullet list; cite the file in parentheses)
            ## Glossary  (domain terms used in the code)
            If the project is a technical library with little business logic, say so and describe its core behaviour instead.
            Output only the Markdown document.
            """);
        return p.toString();
    }

    private static void appendInsights(StringBuilder p, List<Insight> insights, int budget) {
        p.append("\nSummaries of the most important files:\n");
        int used = 0;
        for (Insight i : insights) {
            String line = "- " + i.path() + ": " + i.summary() + "\n";
            if (used + line.length() > budget) break;
            p.append(line);
            used += line.length();
        }
    }

    // ------------------------------------------------------------------ deterministic documents

    private static String fallbackReadme(String projectName, String sourceUrl, Facts facts) {
        StringBuilder md = new StringBuilder("# ").append(projectName).append("\n\n");
        md.append("> Generated from static analysis. Start Ollama and re-run the analysis for AI-written documentation.\n\n");
        if (sourceUrl != null) md.append("Source: ").append(sourceUrl).append("\n\n");
        md.append("## Languages\n\n").append(facts.languageSummary()).append("\n\n");
        md.append("## Project Structure\n\n```\n").append(facts.tree(2, 80)).append("```\n\n");
        List<String> deps = facts.dependencies(60);
        if (!deps.isEmpty()) md.append("## Dependencies\n\n").append(deps.stream().map(d -> "- " + d).collect(Collectors.joining("\n"))).append("\n\n");
        md.append("## Key Components\n\n");
        facts.topClasses(20).forEach(n -> md.append("- **").append(n.name).append("** — `").append(n.filePath).append("`\n"));
        return md.toString();
    }

    private static String fallbackArchitecture(String projectName, Facts facts) {
        return "# Architecture\n\n## Modules\n\n```\n" + facts.moduleList(60) + "```\n\n## Module Dependencies\n\n"
            + facts.moduleDependencies(40).stream().map(d -> "- " + d).collect(Collectors.joining("\n")) + "\n";
    }

    private static String fallbackBusiness(String projectName, boolean aiAvailable) {
        return "# Business Logic\n\n" + (aiAvailable
            ? "The AI model could not produce a business-logic description for this project. Try running the analysis again."
            : "Business-logic documentation is written by the local AI model. Start Ollama (`ollama serve`) and run the analysis again.")
            + "\n";
    }

    private static String apiReference(String projectName, Facts facts) {
        List<Node> endpoints = facts.endpointNodes();
        StringBuilder md = new StringBuilder("# API Reference\n\n");
        if (endpoints.isEmpty()) {
            return md.append("No HTTP endpoints were detected in ").append(projectName).append(".\n").toString();
        }
        md.append(endpoints.size()).append(" HTTP endpoint(s) detected.\n\n| Method | Path | Handler | Location | Description |\n|---|---|---|---|---|\n");
        for (Node e : endpoints) {
            Node handler = facts.handlerOf(e.id);
            String[] parts = e.name.split(" ", 2);
            md.append("| `").append(parts[0]).append("` | `").append(parts.length > 1 ? parts[1] : "/").append("` | ")
                .append(handler == null ? "—" : "`" + handler.name + "`").append(" | `").append(e.filePath).append(':').append(e.startLine)
                .append("` | ").append(handler == null || handler.summary == null ? "" : oneLine(handler.summary, 200).replace("|", "\\|"))
                .append(" |\n");
        }
        return md.toString();
    }

    private static String cleanMarkdown(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:markdown|md)?\\s*\\n", "");
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim() + "\n";
    }

    static String oneLine(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    // ------------------------------------------------------------------ graph facts

    /** Read-only views over the graph that the prompts and fallback documents need. */
    static final class Facts {
        private final CodeGraph graph;
        private final Map<Integer, List<CodeGraph.Edge>> out = new HashMap<>();
        private final Map<Integer, List<CodeGraph.Edge>> in = new HashMap<>();
        private final Map<String, List<Node>> symbolsByFile = new HashMap<>();
        private final Map<Integer, Integer> parentOf = new HashMap<>();

        Facts(CodeGraph graph) {
            this.graph = graph;
            for (CodeGraph.Edge e : graph.edges()) {
                out.computeIfAbsent(e.source(), k -> new ArrayList<>()).add(e);
                in.computeIfAbsent(e.target(), k -> new ArrayList<>()).add(e);
                if (e.kind().equals(CodeGraph.CONTAINS)) parentOf.putIfAbsent(e.target(), e.source());
            }
            for (Node n : graph.nodes()) {
                if (isSymbol(n)) symbolsByFile.computeIfAbsent(n.filePath, k -> new ArrayList<>()).add(n);
            }
        }

        private static boolean isSymbol(Node n) {
            return n.kind.equals(CodeGraph.CLASS) || n.kind.equals(CodeGraph.FUNCTION) || n.kind.equals(CodeGraph.METHOD);
        }

        List<Node> symbolsIn(String path) {
            return symbolsByFile.getOrDefault(path, List.of());
        }

        /** Files ordered by how central they are: declarations, inbound dependencies, endpoints and size. */
        List<Node> rankFiles(Map<String, FileModel> models) {
            Map<Integer, Double> score = new HashMap<>();
            for (Node n : graph.nodes()) {
                if (!n.kind.equals(CodeGraph.FILE) || !"code".equals(n.subkind)) continue;
                double s = symbolsIn(n.filePath).size() * 2.0 + Math.min(10, (n.endLine == null ? 0 : n.endLine) / 50.0);
                for (CodeGraph.Edge e : in.getOrDefault(n.id, List.of())) {
                    if (e.kind().equals(CodeGraph.IMPORTS)) s += 3;
                }
                for (Node sym : symbolsIn(n.filePath)) {
                    for (CodeGraph.Edge e : in.getOrDefault(sym.id, List.of())) {
                        if (!e.kind().equals(CodeGraph.CONTAINS)) s += 1;
                    }
                }
                FileModel model = models.get(n.filePath);
                if (model != null) s += model.endpoints().size() * 5;
                String lower = n.filePath.toLowerCase(Locale.ROOT);
                if (lower.contains("test") || lower.contains("spec") || lower.contains("mock") || lower.contains("example")) s *= 0.3;
                score.put(n.id, s);
            }
            return score.entrySet().stream().sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .map(e -> graph.node(e.getKey())).toList();
        }

        List<String> endpointsIn(String path) {
            return graph.nodes().stream().filter(n -> n.kind.equals(CodeGraph.ENDPOINT) && path.equals(n.filePath))
                .map(n -> n.name).toList();
        }

        List<Node> endpointNodes() {
            return graph.nodes().stream().filter(n -> n.kind.equals(CodeGraph.ENDPOINT)).toList();
        }

        List<String> allEndpoints(int limit) {
            return endpointNodes().stream().limit(limit).map(n -> {
                Node h = handlerOf(n.id);
                return "- " + n.name + (h == null ? "" : " -> " + h.name) + " (" + n.filePath + ")";
            }).toList();
        }

        Node handlerOf(int endpointId) {
            for (CodeGraph.Edge e : out.getOrDefault(endpointId, List.of())) {
                if (e.kind().equals(CodeGraph.HANDLES)) return graph.node(e.target());
            }
            return null;
        }

        List<String> importNames(int fileId) {
            List<String> names = new ArrayList<>();
            for (CodeGraph.Edge e : out.getOrDefault(fileId, List.of())) {
                if (e.kind().equals(CodeGraph.IMPORTS)) names.add(graph.node(e.target()).name);
            }
            return names;
        }

        String languageSummary() {
            Map<String, Long> counts = graph.nodes().stream().filter(n -> n.kind.equals(CodeGraph.FILE) && n.language != null)
                .collect(Collectors.groupingBy(n -> n.language, Collectors.counting()));
            return counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
        }

        List<String> dependencies(int limit) {
            return graph.nodes().stream().filter(n -> n.kind.equals(CodeGraph.EXTERNAL))
                .sorted(Comparator.comparingInt((Node n) -> -in.getOrDefault(n.id, List.of()).size()))
                .limit(limit).map(n -> n.name).toList();
        }

        List<String> manifestFiles() {
            return graph.nodes().stream().filter(n -> n.kind.equals(CodeGraph.FILE)
                    && com.synapse.analysis.parse.ManifestParser.isManifest(n.name))
                .map(n -> n.filePath).limit(20).toList();
        }

        String tree(int depth, int maxLines) {
            TreeMap<String, Integer> dirs = new TreeMap<>();
            for (Node n : graph.nodes()) {
                if (n.kind.equals(CodeGraph.FILE)) {
                    String[] parts = n.filePath.split("/");
                    StringBuilder path = new StringBuilder();
                    for (int i = 0; i < Math.min(depth, parts.length - 1); i++) {
                        path.append(i == 0 ? "" : "/").append(parts[i]);
                        dirs.merge(path.toString(), 1, Integer::sum);
                    }
                    if (parts.length == 1) dirs.merge(parts[0], 0, Integer::sum);
                }
            }
            StringBuilder sb = new StringBuilder();
            int lines = 0;
            for (Map.Entry<String, Integer> e : dirs.entrySet()) {
                if (lines++ >= maxLines) {
                    sb.append("...\n");
                    break;
                }
                int level = e.getKey().split("/").length - 1;
                String name = e.getKey().substring(e.getKey().lastIndexOf('/') + 1);
                sb.append("  ".repeat(level)).append(e.getValue() == 0 ? name : name + "/  (" + e.getValue() + " files)").append('\n');
            }
            return sb.toString();
        }

        private static final java.util.regex.Pattern SOURCE_SET =
            java.util.regex.Pattern.compile("^(?:.*/)?src/(?:main|test|it|androidTest)/(?:java|kotlin|scala|groovy|resources)/");
        private String moduleRoot;

        /** Strips Maven/Gradle source-set folders so main and test code share package paths. */
        private static String normalizedDir(String path) {
            String p = SOURCE_SET.matcher(path).replaceFirst("");
            int slash = p.lastIndexOf('/');
            return slash < 0 ? "" : p.substring(0, slash);
        }

        /**
         * Modules are the folders directly below the deepest folder that contains (nearly) all code,
         * e.g. owner/vet/model under org/springframework/samples/petclinic, or components/pages under src.
         */
        private String moduleRoot() {
            if (moduleRoot != null) return moduleRoot;
            List<String> dirs = graph.nodes().stream()
                .filter(n -> n.kind.equals(CodeGraph.FILE) && "code".equals(n.subkind))
                .map(n -> normalizedDir(n.filePath)).toList();
            String root = "";
            while (true) {
                String current = root;
                String prefix = current.isEmpty() ? "" : current + "/";
                Map<String, Long> children = new HashMap<>();
                long atRoot = 0;
                for (String d : dirs) {
                    if (d.equals(current)) {
                        atRoot++;
                    } else if (d.startsWith(prefix)) {
                        children.merge(prefix + d.substring(prefix.length()).split("/")[0], 1L, Long::sum);
                    }
                }
                // Descend while one folder holds most of the code (resources such as db/ scripts may sit beside it).
                var largest = children.entrySet().stream().max(Map.Entry.comparingByValue());
                if (largest.isEmpty() || largest.get().getValue() < dirs.size() * 0.6 || atRoot > dirs.size() * 0.15) break;
                root = largest.get().getKey();
            }
            moduleRoot = root;
            return root;
        }

        private String moduleOf(Node n) {
            if (n.filePath == null) return null;
            String dir = normalizedDir(n.filePath);
            String root = moduleRoot();
            if (dir.equals(root)) {
                return dir.isEmpty() ? "(root)" : dir.substring(dir.lastIndexOf('/') + 1);
            }
            if (!root.isEmpty() && !dir.startsWith(root + "/")) {
                return dir.isEmpty() ? "(root)" : dir.split("/")[0];
            }
            String rest = root.isEmpty() ? dir : dir.substring(root.length() + 1);
            return rest.split("/")[0];
        }

        String moduleList(int limit) {
            Map<String, Long> counts = graph.nodes().stream().filter(n -> n.kind.equals(CodeGraph.FILE) && "code".equals(n.subkind))
                .collect(Collectors.groupingBy(this::moduleOf, TreeMap::new, Collectors.counting()));
            return counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(limit)
                .map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n")) + "\n";
        }

        private Map<String, Integer> moduleEdges() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (CodeGraph.Edge e : graph.edges()) {
                if (!(e.kind().equals(CodeGraph.IMPORTS) || e.kind().equals(CodeGraph.CALLS) || e.kind().equals(CodeGraph.INSTANTIATES))) continue;
                Node a = graph.node(e.source());
                Node b = graph.node(e.target());
                if (b.kind.equals(CodeGraph.EXTERNAL) || b.kind.equals(CodeGraph.DIRECTORY)) continue;
                String ma = moduleOf(a);
                String mb = moduleOf(b);
                if (ma == null || mb == null || ma.equals(mb)) continue;
                counts.merge(ma + " -> " + mb, e.weight(), Integer::sum);
            }
            return counts;
        }

        List<String> moduleDependencies(int limit) {
            return moduleEdges().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(limit)
                .map(e -> e.getKey() + " (" + e.getValue() + ")").toList();
        }

        /** A Mermaid diagram of the strongest module-to-module dependencies. */
        String moduleDiagram() {
            List<Map.Entry<String, Integer>> top = moduleEdges().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(25).toList();
            if (top.isEmpty()) return "";
            Map<String, String> ids = new LinkedHashMap<>();
            StringBuilder sb = new StringBuilder("## Module Dependency Diagram\n\n```mermaid\nflowchart LR\n");
            for (Map.Entry<String, Integer> e : top) {
                String[] ab = e.getKey().split(" -> ");
                for (String m : ab) ids.computeIfAbsent(m, k -> "M" + ids.size());
            }
            ids.forEach((name, id) -> sb.append("  ").append(id).append("[\"").append(name.replace("\"", "'")).append("\"]\n"));
            for (Map.Entry<String, Integer> e : top) {
                String[] ab = e.getKey().split(" -> ");
                sb.append("  ").append(ids.get(ab[0])).append(" -->|").append(e.getValue()).append("| ").append(ids.get(ab[1])).append('\n');
            }
            return sb.append("```\n").toString();
        }

        List<String> inheritance(int limit) {
            List<String> list = new ArrayList<>();
            for (CodeGraph.Edge e : graph.edges()) {
                if (e.kind().equals(CodeGraph.EXTENDS) || e.kind().equals(CodeGraph.IMPLEMENTS)) {
                    list.add(graph.node(e.source()).name + " " + e.kind().toLowerCase(Locale.ROOT) + " " + graph.node(e.target()).name);
                    if (list.size() >= limit) break;
                }
            }
            return list;
        }

        List<Node> topClasses(int limit) {
            return graph.nodes().stream().filter(n -> n.kind.equals(CodeGraph.CLASS))
                .sorted(Comparator.comparingInt((Node n) -> -in.getOrDefault(n.id, List.of()).size())).limit(limit).toList();
        }

        List<String> domainClasses(int limit) {
            Set<String> technical = Set.of("controller", "config", "util", "helper", "test", "exception", "handler", "filter",
                "mapper", "dto", "factory", "adapter", "middleware");
            Set<String> names = new LinkedHashSet<>();
            for (Node n : topClasses(400)) {
                String lower = n.name.toLowerCase(Locale.ROOT);
                if (technical.stream().noneMatch(lower::contains)) names.add(n.name);
                if (names.size() >= limit) break;
            }
            return new ArrayList<>(names);
        }
    }
}
