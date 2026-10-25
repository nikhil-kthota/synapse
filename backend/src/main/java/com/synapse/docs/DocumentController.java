package com.synapse.docs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.common.ApiException;
import com.synapse.docs.DocumentRepository.DocumentRow;
import com.synapse.project.ProjectRepository;
import com.synapse.project.ProjectRepository.ProjectRow;

@RestController
@RequestMapping("/api/projects/{projectId}/documents")
public class DocumentController {

    private final DocumentRepository documents;
    private final ProjectRepository projects;

    public DocumentController(DocumentRepository documents, ProjectRepository projects) {
        this.documents = documents;
        this.projects = projects;
    }

    @GetMapping
    public List<DocumentRow> list(@AuthenticationPrincipal Jwt jwt, @PathVariable long projectId) {
        owned(jwt, projectId);
        return documents.list(projectId);
    }

    /** All generated documents as a ZIP of Markdown files. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable long projectId) throws IOException {
        ProjectRow project = owned(jwt, projectId);
        List<DocumentRow> docs = documents.list(projectId);
        if (docs.isEmpty()) throw ApiException.notFound("Documentation");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (DocumentRow doc : docs) {
                zip.putNextEntry(new ZipEntry("docs/" + doc.fileName()));
                zip.write(doc.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        String fileName = project.name().replaceAll("[^\\w.-]+", "-") + "-docs.zip";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(buffer.toByteArray());
    }

    private ProjectRow owned(Jwt jwt, long projectId) {
        return projects.findForOwner(projectId, Long.parseLong(jwt.getSubject())).orElseThrow(() -> ApiException.notFound("Project"));
    }
}
