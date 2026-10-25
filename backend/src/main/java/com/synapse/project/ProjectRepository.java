package com.synapse.project;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

    public enum Status { QUEUED, FETCHING, SCANNING, PARSING, BUILDING_GRAPH, GENERATING_DOCS, COMPLETED, FAILED }

    public record ProjectRow(long id, long ownerId, String name, String sourceType, String sourceUrl, String branch,
                             String depth, String status, int progress, String statusMessage, Boolean aiAvailable,
                             String statsJson, Instant createdAt, Instant completedAt) {
    }

    private static final String COLUMNS = "id, owner_id, name, source_type, source_url, branch, depth, status, progress, "
        + "status_message, ai_available, stats::text AS stats, created_at, completed_at";

    private final JdbcClient jdbc;

    public ProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long create(long ownerId, String name, String sourceType, String sourceUrl, String branch, String depth) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO projects (owner_id, name, source_type, source_url, branch, depth, status, status_message)
                VALUES (:owner, :name, :type, :url, :branch, :depth, 'QUEUED', 'Waiting to start')
                """)
            .param("owner", ownerId).param("name", name).param("type", sourceType)
            .param("url", sourceUrl).param("branch", branch).param("depth", depth)
            .update(keys, "id");
        return keys.getKey().longValue();
    }

    public Optional<ProjectRow> findForOwner(long id, long ownerId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM projects WHERE id = :id AND owner_id = :owner")
            .param("id", id).param("owner", ownerId)
            .query(ProjectRepository::map).optional();
    }

    public Optional<ProjectRow> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM projects WHERE id = :id").param("id", id)
            .query(ProjectRepository::map).optional();
    }

    public List<ProjectRow> listForOwner(long ownerId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM projects WHERE owner_id = :owner ORDER BY created_at DESC")
            .param("owner", ownerId).query(ProjectRepository::map).list();
    }

    public void updateStatus(long id, Status status, int progress, String message) {
        jdbc.sql("UPDATE projects SET status = :status, progress = :progress, status_message = :message WHERE id = :id")
            .param("status", status.name()).param("progress", progress).param("message", truncate(message)).param("id", id)
            .update();
    }

    public void resetForRerun(long id) {
        jdbc.sql("""
                UPDATE projects SET status = 'QUEUED', progress = 0, status_message = 'Waiting to start',
                       ai_available = NULL, completed_at = NULL WHERE id = :id
                """).param("id", id).update();
    }

    public void complete(long id, String statsJson, boolean aiAvailable, String message) {
        jdbc.sql("""
                UPDATE projects SET status = 'COMPLETED', progress = 100, status_message = :message,
                       stats = CAST(:stats AS jsonb), ai_available = :ai, completed_at = now() WHERE id = :id
                """)
            .param("message", truncate(message)).param("stats", statsJson).param("ai", aiAvailable).param("id", id)
            .update();
    }

    public void fail(long id, String message) {
        jdbc.sql("UPDATE projects SET status = 'FAILED', status_message = :message, completed_at = now() WHERE id = :id")
            .param("message", truncate(message)).param("id", id).update();
    }

    public boolean delete(long id, long ownerId) {
        return jdbc.sql("DELETE FROM projects WHERE id = :id AND owner_id = :owner")
            .param("id", id).param("owner", ownerId).update() > 0;
    }

    /** Analyses run in memory, so any left mid-way by a restart can never finish. */
    public int failInterrupted() {
        return jdbc.sql("""
                UPDATE projects SET status = 'FAILED', status_message = 'Interrupted because the server restarted. Run it again.'
                WHERE status NOT IN ('COMPLETED', 'FAILED')
                """).update();
    }

    private static ProjectRow map(ResultSet rs, int row) throws SQLException {
        Object ai = rs.getObject("ai_available");
        return new ProjectRow(rs.getLong("id"), rs.getLong("owner_id"), rs.getString("name"), rs.getString("source_type"),
            rs.getString("source_url"), rs.getString("branch"), rs.getString("depth"), rs.getString("status"),
            rs.getInt("progress"), rs.getString("status_message"), ai == null ? null : (Boolean) ai, rs.getString("stats"),
            instant(rs.getObject("created_at", OffsetDateTime.class)), instant(rs.getObject("completed_at", OffsetDateTime.class)));
    }

    private static Instant instant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 497) + "...";
    }
}
