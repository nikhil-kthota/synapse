package com.synapse.docs;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    public record DocumentRow(long id, String kind, String title, String fileName, String content, boolean aiGenerated,
                              OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(long projectId, String kind, String title, String fileName, String content, boolean aiGenerated) {
        jdbc.sql("""
                INSERT INTO documents (project_id, kind, title, file_name, content, ai_generated)
                VALUES (:p, :kind, :title, :file, :content, :ai)
                ON CONFLICT (project_id, kind) DO UPDATE
                   SET title = EXCLUDED.title, file_name = EXCLUDED.file_name, content = EXCLUDED.content,
                       ai_generated = EXCLUDED.ai_generated, created_at = now()
                """)
            .param("p", projectId).param("kind", kind).param("title", title).param("file", fileName)
            .param("content", content.replace("\u0000", "")).param("ai", aiGenerated)
            .update();
    }

    public void deleteAll(long projectId) {
        jdbc.sql("DELETE FROM documents WHERE project_id = :p").param("p", projectId).update();
    }

    public List<DocumentRow> list(long projectId) {
        return jdbc.sql("""
                SELECT id, kind, title, file_name, content, ai_generated, created_at FROM documents WHERE project_id = :p
                ORDER BY CASE kind WHEN 'README' THEN 0 WHEN 'ARCHITECTURE' THEN 1 WHEN 'BUSINESS_LOGIC' THEN 2 ELSE 3 END
                """)
            .param("p", projectId)
            .query((rs, i) -> new DocumentRow(rs.getLong("id"), rs.getString("kind"), rs.getString("title"),
                rs.getString("file_name"), rs.getString("content"), rs.getBoolean("ai_generated"),
                rs.getObject("created_at", OffsetDateTime.class)))
            .list();
    }

    public Optional<DocumentRow> find(long projectId, long id) {
        return list(projectId).stream().filter(d -> d.id() == id).findFirst();
    }
}
