package com.synapse.analysis.ingest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.synapse.analysis.parse.Language;
import com.synapse.config.SynapseProperties;

/** Walks a checked-out repository and returns the text files worth analysing. */
@Component
public class FileScanner {

    public record SourceFile(String path, Language language, String content) {
    }

    public record ScanResult(List<SourceFile> files, int skipped, boolean truncated) {
    }

    private static final Set<String> SKIP_DIRS = Set.of(
        ".git", ".svn", ".hg", "node_modules", "bower_components", "vendor", "dist", "build", "target", "out", "bin",
        "obj", ".venv", "venv", "env", "__pycache__", ".idea", ".vscode", ".gradle", ".next", ".nuxt", "coverage",
        "Pods", ".terraform", ".cache", ".pytest_cache", ".mypy_cache", ".tox", "site-packages", "DerivedData",
        ".dart_tool", "Carthage", ".angular", ".svelte-kit", "__MACOSX");

    private static final Set<String> SKIP_FILES = Set.of("package-lock.json", "yarn.lock", "pnpm-lock.yaml",
        "composer.lock", "cargo.lock", "poetry.lock", "gemfile.lock", "go.sum", ".ds_store");

    private final SynapseProperties properties;

    public FileScanner(SynapseProperties properties) {
        this.properties = properties;
    }

    public ScanResult scan(Path root) throws IOException {
        List<SourceFile> files = new ArrayList<>();
        int[] skipped = {0};
        boolean[] truncated = {false};
        int maxFiles = properties.analysis().maxFiles();
        long maxBytes = properties.analysis().maxFileBytes();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (!dir.equals(root) && (SKIP_DIRS.contains(name) || name.startsWith("."))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (files.size() >= maxFiles) {
                    truncated[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                String name = file.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                // Never ingest secrets or lockfiles.
                if (SKIP_FILES.contains(lower) || lower.startsWith(".env") || lower.endsWith(".min.js") || lower.endsWith(".min.css")
                    || lower.endsWith(".map") || lower.endsWith(".pem") || lower.endsWith(".key")) {
                    skipped[0]++;
                    return FileVisitResult.CONTINUE;
                }
                Language language = Language.detect(name);
                if (language == null || attrs.size() > maxBytes || attrs.size() == 0) {
                    skipped[0]++;
                    return FileVisitResult.CONTINUE;
                }
                String content = readText(file);
                if (content == null) {
                    skipped[0]++;
                    return FileVisitResult.CONTINUE;
                }
                String relative = root.relativize(file).toString().replace('\\', '/');
                files.add(new SourceFile(relative, language, content));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                skipped[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort((a, b) -> a.path().compareTo(b.path()));
        return new ScanResult(files, skipped[0], truncated[0]);
    }

    /** Reads UTF-8 (falling back to Latin-1 for legacy code); returns null for binary files. */
    private static String readText(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int probe = Math.min(bytes.length, 8000);
            for (int i = 0; i < probe; i++) {
                if (bytes[i] == 0) return null;
            }
            try {
                return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString().replace("\r\n", "\n");
            } catch (CharacterCodingException e) {
                return new String(bytes, StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
            }
        } catch (IOException e) {
            return null;
        }
    }
}
