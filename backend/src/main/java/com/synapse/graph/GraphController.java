package com.synapse.graph;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.common.ApiException;
import com.synapse.graph.GraphRepository.EdgeRow;
import com.synapse.graph.GraphRepository.Neighbor;
import com.synapse.graph.GraphRepository.NodeDetail;
import com.synapse.graph.GraphRepository.NodeRow;
import com.synapse.project.ProjectRepository;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class GraphController {

    private static final Set<String> ALL_KINDS = Set.of("REPOSITORY", "DIRECTORY", "FILE", "CLASS", "FUNCTION", "METHOD",
        "ENDPOINT", "EXTERNAL");

    public record GraphView(List<NodeRow> nodes, List<EdgeRow> edges) {
    }

    public record NodeView(NodeRow node, String snippet, List<Neighbor> neighbors) {
    }

    private final GraphRepository graphs;
    private final ProjectRepository projects;

    public GraphController(GraphRepository graphs, ProjectRepository projects) {
        this.graphs = graphs;
        this.projects = projects;
    }

    /** Nodes of the requested kinds, plus every edge between two returned nodes. */
    @GetMapping("/graph")
    public GraphView graph(@AuthenticationPrincipal Jwt jwt, @PathVariable long projectId,
                           @RequestParam(required = false) String kinds) {
        requireOwner(jwt, projectId);
        Set<String> wanted = new HashSet<>();
        if (kinds == null || kinds.isBlank()) {
            wanted.addAll(ALL_KINDS);
        } else {
            for (String k : kinds.split(",")) {
                String kind = k.trim().toUpperCase(Locale.ROOT);
                if (ALL_KINDS.contains(kind)) wanted.add(kind);
            }
        }
        if (wanted.isEmpty()) return new GraphView(List.of(), List.of());
        List<NodeRow> nodes = graphs.nodes(projectId, wanted);
        Set<Integer> ids = new HashSet<>();
        for (NodeRow n : nodes) ids.add(n.id());
        List<EdgeRow> edges = graphs.edges(projectId).stream()
            .filter(e -> ids.contains(e.source()) && ids.contains(e.target())).toList();
        return new GraphView(nodes, edges);
    }

    @GetMapping("/nodes/{nodeId}")
    public NodeView node(@AuthenticationPrincipal Jwt jwt, @PathVariable long projectId, @PathVariable int nodeId) {
        requireOwner(jwt, projectId);
        NodeDetail detail = graphs.node(projectId, nodeId).orElseThrow(() -> ApiException.notFound("Node"));
        return new NodeView(detail.node(), detail.snippet(), graphs.neighbors(projectId, nodeId));
    }

    @GetMapping("/nodes/{nodeId}/impact")
    public List<Map<String, Object>> impact(@AuthenticationPrincipal Jwt jwt, @PathVariable long projectId,
                                            @PathVariable int nodeId, @RequestParam(defaultValue = "3") int depth) {
        requireOwner(jwt, projectId);
        return graphs.impact(projectId, nodeId, Math.max(1, Math.min(depth, 6)));
    }

    @GetMapping("/search")
    public List<NodeRow> search(@AuthenticationPrincipal Jwt jwt, @PathVariable long projectId, @RequestParam String q,
                                @RequestParam(defaultValue = "40") int limit) {
        requireOwner(jwt, projectId);
        String query = q.trim();
        if (query.isEmpty()) return List.of();
        if (query.length() > 200) query = query.substring(0, 200);
        return graphs.search(projectId, query, Math.max(1, Math.min(limit, 100)));
    }

    private void requireOwner(Jwt jwt, long projectId) {
        projects.findForOwner(projectId, Long.parseLong(jwt.getSubject())).orElseThrow(() -> ApiException.notFound("Project"));
    }
}
