package com.synapse.analysis.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.synapse.config.SynapseProperties;

/** Brings a repository onto local disk, either by cloning from GitHub or by unpacking an uploaded ZIP. */
@Component
public class SourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(SourceFetcher.class);

    private final SynapseProperties properties;

    public SourceFetcher(SynapseProperties properties) {
        this.properties = properties;
    }

    public Path workspaceFor(long projectId) throws IOException {
        Path dir = properties.analysis().workspace().resolve("project-" + projectId);
        deleteRecursively(dir);
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Shallow-clones a repository. The access token is only used for this clone; it is never
     * stored, logged, or included in error messages.
     */
    public void cloneGitHub(String repoUrl, String branch, String token, Path target) throws FetchException {
        var clone = Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(target.toFile())
            .setDepth(1)
            .setCloneSubmodules(false);
        if (branch != null && !branch.isBlank()) {
            clone.setBranch(branch.trim());
        }
        if (token != null && !token.isBlank()) {
            clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", token.trim()));
        }
        try (Git git = clone.call()) {
            // Closing Git releases the repository's file handles before .git is removed below.
        } catch (TransportException e) {
            log.warn("Clone failed: {}", e.getMessage());
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            if (msg.contains("not authorized") || msg.contains("authentication") || msg.contains("401") || msg.contains("403")) {
                throw new FetchException("GitHub refused access. For a private repository, add a personal access token with read access.");
            }
            if (msg.contains("not found") || msg.contains("404")) {
                throw new FetchException("Repository not found. Check the URL, or add a token if it is private.");
            }
            if (msg.contains("remote branch") || msg.contains("ref")) {
                throw new FetchException("Branch '" + branch + "' was not found in this repository.");
            }
            throw new FetchException("Could not reach GitHub. Check your internet connection and the repository URL.");
        } catch (GitAPIException e) {
            log.warn("Clone failed", e);
            throw new FetchException("Cloning failed: " + e.getClass().getSimpleName());
        }
        // Drop git metadata once JGit has released its file handles (Windows locks open pack files).
        try {
            deleteRecursively(target.resolve(".git"));
        } catch (IOException e) {
            log.debug("Could not remove .git folder; it is skipped during scanning anyway", e);
        }
    }

    /** Unpacks a ZIP safely: rejects path traversal and stops at the configured size limit. */
    public void unzip(Path zipFile, Path target) throws FetchException {
        long maxBytes = properties.analysis().maxRepoBytes();
        long written = 0;
        Path root = target.toAbsolutePath().normalize();
        try (InputStream in = Files.newInputStream(zipFile); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 200_000) {
                    throw new FetchException("The ZIP contains too many files.");
                }
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) {
                    throw new FetchException("The ZIP contains an unsafe file path and was rejected.");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                written += Files.copy(new LimitedInputStream(zip, maxBytes - written), out, StandardCopyOption.REPLACE_EXISTING);
                if (written >= maxBytes) {
                    throw new FetchException("The unpacked repository is larger than " + (maxBytes / (1024 * 1024)) + " MB.");
                }
            }
        } catch (FetchException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw new FetchException("The uploaded file is not a valid ZIP archive.");
        }
        if (written == 0) {
            throw new FetchException("The upload is empty or not a valid ZIP archive.");
        }
    }

    /** If everything sits inside one top-level folder (typical for GitHub downloads), analyse that folder. */
    public Path effectiveRoot(Path dir) throws IOException {
        try (var children = Files.list(dir)) {
            var list = children.filter(p -> !p.getFileName().toString().startsWith("__MACOSX")).toList();
            if (list.size() == 1 && Files.isDirectory(list.get(0))) {
                return list.get(0);
            }
        }
        return dir;
    }

    public void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                p.toFile().setWritable(true);
                Files.deleteIfExists(p);
            }
        }
    }

    public static class FetchException extends Exception {
        public FetchException(String message) {
            super(message);
        }
    }

    private static final class LimitedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = Math.max(0, limit);
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = in.read();
            if (b >= 0) remaining--;
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int n = in.read(buf, off, (int) Math.min(len, remaining));
            if (n > 0) remaining -= n;
            return n;
        }
    }
}
