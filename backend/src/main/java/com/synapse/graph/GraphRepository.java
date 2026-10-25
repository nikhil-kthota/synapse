package com.synapse.graph;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.analysis.graph.CodeGraph;

@Repository
public class GraphRepository {

    public record NodeRow(int id, String kind, String subkind, String name, String qualifiedName, String language,
                          String filePath, Integer startLine, Integer endLine, String signature, String summary) {
    }

    public record NodeDetail(NodeRow node, String snippet) {
    }

    public record EdgeRow(int source, int target, String kind, int weight) {
    }

    /** @param lines lines in the caller's file where the relationship occurs (call sites) */
    public record Neighbor(String direction, String edgeKind, int weight, List<Integer> lines, NodeRow node) {
    }

    private static final String NODE_COLUMNS =
        "id, kind, subkind, name, qualified_name, language, file_path, start_line, end_line, signature, summary";

    private final JdbcTemplate template;
    private final JdbcClient jdbc;

    public GraphRepository(JdbcTemplate template, JdbcClient jdbc) {
        this.template = template;
        this.jdbc = jdbc;
    }

    @Transactional
    public void replaceGraph(long projectId, CodeGraph graph) {
        jdbc.sql("DELETE FROM graph_edges WHERE project_id = ?").param(projectId).update();
        jdbc.sql("DELETE FROM graph_nodes WHERE project_id = ?").param(projectId).update();
        template.batchUpdate("""
                INSERT INTO graph_nodes (project_id, id, kind, subkind, name, qualified_name, language, file_path,
                                         start_line, end_line, signature, snippet, summary)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, graph.nodes(), 1000, (ps, n) -> {
                ps.setLong(1, projectId);
                ps.setInt(2, n.id);
                ps.setString(3, n.kind);
                ps.setString(4, n.subkind);
                ps.setString(5, clean(n.name));
                ps.setString(6, clean(n.qualifiedName));
                ps.setString(7, n.language);
                ps.setString(8, clean(n.filePath));
                ps.setObject(9, n.startLine);
                ps.setObject(10, n.endLine);
                ps.setString(11, clean(n.signature));
                ps.setString(12, clean(n.snippet));
                ps.setString(13, clean(n.summary));
            });
        template.batchUpdate("INSERT INTO graph_edges (project_id, source_id, target_id, kind, weight, lines) VALUES (?, ?, ?, ?, ?, ?)",
            graph.edges(), 2000, (ps, e) -> {
                ps.setLong(1, projectId);
                ps.setInt(2, e.source());
                ps.setInt(3, e.target());
                ps.setString(4, e.kind());
                ps.setInt(5, e.weight());
                if (e.lines().isEmpty()) {
                    ps.setNull(6, java.sql.Types.ARRAY);
                } else {
                    ps.setArray(6, ps.getConnection().createArrayOf("int4", e.lines().toArray()));
                }
            });
    }

    public void updateSummaries(long projectId, Map<Integer, String> summaries) {
        template.batchUpdate("UPDATE graph_nodes SET summary = ? WHERE project_id = ? AND id = ?",
            new ArrayList<>(summaries.entrySet()), 500, (ps, e) -> {
                ps.setString(1, clean(e.getValue()));
                ps.setLong(2, projectId);
                ps.setInt(3, e.getKey());
            });
    }

    public List<NodeRow> nodes(long projectId, Collection<String> kinds) {
        return jdbc.sql("SELECT " + NODE_COLUMNS + " FROM graph_nodes WHERE project_id = :p AND kind IN (:kinds) ORDER BY id")
            .param("p", projectId).param("kinds", kinds).query(GraphRepository::mapNode).list();
    }

    public List<EdgeRow> edges(long projectId) {
        return jdbc.sql("SELECT source_id, target_id, kind, weight FROM graph_edges WHERE project_id = :p")
            .param("p", projectId)
            .query((rs, i) -> new EdgeRow(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4))).list();
    }

    public Optional<NodeDetail> node(long projectId, int nodeId) {
        return jdbc.sql("SELECT " + NODE_COLUMNS + ", snippet FROM graph_nodes WHERE project_id = :p AND id = :id")
            .param("p", projectId).param("id", nodeId)
            .query((rs, i) -> new NodeDetail(mapNode(rs, i), rs.getString("snippet"))).optional();
    }

    public List<Neighbor> neighbors(long projectId, int nodeId) {
        return jdbc.sql("""
                SELECT 'out' AS direction, e.kind AS edge_kind, e.weight, e.lines AS edge_lines, n.*
                  FROM graph_edges e JOIN graph_nodes n ON n.project_id = e.project_id AND n.id = e.target_id
                 WHERE e.project_id = :p AND e.source_id = :id
                UNION ALL
                SELECT 'in', e.kind, e.weight, e.lines, n.*
                  FROM graph_edges e JOIN graph_nodes n ON n.project_id = e.project_id AND n.id = e.source_id
                 WHERE e.project_id = :p AND e.target_id = :id
                LIMIT 400
                """)
            .param("p", projectId).param("id", nodeId)
            .query((rs, i) -> new Neighbor(rs.getString("direction"), rs.getString("edge_kind"), rs.getInt("weight"),
                lines(rs.getArray("edge_lines")), mapNode(rs, i)))
            .list();
    }

    /** Full-text search over names, paths and AI summaries, with a substring fallback for partial names. */
    public List<NodeRow> search(long projectId, String query, int limit) {
        return jdbc.sql("""
                SELECT %s FROM (
                    SELECT n.*, ts_rank(search, websearch_to_tsquery('simple', :q)) * 2
                              + CASE WHEN lower(name) = lower(:q) THEN 5
                                     WHEN lower(name) LIKE lower(:prefix) THEN 2
                                     WHEN lower(name) LIKE lower(:contains) THEN 1 ELSE 0 END
                              + CASE kind WHEN 'CLASS' THEN 0.6 WHEN 'FILE' THEN 0.4 WHEN 'ENDPOINT' THEN 0.5 ELSE 0.2 END AS score
                      FROM graph_nodes n
                     WHERE project_id = :p AND kind <> 'REPOSITORY'
                       AND (search @@ websearch_to_tsquery('simple', :q)
                            OR search @@ websearch_to_tsquery('english', :q)
                            OR lower(name) LIKE lower(:contains)
                            OR lower(qualified_name) LIKE lower(:contains))
                ) ranked ORDER BY score DESC, length(name) LIMIT :limit
                """.formatted(NODE_COLUMNS))
            .param("p", projectId).param("q", query)
            .param("prefix", escapeLike(query) + "%").param("contains", "%" + escapeLike(query) + "%")
            .param("limit", limit)
            .query(GraphRepository::mapNode).list();
    }

    /**
     * Retrieval for the chat assistant: nodes matching ANY of the terms (prefix match), ranked by
     * relevance with a boost for exact names and for elements that have an AI summary.
     */
    public List<NodeRow> searchAnyTerm(long projectId, List<String> terms, int limit) {
        if (terms.isEmpty()) return List.of();
        String tsquery = String.join(" | ", terms.stream().map(t -> t + ":*").toList());
        return jdbc.sql("""
                SELECT %s FROM (
                    SELECT n.*, ts_rank(search, to_tsquery('simple', :q)) + ts_rank(search, to_tsquery('english', :eq))
                              + CASE WHEN lower(name) = ANY(:names) THEN 3 ELSE 0 END
                              + CASE WHEN summary IS NOT NULL THEN 0.3 ELSE 0 END
                              + CASE kind WHEN 'CLASS' THEN 0.4 WHEN 'METHOD' THEN 0.35 WHEN 'FUNCTION' THEN 0.35
                                          WHEN 'FILE' THEN 0.2 WHEN 'ENDPOINT' THEN 0.3 ELSE 0.1 END
                              - CASE WHEN :tests THEN 0 WHEN lower(file_path) ~ '(^|/)(test|tests|spec|__tests__)/|tests?[.]|spec[.]' THEN 0.6 ELSE 0 END AS score
                      FROM graph_nodes n
                     WHERE project_id = :p AND kind NOT IN ('REPOSITORY', 'DIRECTORY')
                       AND (search @@ to_tsquery('simple', :q) OR search @@ to_tsquery('english', :eq) OR lower(name) = ANY(:names))
                ) ranked ORDER BY score DESC LIMIT :limit
                """.formatted(NODE_COLUMNS))
            .param("p", projectId).param("q", tsquery).param("eq", String.join(" | ", terms))
            .param("names", terms.toArray(String[]::new)).param("limit", limit)
            .param("tests", terms.stream().anyMatch(t -> t.startsWith("test") || t.startsWith("spec")))
            .query(GraphRepository::mapNode).list();
    }

    /** Classes, methods and functions whose name exactly matches one of the given (lower-case) names. */
    public List<NodeRow> symbolsNamed(long projectId, List<String> names, int limit) {
        if (names.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT %s FROM graph_nodes
                 WHERE project_id = :p AND kind IN ('CLASS', 'METHOD', 'FUNCTION') AND lower(name) = ANY(:names)
                 ORDER BY CASE kind WHEN 'METHOD' THEN 0 WHEN 'FUNCTION' THEN 0 ELSE 1 END, id
                 LIMIT :limit
                """.formatted(NODE_COLUMNS))
            .param("p", projectId).param("names", names.toArray(String[]::new)).param("limit", limit)
            .query(GraphRepository::mapNode).list();
    }

