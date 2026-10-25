package com.synapse.project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.analysis.AnalysisService;
import com.synapse.analysis.AnalysisService.Depth;
import com.synapse.analysis.AnalysisService.Source;
import com.synapse.common.ApiException;
import com.synapse.config.SynapseProperties;
import com.synapse.project.ProjectRepository.ProjectRow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Pattern REPO_URL = Pattern.compile(
        "^(?:https://(github\\.com|gitlab\\.com|bitbucket\\.org)/)?([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$");
    private static final Set<String> RUNNING = Set.of("QUEUED", "FETCHING", "SCANNING", "PARSING", "BUILDING_GRAPH", "GENERATING_DOCS");

    public record GitHubRequest(
        @NotBlank(message = "Repository URL is required") @Size(max = 300) String repoUrl,
        @Size(max = 200) String branch,
        @Size(max = 500) String token,
        String depth,
        @Size(max = 200) String name) {
    }

    public record RerunRequest(@Size(max = 500) String token) {
    }

    public record ProjectView(long id, String name, String sourceType, String sourceUrl, String branch, String depth,
                              String status, int progress, String statusMessage, Boolean aiAvailable,
                              Map<String, Object> stats, Instant createdAt, Instant completedAt) {
    }

    private final ProjectRepository projects;
    private final AnalysisService analysis;
    private final SynapseProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public ProjectController(ProjectRepository projects, AnalysisService analysis, SynapseProperties properties) {
        this.projects = projects;
        this.analysis = analysis;
        this.properties = properties;
    }

    @GetMapping
    public List<ProjectView> list(@AuthenticationPrincipal Jwt jwt) {
        return projects.listForOwner(userId(jwt)).stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    public ProjectView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return view(owned(jwt, id));
    }

    @PostMapping("/github")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProjectView analyseGitHub(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GitHubRequest request) {
        Matcher m = REPO_URL.matcher(request.repoUrl().trim());
        if (!m.matches()) {
            throw ApiException.badRequest("Enter a GitHub URL like https://github.com/owner/repo");
        }
        String host = m.group(1) == null ? "github.com" : m.group(1);
        String url = "https://" + host + "/" + m.group(2) + "/" + m.group(3) + ".git";
        String name = request.name() != null && !request.name().isBlank() ? request.name().trim() : m.group(3);
        Depth depth = depth(request.depth());
        String branch = request.branch() == null || request.branch().isBlank() ? null : request.branch().trim();
        long id = projects.create(userId(jwt), name, "GITHUB", url, branch, depth.name());
        analysis.start(id, name, depth, new Source("GITHUB", url, branch, request.token(), null));
        return view(projects.findById(id).orElseThrow());
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProjectView analyseZip(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "name", required = false) String name,
                                  @RequestParam(value = "depth", required = false) String depthParam) throws IOException {
        String original = file.getOriginalFilename() == null ? "upload.zip" : file.getOriginalFilename();
        if (file.isEmpty() || !original.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw ApiException.badRequest("Upload a .zip archive of the source code");
        }
        String projectName = name != null && !name.isBlank() ? name.trim()
            : original.replaceAll("(?i)\\.zip$", "").replaceAll("[-_]?(main|master)$", "");
        if (projectName.length() > 200) projectName = projectName.substring(0, 200);
        Depth depth = depth(depthParam);
        Files.createDirectories(properties.analysis().workspace());
        Path stored = Files.createTempFile(properties.analysis().workspace(), "upload-", ".zip");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, stored, StandardCopyOption.REPLACE_EXISTING);
        }
        long id = projects.create(userId(jwt), projectName, "ZIP", null, null, depth.name());
        analysis.start(id, projectName, depth, new Source("ZIP", null, null, null, stored));
        return view(projects.findById(id).orElseThrow());
    }

    @PostMapping("/{id}/reanalyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProjectView reanalyze(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                 @RequestBody(required = false) RerunRequest request) {
        ProjectRow row = owned(jwt, id);
        if (RUNNING.contains(row.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "This project is already being analysed");
        }
        if (!"GITHUB".equals(row.sourceType())) {
            throw ApiException.badRequest("ZIP projects cannot be re-run. Upload the archive again as a new project.");
        }
        projects.resetForRerun(id);
        String token = request == null ? null : request.token();
        analysis.start(id, row.name(), depth(row.depth()), new Source("GITHUB", row.sourceUrl(), row.branch(), token, null));
        return view(projects.findById(id).orElseThrow());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        ProjectRow row = owned(jwt, id);
        if (RUNNING.contains(row.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "Wait for the analysis to finish before deleting this project");
        }
        projects.delete(id, userId(jwt));
    }

    ProjectRow owned(Jwt jwt, long id) {
        return projects.findForOwner(id, userId(jwt)).orElseThrow(() -> ApiException.notFound("Project"));
    }

    static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private static Depth depth(String value) {
        if (value == null || value.isBlank()) return Depth.STANDARD;
        try {
            return Depth.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Depth must be quick, standard or deep");
        }
    }

    @SuppressWarnings("unchecked")
    private ProjectView view(ProjectRow r) {
        Map<String, Object> stats = r.statsJson() == null ? Map.of() : json.readValue(r.statsJson(), Map.class);
        return new ProjectView(r.id(), r.name(), r.sourceType(), r.sourceUrl(), r.branch(), r.depth(), r.status(),
            r.progress(), r.statusMessage(), r.aiAvailable(), stats, r.createdAt(), r.completedAt());
    }
}
