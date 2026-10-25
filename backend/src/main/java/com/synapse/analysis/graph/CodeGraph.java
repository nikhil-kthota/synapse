package com.synapse.analysis.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory knowledge graph for one project, built during analysis and then persisted. */
public final class CodeGraph {

    public static final String REPOSITORY = "REPOSITORY";
    public static final String DIRECTORY = "DIRECTORY";
    public static final String FILE = "FILE";
    public static final String CLASS = "CLASS";
    public static final String FUNCTION = "FUNCTION";
    public static final String METHOD = "METHOD";
    public static final String ENDPOINT = "ENDPOINT";
    public static final String EXTERNAL = "EXTERNAL";

    public static final String CONTAINS = "CONTAINS";
    public static final String IMPORTS = "IMPORTS";
    public static final String CALLS = "CALLS";
    public static final String INSTANTIATES = "INSTANTIATES";
    public static final String EXTENDS = "EXTENDS";
    public static final String IMPLEMENTS = "IMPLEMENTS";
    public static final String DEPENDS_ON = "DEPENDS_ON";
    public static final String HANDLES = "HANDLES";

    public static final class Node {
        public final int id;
        public final String kind;
        public final String subkind;
        public final String name;
        public final String qualifiedName;
        public final String language;
        public final String filePath;
        public final Integer startLine;
        public final Integer endLine;
        public final String signature;
        public final String snippet;
        public String summary;

        Node(int id, String kind, String subkind, String name, String qualifiedName, String language, String filePath,
             Integer startLine, Integer endLine, String signature, String snippet) {
            this.id = id;
            this.kind = kind;
            this.subkind = subkind;
            this.name = truncate(name, 400);
            this.qualifiedName = truncate(qualifiedName, 1000);
            this.language = language;
            this.filePath = truncate(filePath, 1000);
            this.startLine = startLine;
            this.endLine = endLine;
            this.signature = signature;
            this.snippet = snippet;
        }
    }

    /** @param lines source lines where the relationship occurs (call sites), possibly empty */
    public record Edge(int source, int target, String kind, int weight, List<Integer> lines) {
    }

    private static final int MAX_LINES_PER_EDGE = 25;

    private record EdgeKey(int source, int target, String kind) {
    }

    private final List<Node> nodes = new ArrayList<>();
    private final Map<EdgeKey, Integer> edges = new LinkedHashMap<>();
    private final Map<EdgeKey, java.util.TreeSet<Integer>> edgeLines = new HashMap<>();
    private final Map<Integer, List<Integer>> outgoing = new HashMap<>();

    Node addNode(String kind, String subkind, String name, String qualifiedName, String language, String filePath,
                 Integer startLine, Integer endLine, String signature, String snippet) {
        Node node = new Node(nodes.size() + 1, kind, subkind, name, qualifiedName, language, filePath, startLine, endLine,
            signature, snippet);
        nodes.add(node);
        return node;
    }

    void addEdge(int source, int target, String kind) {
        addEdge(source, target, kind, 0);
    }

    /** Adds (or strengthens) an edge and remembers the line it occurs on when known (> 0). */
    void addEdge(int source, int target, String kind, int line) {
        if (source == target) return;
        if (line > 0) {
            var lines = edgeLines.computeIfAbsent(new EdgeKey(source, target, kind), k -> new java.util.TreeSet<>());
            if (lines.size() < MAX_LINES_PER_EDGE) lines.add(line);
        }
        EdgeKey key = new EdgeKey(source, target, kind);
        int weight = edges.merge(key, 1, Integer::sum);
        if (weight == 1) {
            outgoing.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
        }
    }

    public Node node(int id) {
        return nodes.get(id - 1);
    }

    public List<Node> nodes() {
        return nodes;
    }

    public Collection<Edge> edges() {
        List<Edge> list = new ArrayList<>(edges.size());
        edges.forEach((k, w) -> list.add(new Edge(k.source(), k.target(), k.kind(), w,
            List.copyOf(edgeLines.getOrDefault(k, new java.util.TreeSet<>())))));
        return list;
    }

    public int edgeCount() {
        return edges.size();
    }

    List<Integer> targetsOf(int source) {
        return outgoing.getOrDefault(source, List.of());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