    public List<Map<String, Object>> endpointsWithHandlers(long projectId, int limit) {
        return jdbc.sql("""
                SELECT e.name AS endpoint, h.id AS handler_id, h.name AS handler, e.file_path
                  FROM graph_nodes e
                  LEFT JOIN graph_edges x ON x.project_id = e.project_id AND x.source_id = e.id AND x.kind = 'HANDLES'
                  LEFT JOIN graph_nodes h ON h.project_id = e.project_id AND h.id = x.target_id
                 WHERE e.project_id = :p AND e.kind = 'ENDPOINT'
                 ORDER BY e.name LIMIT :limit
                """).param("p", projectId).param("limit", limit).query().listOfRows();
    }

    /**
     * Everything that depends on a node, directly or indirectly: the blast radius of changing it.
     * A class or file counts together with its members, so callers of its methods are included.
     */
    public List<Map<String, Object>> impact(long projectId, int nodeId, int maxDepth) {
        return jdbc.sql("""
                WITH RECURSIVE members(id, level) AS (
                    SELECT :id, 0
                    UNION
                    SELECT e.target_id, m.level + 1
                      FROM graph_edges e JOIN members m ON e.source_id = m.id
                     WHERE e.project_id = :p AND e.kind = 'CONTAINS' AND m.level < 3
                ),
                dependents(id, depth) AS (
                    SELECT id, 0 FROM members
                    UNION
                    SELECT e.source_id, d.depth + 1
                      FROM graph_edges e JOIN dependents d ON e.target_id = d.id
                     WHERE e.project_id = :p AND d.depth < :max
                       AND e.kind IN ('CALLS', 'IMPORTS', 'EXTENDS', 'IMPLEMENTS', 'INSTANTIATES', 'HANDLES')
                )
                SELECT n.id, n.kind, n.name, n.file_path, MIN(d.depth) AS depth
                  FROM dependents d JOIN graph_nodes n ON n.project_id = :p AND n.id = d.id
                 WHERE d.id NOT IN (SELECT id FROM members)
                 GROUP BY n.id, n.kind, n.name, n.file_path
                 ORDER BY depth, n.kind, n.name
                 LIMIT 500
                """)
            .param("p", projectId).param("id", nodeId).param("max", maxDepth)
            .query().listOfRows();
    }

    private static NodeRow mapNode(ResultSet rs, int row) throws SQLException {
        return new NodeRow(rs.getInt("id"), rs.getString("kind"), rs.getString("subkind"), rs.getString("name"),
            rs.getString("qualified_name"), rs.getString("language"), rs.getString("file_path"),
            (Integer) rs.getObject("start_line"), (Integer) rs.getObject("end_line"), rs.getString("signature"),
            rs.getString("summary"));
    }

    private static List<Integer> lines(java.sql.Array array) throws SQLException {
        if (array == null) return List.of();
        Integer[] values = (Integer[]) array.getArray();
        return List.of(values);
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** PostgreSQL text columns reject NUL characters, which do appear in some legacy sources. */
    private static String clean(String s) {
        return s == null ? null : s.replace("\u0000", "");
    }
}
