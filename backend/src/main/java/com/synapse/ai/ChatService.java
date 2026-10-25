package com.synapse.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.synapse.docs.DocumentRepository;
import com.synapse.docs.DocumentRepository.DocumentRow;
import com.synapse.graph.GraphRepository;
import com.synapse.graph.GraphRepository.Neighbor;
import com.synapse.graph.GraphRepository.NodeRow;
import com.synapse.project.ProjectRepository.ProjectRow;

/**
 * Answers questions about one analysed project. Retrieval is grounded in the project's own
 * knowledge graph (names, AI summaries, source snippets, relationships) and generated documents,
 * then the local model writes the answer.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public record Turn(String role, String content) {
    }

    /** What the user is looking at when they ask, so "this class" or "this document" can be resolved. */
    public record ViewContext(String tab, Integer selectedNodeId, String documentKind) {
    }

    public record Source(int id, String kind, String name, String filePath) {
    }

    /** One place where a target is called, created, extended or routed to. */
    public record CallSite(int callerId, String callerName, String relation, String filePath, List<Integer> lines) {
    }

    public record CallSiteGroup(int targetId, String targetName, String targetKind, String definedAt, List<CallSite> sites) {
    }

    private static final Pattern WHERE_QUESTION = Pattern.compile(
        "(?i)\\b(where|called|calls?|call sites?|used|usages?|uses|instantiat\\w*|created|creates|invok\\w*|referenc\\w*|who)\\b");

    public interface Listener {
        void sources(List<Source> sources);

        /** Exact call sites, shown to the user verbatim (small models abbreviate long lists). */
        default void callSites(List<CallSiteGroup> groups) {
        }
        void waiting();
        void token(String text);
    }

    private static final int MAX_NODES = 14;
    private static final int SNIPPETS = 4;
    private static final int SNIPPET_CHARS = 1_600;
    private static final int DOC_CHUNKS = 3;
    private static final int DOC_CHUNK_CHARS = 1_500;
    private static final int HISTORY_TURNS = 6;

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern API_QUESTION = Pattern.compile("(?i)\\b(api|apis|endpoint|endpoints|route|routes|url|urls|http|rest|request|requests|controller)\\b");
    private static final Set<String> STOPWORDS = Set.of("the", "and", "for", "with", "this", "that", "what", "which", "who",
        "how", "does", "did", "are", "is", "was", "were", "can", "could", "should", "would", "will", "about", "from", "into",
        "there", "their", "they", "them", "its", "it's", "has", "have", "had", "you", "your", "our", "any", "all", "some",
        "where", "when", "why", "explain", "tell", "show", "list", "give", "please", "code", "project", "file", "files",
        "class", "classes", "function", "functions", "method", "methods", "work", "works", "used", "use", "uses", "using",
        "between", "each", "other", "than", "then", "also", "more", "most", "much", "many", "here", "being", "been", "doing",
        "describe", "mean", "means", "happen", "happens", "part", "parts", "thing", "things", "make", "makes", "need");

    private static final String RULES = """
        You are Synapse, an assistant that answers questions about ONE software project for developers.
        Answer ONLY from the CONTEXT below (the project's knowledge graph, source snippets and generated documentation).
        If the context does not contain the answer, say so plainly and suggest what to search for; never invent code,
        files or behaviour.
        To say WHERE something is called, created or used, rely ONLY on the CALL SITES section and give the file
        path and line numbers exactly as listed. If it says no call sites were found, say that and repeat its
        explanation; never list callers, files or line numbers that are not in the context.
        Answer WHERE questions by listing EVERY matching entry from CALL SITES (caller, file, line), grouped by file,
        with no code blocks.
        Only quote code that appears verbatim in the CONTEXT. Never write new, example or "simplified" code, never
        invent class names, methods, limits or rules; describe in words anything the context does not show.
        When you mention a code element listed in the context, cite it as [[ID|Name]] using the numeric ID in square
        brackets from the context, for example [[42|OrderService]]. Cite the most relevant elements, not every one.
        Be concise and concrete: short paragraphs or bullet lists in Markdown.
        """;

    private final GraphRepository graphs;
    private final DocumentRepository documents;
    private final LocalLlm llm;

    public ChatService(GraphRepository graphs, DocumentRepository documents, LocalLlm llm) {
        this.graphs = graphs;
        this.documents = documents;
        this.llm = llm;
    }

    public boolean aiReady() {
        return llm.status().ready();
    }

    public String answer(ProjectRow project, String question, List<Turn> history, ViewContext view, Listener listener) {
        Map<Integer, NodeRow> picked = new LinkedHashMap<>();
        StringBuilder context = new StringBuilder();

        context.append("PROJECT: ").append(project.name());
        if (project.sourceUrl() != null) context.append(" (").append(project.sourceUrl()).append(')');
        context.append('\n');
        if (project.statsJson() != null) context.append("STATISTICS: ").append(oneLine(project.statsJson(), 700)).append('\n');

        // 1. What the user is looking at right now.
        if (view != null && view.selectedNodeId() != null) {
            graphs.node(project.id(), view.selectedNodeId()).ifPresent(detail -> {
                NodeRow n = detail.node();
                picked.put(n.id(), n);
                context.append("\nCURRENTLY SELECTED IN THE GRAPH (\"this\" refers to it):\n").append(describe(n)).append('\n');
                if (detail.snippet() != null) {
                    context.append("Source:\n```\n").append(truncate(detail.snippet(), SNIPPET_CHARS)).append("\n```\n");
                }
                List<Neighbor> neighbors = graphs.neighbors(project.id(), n.id());
                if (!neighbors.isEmpty()) {
                    context.append("Relationships:\n");
                    neighbors.stream().filter(nb -> !nb.edgeKind().equals("CONTAINS") || nb.direction().equals("out")).limit(25)
                        .forEach(nb -> {
                            picked.putIfAbsent(nb.node().id(), nb.node());
                            context.append("- ").append(nb.direction().equals("out") ? n.name() + " " + verb(nb.edgeKind(), true)
                                    : verb(nb.edgeKind(), false) + " ")
                                .append(" [").append(nb.node().id()).append("] ").append(nb.node().name())
                                .append(" (").append(nb.node().kind().toLowerCase(Locale.ROOT)).append(")\n");
                        });
                }
            });
        }

        // 2. Elements of the graph that match the question.
        List<String> terms = terms(question + " " + lastUserTurn(history));
        List<NodeRow> hits = graphs.searchAnyTerm(project.id(), terms, MAX_NODES);
        if (!hits.isEmpty()) {
            context.append("\nRELEVANT CODE ELEMENTS:\n");
            for (NodeRow n : hits) {
                if (picked.containsKey(n.id()) && view != null && Integer.valueOf(n.id()).equals(view.selectedNodeId())) continue;
                picked.putIfAbsent(n.id(), n);
                context.append(describe(n)).append('\n');
            }
            int shown = 0;
            List<NodeRow> bySpecificity = new ArrayList<>(hits);
            bySpecificity.sort(Comparator.comparingInt(n -> switch (n.kind()) {
                case "METHOD", "FUNCTION" -> 0;
                case "CLASS" -> 1;
                default -> 2;
            }));
            for (NodeRow n : bySpecificity) {
                if (shown >= SNIPPETS || n.kind().equals("EXTERNAL") || n.kind().equals("ENDPOINT")) continue;
                if (view != null && Integer.valueOf(n.id()).equals(view.selectedNodeId())) continue;
                var detail = graphs.node(project.id(), n.id());
                if (detail.isPresent() && detail.get().snippet() != null) {
                    context.append("\nSource of [").append(n.id()).append("] ").append(n.name()).append(":\n```\n")
                        .append(truncate(detail.get().snippet(), SNIPPET_CHARS)).append("\n```\n");
                    shown++;
                }
            }
        }

        // 3. Exact call sites for the selected element and any element named in the question.
        List<NodeRow> callTargets = new ArrayList<>();
        if (view != null && view.selectedNodeId() != null && picked.containsKey(view.selectedNodeId())) {
            callTargets.add(picked.get(view.selectedNodeId()));
        }
        // Symbols named in the question, looked up by exact name. Prefer members of a class that is also
        // named ("validate in PetValidator"), and methods over the class itself.
        List<NodeRow> named = graphs.symbolsNamed(project.id(), terms, 40);
        Set<String> namedClassFiles = new java.util.HashSet<>();
        for (NodeRow n : named) {
            if (n.kind().equals("CLASS") && n.filePath() != null) namedClassFiles.add(n.filePath());
        }
        List<NodeRow> ordered = new ArrayList<>(named);
        ordered.sort(Comparator.comparingInt((NodeRow n) -> {
            boolean member = !n.kind().equals("CLASS") && namedClassFiles.contains(n.filePath());
            boolean isClassWithMemberNamed = n.kind().equals("CLASS")
                && named.stream().anyMatch(m -> !m.kind().equals("CLASS") && n.filePath().equals(m.filePath()));
            if (member) return 0;
            if (!namedClassFiles.isEmpty() && !n.kind().equals("CLASS")) return 2; // same name, unrelated class
            return isClassWithMemberNamed ? 3 : 1;
        }));
        for (NodeRow n : ordered) {
            if (callTargets.size() >= 4) break;
            if (callTargets.stream().noneMatch(t -> t.id() == n.id())) {
                callTargets.add(n);
                picked.putIfAbsent(n.id(), n);
            }
        }
        List<CallSiteGroup> callSiteGroups = new ArrayList<>();
        if (!callTargets.isEmpty()) {
            context.append("\nCALL SITES (exact locations from the parsed code):\n");
            for (NodeRow target : callTargets) callSiteGroups.add(appendCallSites(project.id(), target, context, picked));
        }

        // 4. API surface, when the question is about it.
        if (API_QUESTION.matcher(question).find()) {
            List<Map<String, Object>> endpoints = graphs.endpointsWithHandlers(project.id(), 40);
            if (!endpoints.isEmpty()) {
                context.append("\nHTTP ENDPOINTS:\n");
                for (Map<String, Object> e : endpoints) {
                    context.append("- ").append(e.get("endpoint"));
                    if (e.get("handler_id") != null) {
                        context.append(" -> [").append(e.get("handler_id")).append("] ").append(e.get("handler"));
                    }
                    context.append(" (").append(e.get("file_path")).append(")\n");
                }
            }
        }

        // 5. Generated documentation sections that match.
        List<String> chunks = relevantDocChunks(project.id(), terms, view == null ? null : view.documentKind());
        if (!chunks.isEmpty()) {
            context.append("\nDOCUMENTATION EXCERPTS:\n");
            chunks.forEach(c -> context.append(c).append("\n---\n"));
        }

        listener.sources(picked.values().stream().limit(20)
            .map(n -> new Source(n.id(), n.kind(), n.name(), n.filePath())).toList());
        List<CallSiteGroup> shownGroups = WHERE_QUESTION.matcher(question).find()
            ? namedInQuestion(callSiteGroups, question, view) : List.of();
        boolean showCallSites = !shownGroups.isEmpty();
        if (showCallSites) {
            listener.callSites(shownGroups);
        }

        log.debug("Chat context for project {} ({} chars):\n{}", project.id(), context.length(), context);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(RULES + "\nCONTEXT:\n" + context));
        int from = Math.max(0, history.size() - HISTORY_TURNS);
        for (Turn t : history.subList(from, history.size())) {
            String content = truncate(t.content(), 1_500);
            messages.add("assistant".equals(t.role()) ? new AssistantMessage(content) : new UserMessage(content));
        }
        if (showCallSites) {
            // The full list is rendered under the answer, so the model only has to summarise it correctly.
            int total = shownGroups.stream().mapToInt(g -> g.sites().size()).sum();
            messages.add(new UserMessage(question + "\n\n(The complete list of " + total + " call sites from CALL SITES is shown "
                + "to me under your answer. Summarise it in 2-4 sentences: how many places, which files and methods, and "
                + "production code versus tests. Where an element is DEFINED is not a call site. If the list is empty, say "
                + "nothing in this repository calls it.)"));
        } else {
            messages.add(new UserMessage(question));
        }
        return llm.stream(messages, listener::waiting, listener::token);
    }

    // ------------------------------------------------------------------ retrieval helpers

    private static final Set<String> USAGE_EDGES = Set.of("CALLS", "INSTANTIATES", "EXTENDS", "IMPLEMENTS", "HANDLES");

    /**
     * The call-site groups the user actually asked about: elements named as a whole word in the question
     * (or the selected node). When a method and its class are both named ("validate in PetValidator"),
     * only the method is meant.
     */
    static List<CallSiteGroup> namedInQuestion(List<CallSiteGroup> groups, String question, ViewContext view) {
        Set<String> words = new java.util.HashSet<>();
        var m = WORD.matcher(question);
        while (m.find()) words.add(m.group().toLowerCase(Locale.ROOT));
        List<CallSiteGroup> named = groups.stream()
            .filter(g -> words.contains(g.targetName().toLowerCase(Locale.ROOT))
                || (view != null && Integer.valueOf(g.targetId()).equals(view.selectedNodeId())))
            .toList();
        Set<String> memberFiles = new java.util.HashSet<>();
        Set<String> classFiles = new java.util.HashSet<>();
        for (CallSiteGroup g : named) {
            (g.targetKind().equals("CLASS") ? classFiles : memberFiles).add(fileOf(g.definedAt()));
        }
        // A named class scopes the method: "validate of PetValidator" excludes other classes' validate methods.
        boolean scoped = memberFiles.stream().anyMatch(classFiles::contains);
        return named.stream()
            .filter(g -> !(g.targetKind().equals("CLASS") && memberFiles.contains(fileOf(g.definedAt()))))
            .filter(g -> !scoped || g.targetKind().equals("CLASS") || classFiles.contains(fileOf(g.definedAt())))
            .toList();
    }

    private static String fileOf(String definedAt) {
        int colon = definedAt.lastIndexOf(':');
        return colon > 0 ? definedAt.substring(0, colon) : definedAt;
    }

    private CallSiteGroup appendCallSites(long projectId, NodeRow target, StringBuilder context, Map<Integer, NodeRow> picked) {
        String definedAt = target.filePath() + (target.startLine() == null ? "" : ":" + target.startLine());
        List<CallSite> sites = new ArrayList<>();
        context.append("* [").append(target.id()).append("] ").append(target.name()).append(" (").append(target.kind().toLowerCase(Locale.ROOT))
            .append(" defined at ").append(target.filePath()).append(':').append(target.startLine()).append(")\n");
        List<Neighbor> usages = graphs.neighbors(projectId, target.id()).stream()
            .filter(nb -> nb.direction().equals("in") && USAGE_EDGES.contains(nb.edgeKind())).toList();
        if (usages.isEmpty()) {
            context.append("  NO CALLERS: nothing in the analysed code calls or uses ").append(target.name())
                .append(". It is only DEFINED in ").append(target.filePath()).append(" (that class is not a caller). ")
                .append("It is probably invoked by a framework (annotations, dependency injection, routing), ")
                .append("reflection, configuration, or code outside this repository.\n");
            return new CallSiteGroup(target.id(), target.name(), target.kind(), definedAt, sites);
        }
        for (Neighbor nb : usages) {
            NodeRow caller = nb.node();
            sites.add(new CallSite(caller.id(), caller.name(), verb(nb.edgeKind(), false), caller.filePath(), nb.lines()));
            if (sites.size() > 25) continue; // the model sees the first 25; the user sees all
            picked.putIfAbsent(caller.id(), caller);
            context.append("  - ").append(verb(nb.edgeKind(), false)).append(" [").append(caller.id()).append("] ").append(caller.name())
                .append(" in ").append(caller.filePath());
            if (!nb.lines().isEmpty()) {
                context.append(nb.lines().size() == 1 ? " at line " : " at lines ")
                    .append(String.join(", ", nb.lines().stream().map(String::valueOf).toList()));
            } else if (caller.startLine() != null) {
                context.append(" (caller starts at line ").append(caller.startLine()).append(')');
            }
            context.append('\n');
        }
        return new CallSiteGroup(target.id(), target.name(), target.kind(), definedAt, sites);
    }

    /** Search terms: identifiers kept whole and split on camelCase / snake_case, minus filler words. */
    static List<String> terms(String text) {
        Set<String> out = new LinkedHashSet<>();
        var m = WORD.matcher(text);
        while (m.find() && out.size() < 16) {
            String word = m.group();
            String lower = word.toLowerCase(Locale.ROOT);
            if (lower.length() >= 3 && !STOPWORDS.contains(lower)) out.add(lower);
            for (String part : word.split("(?<=[a-z0-9])(?=[A-Z])|_")) {
                String p = part.toLowerCase(Locale.ROOT);
                if (p.length() >= 3 && !STOPWORDS.contains(p)) out.add(p);
            }
        }
        return new ArrayList<>(out);
    }

    private List<String> relevantDocChunks(long projectId, List<String> terms, String activeKind) {
        record Chunk(String text, double score) {
        }
        List<Chunk> chunks = new ArrayList<>();
        for (DocumentRow doc : documents.list(projectId)) {
            String[] sections = doc.content().split("(?m)(?=^#{1,3} )");
            for (int i = 0; i < sections.length; i++) {
                String section = sections[i].trim();
                if (section.length() < 40 || section.startsWith("```mermaid") || section.contains("```mermaid")) continue;
                String lower = section.toLowerCase(Locale.ROOT);
                double score = 0;
                for (String t : terms) {
                    int idx = 0;
                    while ((idx = lower.indexOf(t, idx)) >= 0 && score < 50) {
                        score += 1;
                        idx += t.length();
                    }
                }
                if (doc.kind().equals(activeKind)) score += 2;
                if (doc.kind().equals("README") && i <= 1) score += 1.5; // the overview is broadly useful
                if (score > 0) {
                    chunks.add(new Chunk("[" + doc.fileName() + "]\n" + truncate(section, DOC_CHUNK_CHARS), score));
                }
            }
        }
        return chunks.stream().sorted(Comparator.comparingDouble(Chunk::score).reversed()).limit(DOC_CHUNKS)
            .map(Chunk::text).toList();
    }

    private static String describe(NodeRow n) {
        StringBuilder sb = new StringBuilder("[").append(n.id()).append("] ").append(n.kind().toLowerCase(Locale.ROOT))
            .append(' ').append(n.name());
        if (n.filePath() != null) {
            sb.append(" — ").append(n.filePath());
            if (n.startLine() != null) sb.append(':').append(n.startLine());
        }
        if (n.signature() != null && !n.kind().equals("ENDPOINT")) sb.append("\n    signature: ").append(oneLine(n.signature(), 200));
        if (n.summary() != null) sb.append("\n    summary: ").append(oneLine(n.summary(), 400));
        return sb.toString();
    }

    private static String verb(String edgeKind, boolean outgoing) {
        return switch (edgeKind) {
            case "CALLS" -> outgoing ? "calls" : "called by";
            case "IMPORTS" -> outgoing ? "imports" : "imported by";
            case "EXTENDS" -> outgoing ? "extends" : "extended by";
            case "IMPLEMENTS" -> outgoing ? "implements" : "implemented by";
            case "INSTANTIATES" -> outgoing ? "creates" : "created by";
            case "HANDLES" -> outgoing ? "is handled by" : "handles";
            case "DEPENDS_ON" -> outgoing ? "depends on" : "used by";
            default -> outgoing ? "contains" : "inside";
        };
    }

    private static String lastUserTurn(List<Turn> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) return truncate(history.get(i).content(), 300);
        }
        return "";
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "\n…";
    }

    private static String oneLine(String s, int max) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }
}
