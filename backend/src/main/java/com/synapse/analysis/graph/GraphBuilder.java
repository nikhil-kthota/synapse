package com.synapse.analysis.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.synapse.analysis.graph.CodeGraph.Node;
import com.synapse.analysis.ingest.FileScanner.SourceFile;
import com.synapse.analysis.parse.FileModel;
import com.synapse.analysis.parse.FileModel.SymbolKind;
import com.synapse.analysis.parse.Language;
import com.synapse.analysis.parse.ManifestParser;

/**
 * Links parsed files into one knowledge graph: containment (repo / folders / files / symbols),
 * imports, calls, inheritance, HTTP endpoints and third-party dependencies.
 */
public class GraphBuilder {

    private static final int FILE_SNIPPET_LINES = 400;
    private static final int FILE_SNIPPET_CHARS = 20_000;
    private static final int MAX_GLOBAL_CANDIDATES = 2;

    /** Names so common that a project-wide match is more likely a library call than ours. */
    private static final Set<String> COMMON_NAMES = Set.of("get", "set", "add", "put", "remove", "run", "call", "apply",
        "map", "filter", "reduce", "init", "start", "stop", "close", "open", "read", "write", "update", "delete", "create",
        "find", "save", "load", "execute", "handle", "process", "send", "push", "pop", "append", "join", "split", "format",
        "parse", "toString", "equals", "hashCode", "length", "size", "count", "keys", "values", "items", "next", "log",
        "print", "debug", "info", "error", "warn", "then", "catch", "emit", "on", "off", "use", "render", "main", "test",
        "setup", "build", "clear", "reset", "list", "show", "connect", "query", "fetch", "submit", "validate",
        "contains", "isEmpty", "of", "from", "to", "with", "new", "default", "index", "toJSON", "valueOf", "forEach");

    private static final Set<Language> CALLABLE_CLASSES = Set.of(Language.PYTHON, Language.KOTLIN, Language.SCALA,
        Language.SWIFT, Language.DART, Language.RUST);
    private static final List<String> RESOLVE_EXTENSIONS = List.of("", ".js", ".jsx", ".ts", ".tsx", ".mjs", ".cjs",
        ".vue", ".svelte", ".py", ".rb", ".php", ".rs", ".go", ".h", ".hpp", ".css", ".scss", ".lua", ".dart");
    private static final List<String> INDEX_FILES = List.of("/index", "/__init__", "/mod", "/main", "/lib");

    private final CodeGraph graph = new CodeGraph();
    private final Map<String, Integer> dirNodes = new HashMap<>();
    private final Map<String, Integer> fileNodes = new HashMap<>();                  // path -> node
    private final Map<String, List<Integer>> filesByNoExt = new HashMap<>();        // lower path w/o ext -> nodes
    private final Map<String, List<Integer>> filesByLowerPath = new HashMap<>();    // lower path -> nodes
    private final Map<String, List<Integer>> filesByPackage = new HashMap<>();
    private final Map<String, List<Integer>> suffixNoExt = new HashMap<>();         // every trailing path suffix
    private final Map<String, List<Integer>> suffixFull = new HashMap<>();
    private final Map<String, List<Integer>> suffixDir = new HashMap<>();
    private final Map<Integer, String> fileDir = new HashMap<>();
    private final Map<Integer, String> filePackage = new HashMap<>();
    private final Map<Integer, Integer> fileOf = new HashMap<>();                   // symbol node -> file node
    private final Map<String, List<Integer>> symbolsByName = new HashMap<>();
    private final Map<String, List<Integer>> classesByName = new HashMap<>();
    private final Map<Integer, Language> nodeLanguage = new HashMap<>();
    private final Map<String, Integer> externals = new HashMap<>();
    private final Map<Integer, Set<Integer>> importedFiles = new HashMap<>();

    public CodeGraph build(String projectName, List<SourceFile> files, List<FileModel> models) {
        Node repo = graph.addNode(CodeGraph.REPOSITORY, null, projectName, "/", null, null, null, null, null, null);
        dirNodes.put("", repo.id);

        Map<String, String> contentByPath = new HashMap<>();
        for (SourceFile f : files) contentByPath.put(f.path(), f.content());

        // Pass 1: folders, files and declared symbols.
        List<int[]> symbolNodeIds = new ArrayList<>();
        for (FileModel model : models) {
            int fileId = addFile(model, contentByPath.get(model.path()));
            symbolNodeIds.add(addSymbols(model, fileId));
        }
        linkDetachedMethods(models, symbolNodeIds);

        // Pass 2: relationships that need every file to be known.
        for (FileModel model : models) {
            resolveImports(model, fileNodes.get(model.path()));
        }
        for (int i = 0; i < models.size(); i++) {
            FileModel model = models.get(i);
            int fileId = fileNodes.get(model.path());
            int[] ids = symbolNodeIds.get(i);
            resolveInheritance(model, fileId, ids);
            resolveCalls(model, fileId, ids);
            addEndpoints(model, fileId, ids);
        }
        for (SourceFile f : files) {
            String name = f.path().substring(f.path().lastIndexOf('/') + 1);
            if (ManifestParser.isManifest(name)) {
                for (ManifestParser.Dependency dep : ManifestParser.parse(name, f.content())) {
                    String key = dep.ecosystem().equals("maven") ? dep.name().split(":")[0] : dep.name();
                    graph.addEdge(fileNodes.get(f.path()), external(key, dep.ecosystem()), CodeGraph.DEPENDS_ON);
                }
            }
        }
        return graph;
    }

    // ---------------------------------------------------------------- structure

    private int addFile(FileModel model, String content) {
        String path = model.path();
        int slash = path.lastIndexOf('/');
        String dir = slash < 0 ? "" : path.substring(0, slash);
        String name = slash < 0 ? path : path.substring(slash + 1);
        int dirId = directory(dir);
        Node file = graph.addNode(CodeGraph.FILE, model.language().category().name().toLowerCase(Locale.ROOT), name, path,
            model.language().displayName(), path, 1, model.lineCount(), null, fileSnippet(content));
        graph.addEdge(dirId, file.id, CodeGraph.CONTAINS);
        fileNodes.put(path, file.id);
        fileDir.put(file.id, dir);
        nodeLanguage.put(file.id, model.language());
        String lower = path.toLowerCase(Locale.ROOT);
        filesByLowerPath.computeIfAbsent(lower, k -> new ArrayList<>()).add(file.id);
        int dot = lower.lastIndexOf('.');
        String noExt = dot > lower.lastIndexOf('/') ? lower.substring(0, dot) : lower;
        filesByNoExt.computeIfAbsent(noExt, k -> new ArrayList<>()).add(file.id);
        indexSuffixes(suffixFull, lower, file.id);
        indexSuffixes(suffixNoExt, noExt, file.id);
        if (model.packageName() != null) {
            filePackage.put(file.id, model.packageName());
            filesByPackage.computeIfAbsent(model.packageName(), k -> new ArrayList<>()).add(file.id);
        }
        return file.id;
    }

    private int directory(String dir) {
        Integer existing = dirNodes.get(dir);
        if (existing != null) return existing;
        int slash = dir.lastIndexOf('/');
        String parent = slash < 0 ? "" : dir.substring(0, slash);
        String name = slash < 0 ? dir : dir.substring(slash + 1);
        int parentId = directory(parent);
        Node node = graph.addNode(CodeGraph.DIRECTORY, null, name, dir, null, dir, null, null, null, null);
        graph.addEdge(parentId, node.id, CodeGraph.CONTAINS);
        dirNodes.put(dir, node.id);
        indexSuffixes(suffixDir, dir.toLowerCase(Locale.ROOT), node.id);
        return node.id;
    }

    /** Indexes "a/b/c" under "c", "b/c" and "a/b/c" so suffix lookups are a single map read. */
    private static void indexSuffixes(Map<String, List<Integer>> index, String path, int id) {
        int from = path.length();
        while (true) {
            int slash = path.lastIndexOf('/', from - 1);
            String suffix = path.substring(slash + 1);
            List<Integer> list = index.computeIfAbsent(suffix, k -> new ArrayList<>(2));
            if (list.size() < 6) list.add(id);
            if (slash < 0) break;
            from = slash;
        }
    }

    private int[] addSymbols(FileModel model, int fileId) {
        List<FileModel.Symbol> symbols = model.symbols();
        int[] ids = new int[symbols.size()];
        for (FileModel.Symbol s : symbols) {
            String kind = switch (s.kind()) {
                case CLASS -> CodeGraph.CLASS;
                case METHOD -> CodeGraph.METHOD;
                case FUNCTION -> CodeGraph.FUNCTION;
            };
            Node node = graph.addNode(kind, s.subkind(), s.name(), model.path() + "#" + qualifiedName(symbols, s),
                model.language().displayName(), model.path(), s.startLine(), s.endLine(), s.signature(), s.snippet());
            ids[s.index()] = node.id;
            int parent = s.parentIndex() >= 0 ? ids[s.parentIndex()] : fileId;
            graph.addEdge(parent, node.id, CodeGraph.CONTAINS);
            fileOf.put(node.id, fileId);
            nodeLanguage.put(node.id, model.language());
            symbolsByName.computeIfAbsent(s.name(), k -> new ArrayList<>()).add(node.id);
            if (s.kind() == SymbolKind.CLASS) {
                classesByName.computeIfAbsent(s.name(), k -> new ArrayList<>()).add(node.id);
            }
        }
        return ids;
    }

    /** Go receivers and Rust impl blocks declare methods away from their type; attach them to it. */
    private void linkDetachedMethods(List<FileModel> models, List<int[]> symbolNodeIds) {
        for (int i = 0; i < models.size(); i++) {
            FileModel model = models.get(i);
            int fileId = fileNodes.get(model.path());
            for (FileModel.Symbol s : model.symbols()) {
                if (s.parentName() == null || s.parentIndex() >= 0) continue;
                Integer owner = pick(classesByName.get(s.parentName()), fileId, null);
                if (owner != null) {
                    graph.addEdge(owner, symbolNodeIds.get(i)[s.index()], CodeGraph.CONTAINS);
                }
            }
        }
    }

    // ---------------------------------------------------------------- imports

    private void resolveImports(FileModel model, int fileId) {
        Set<Integer> imported = importedFiles.computeIfAbsent(fileId, k -> new LinkedHashSet<>());
        for (String raw : model.imports()) {
            List<Integer> targets = resolveImport(model, raw);
            if (!targets.isEmpty()) {
                for (int target : targets) {
                    if (target != fileId) {
                        graph.addEdge(fileId, target, CodeGraph.IMPORTS);
                        imported.add(target);
                    }
                }
            } else {
                String key = externalKey(model.language(), raw);
                if (key != null) {
                    graph.addEdge(fileId, external(key, null), CodeGraph.IMPORTS);
                }
            }
        }
    }

    private List<Integer> resolveImport(FileModel model, String raw) {
        Language lang = model.language();
        String dir = model.path().contains("/") ? model.path().substring(0, model.path().lastIndexOf('/')) : "";

        // Package-style imports (Java, Kotlin, C#, PHP, Scala).
        String dotted = raw.replace('\\', '.').replace("::", ".").replaceAll("\\.\\*$", "");
        if (!filesByPackage.isEmpty()) {
            List<Integer> inPackage = filesByPackage.get(dotted);
            if (inPackage != null) {
                return inPackage.size() > 8 ? inPackage.subList(0, 8) : inPackage;
            }
            int lastDot = dotted.lastIndexOf('.');
            if (lastDot > 0) {
                List<Integer> pkgFiles = filesByPackage.get(dotted.substring(0, lastDot));
                String cls = dotted.substring(lastDot + 1);
                if (pkgFiles != null) {
                    for (int f : pkgFiles) {
                        String p = graph.node(f).filePath;
                        String base = p.substring(p.lastIndexOf('/') + 1);
                        if (base.startsWith(cls + ".")) return List.of(f);
                    }
                    List<Integer> classes = classesByName.getOrDefault(cls, List.of());
                    for (int c : classes) {
                        if (pkgFiles.contains(fileOf.get(c))) return List.of(fileOf.get(c));
                    }
                }
            }
        }

        // Relative paths: ./x, ../x, Python leading dots, Ruby require_relative style.
        String candidate = null;
        if (raw.startsWith("./") || raw.startsWith("../")) {
            candidate = normalize(dir + "/" + raw);
        } else if (lang == Language.PYTHON && raw.startsWith(".")) {
            int dots = 0;
            while (dots < raw.length() && raw.charAt(dots) == '.') dots++;
            String base = dir;
            for (int i = 1; i < dots && base.contains("/"); i++) base = base.substring(0, base.lastIndexOf('/'));
            if (dots > 1 && !dir.contains("/")) base = "";
            String rest = raw.substring(dots).replace('.', '/');
            candidate = rest.isEmpty() ? base : (base.isEmpty() ? rest : base + "/" + rest);
        } else if (raw.startsWith("@/") || raw.startsWith("~/")) {
            candidate = "src/" + raw.substring(2);
        }
        if (candidate != null) {
            List<Integer> found = lookupPath(candidate, true);
            if (!found.isEmpty()) return found;
            if (lang == Language.PYTHON) return List.of();
            List<Integer> folder = lookupDirectory(candidate);
            return folder;
        }

        // Path-like or dotted absolute imports: match by path suffix.
        boolean keepDots = lang == Language.JAVASCRIPT || lang == Language.TYPESCRIPT || lang == Language.VUE
            || lang == Language.SVELTE || lang == Language.C || lang == Language.CPP || lang == Language.OBJECTIVE_C
            || lang == Language.HTML || lang == Language.CSS || lang == Language.RUBY || lang == Language.LUA
            || lang == Language.DART || lang == Language.SHELL || lang == Language.R || lang == Language.PHP && raw.contains("/");
        String path = raw.replace('\\', '/').replace("::", "/");
        if (!keepDots) path = path.replace('.', '/');
        path = path.replaceAll("^(crate|self|super)/", "").replaceAll("^package:[^/]+/", "lib/").replaceAll("/\\*$", "");
        String[] segments = path.split("/");
        for (int drop = 0; drop <= 2 && segments.length - drop >= 1; drop++) {
            String suffix = String.join("/", java.util.Arrays.copyOf(segments, segments.length - drop));
            if (suffix.isBlank()) break;
            boolean single = !suffix.contains("/");
            if (single && drop > 0) break;
            List<Integer> found = lookupSuffix(suffix, single, lang);
            if (!found.isEmpty()) return found;
        }
        return List.of();
    }

    private List<Integer> lookupPath(String path, boolean tryIndexFiles) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (String ext : RESOLVE_EXTENSIONS) {
            List<Integer> exact = filesByLowerPath.get(lower + ext);
            if (exact != null) return exact;
        }
        List<Integer> noExt = filesByNoExt.get(lower);
        if (noExt != null) return noExt.subList(0, 1);
        if (tryIndexFiles) {
            for (String index : INDEX_FILES) {
                List<Integer> found = filesByNoExt.get(lower + index);
                if (found != null) return found.subList(0, 1);
            }
        }
        return List.of();
    }

    private List<Integer> lookupDirectory(String dir) {
        Integer id = dirNodes.get(dir);
        return id == null || dir.isEmpty() ? List.of() : List.of(id);
    }

    /** Finds files (or folders) whose path ends with the given suffix; ambiguous matches are ignored. */
    private List<Integer> lookupSuffix(String suffix, boolean single, Language lang) {
        String lower = suffix.toLowerCase(Locale.ROOT);
        List<Integer> matches = new ArrayList<>((lower.contains(".") ? suffixFull : suffixNoExt).getOrDefault(lower, List.of()));
        if (matches.size() > 4) return List.of();
        if (matches.isEmpty()) {
            for (String index : INDEX_FILES) {
                matches.addAll(suffixNoExt.getOrDefault(lower + index, List.of()));
                if (!matches.isEmpty()) break;
            }
        }
        if (matches.isEmpty() && (lang == Language.GO || !single)) {
            matches.addAll(suffixDir.getOrDefault(lower, List.of()));
            if (matches.size() > 1) return List.of();
        }
        if (single && matches.size() > 1) {
            // A bare module name like "utils" only counts if it is unique in the same language.
            matches.removeIf(id -> nodeLanguage.get(id) != null && nodeLanguage.get(id) != lang);
            if (matches.size() != 1) return List.of();
        }
        return matches;
    }

    private int external(String key, String ecosystem) {
        String lower = key.toLowerCase(Locale.ROOT);
        Integer id = externals.get(lower);
        if (id == null) {
            Node node = graph.addNode(CodeGraph.EXTERNAL, ecosystem, key, "ext:" + key, null, null, null, null, null, null);
            externals.put(lower, node.id);
            id = node.id;
        }
        return id;
    }

    /** Groups an unresolved import under its library name (react, numpy, org.springframework, fmt...). */
    static String externalKey(Language lang, String raw) {
        String s = raw.trim();
        if (s.isEmpty() || s.startsWith(".") || s.startsWith("/")) return null;
        switch (lang) {
            case JAVASCRIPT, TYPESCRIPT, VUE, SVELTE -> {
                s = s.replaceFirst("^node:", "");
                String[] parts = s.split("/");
                return s.startsWith("@") && parts.length > 1 ? parts[0] + "/" + parts[1] : parts[0];
            }
            case JAVA, KOTLIN, SCALA, GROOVY -> {
                String[] parts = s.replaceAll("\\.\\*$", "").split("\\.");
                int keep = Set.of("com", "org", "io", "net", "dev", "me", "edu").contains(parts[0]) ? 3 : 2;
                return String.join(".", java.util.Arrays.copyOf(parts, Math.min(keep, Math.max(1, parts.length - 1))));
            }
            case CSHARP, FSHARP -> {
                String[] parts = s.split("\\.");
                return parts.length > 1 && !parts[0].equals("System") && !parts[0].equals("Microsoft") ? parts[0] + "." + parts[1] : parts[0];
            }
            case GO -> {
                String[] parts = s.split("/");
                return parts[0].contains(".") ? String.join("/", java.util.Arrays.copyOf(parts, Math.min(3, parts.length))) : parts[0];
            }
            case C, CPP, OBJECTIVE_C -> {
                return s;
            }
            case PHP -> {
                return s.replace('\\', '/').split("/")[0];
            }
            case HTML, CSS -> {
                return s.startsWith("http") ? null : s;
            }
            default -> {
                return s.split("[./:\\\\]")[0];
            }
        }
    }

    // ---------------------------------------------------------------- calls and types

    private void resolveCalls(FileModel model, int fileId, int[] ids) {
        boolean classesCallable = CALLABLE_CLASSES.contains(model.language());
        for (FileModel.Call call : model.calls()) {
            int caller = call.ownerIndex() >= 0 ? ids[call.ownerIndex()] : fileId;
            Integer target;
            if (call.instantiation()) {
                target = pick(classesByName.get(call.callee()), fileId, model.language(), call.callee());
            } else {
                // A method calling another method with its own name (e.g. a test helper `validate` calling
                // `validator.validate`) is a call elsewhere, not recursion we can resolve to itself.
                target = pick(without(symbolsByName.get(call.callee()), caller), fileId, model.language(), call.callee());
                if (target != null && !classesCallable && CodeGraph.CLASS.equals(graph.node(target).kind)) {
                    target = null;
                }
            }
            if (target != null && target != caller) {
                boolean isClass = CodeGraph.CLASS.equals(graph.node(target).kind);
                graph.addEdge(caller, target, isClass ? CodeGraph.INSTANTIATES : CodeGraph.CALLS, call.line());
            }
        }
    }

    private void resolveInheritance(FileModel model, int fileId, int[] ids) {
        for (FileModel.Symbol s : model.symbols()) {
            if (s.kind() != SymbolKind.CLASS) continue;
            for (String parent : s.extendsNames()) {
                Integer target = pick(classesByName.get(parent), fileId, model.language());
                if (target != null) graph.addEdge(ids[s.index()], target, CodeGraph.EXTENDS);
            }
            for (String parent : s.implementsNames()) {
                Integer target = pick(classesByName.get(parent), fileId, model.language());
                if (target != null) graph.addEdge(ids[s.index()], target, CodeGraph.IMPLEMENTS);
            }
        }
        for (FileModel.Relation r : model.relations()) {
            Integer from = pick(classesByName.get(r.fromName()), fileId, model.language());
            Integer to = pick(classesByName.get(r.toName()), fileId, model.language());
            if (from != null && to != null) graph.addEdge(from, to, r.kind());
        }
    }

    /**
     * Chooses which declaration a name refers to: same file first, then imported files, then the
     * same folder, then a project-wide match if it is unambiguous.
     */
    private Integer pick(List<Integer> candidates, int fileId, Language language) {
        return pick(candidates, fileId, language, null);
    }

    private Integer pick(List<Integer> candidates, int fileId, Language language, String calleeName) {
        if (candidates == null || candidates.isEmpty()) return null;
        for (int c : candidates) {
            if (fileOf.get(c) == fileId) return c;
        }
        Set<Integer> imported = importedFiles.getOrDefault(fileId, Set.of());
        for (int c : candidates) {
            if (imported.contains(fileOf.get(c))) return c;
        }
        String dir = fileDir.get(fileId);
        Integer sameDir = null;
        for (int c : candidates) {
            if (dir.equals(fileDir.get(fileOf.get(c)))) {
                if (sameDir != null) return null;
                sameDir = c;
            }
        }
        if (sameDir != null) return sameDir;
        // Same package in another folder, e.g. src/test/... next to src/main/... in Java projects.
        String pkg = filePackage.get(fileId);
        if (pkg != null) {
            Integer samePackage = null;
            for (int c : candidates) {
                if (pkg.equals(filePackage.get(fileOf.get(c)))) {
                    if (samePackage != null) return null;
                    samePackage = c;
                }
            }
            if (samePackage != null) return samePackage;
        }
        if (calleeName != null && COMMON_NAMES.contains(calleeName)) {
            return null;
        }
        if (candidates.size() == 1) {
            int only = candidates.get(0);
            return language == null || compatible(language, nodeLanguage.get(only)) ? only : null;
        }
        List<Integer> sameLanguage = new ArrayList<>();
        for (int c : candidates) {
            if (language == null || compatible(language, nodeLanguage.get(c))) sameLanguage.add(c);
        }
        Set<Integer> distinctFiles = new HashSet<>();
        for (int c : sameLanguage) distinctFiles.add(fileOf.get(c));
        return sameLanguage.size() >= 1 && sameLanguage.size() <= MAX_GLOBAL_CANDIDATES && distinctFiles.size() == 1
            ? sameLanguage.get(0) : null;
    }

    private static List<Integer> without(List<Integer> candidates, int exclude) {
        if (candidates == null || !candidates.contains(exclude)) return candidates;
        List<Integer> rest = new ArrayList<>(candidates);
        rest.remove(Integer.valueOf(exclude));
        return rest;
    }

    private static boolean compatible(Language a, Language b) {
        if (a == b) return true;
        Set<Language> js = Set.of(Language.JAVASCRIPT, Language.TYPESCRIPT, Language.VUE, Language.SVELTE);
        Set<Language> jvm = Set.of(Language.JAVA, Language.KOTLIN, Language.SCALA, Language.GROOVY);
        Set<Language> c = Set.of(Language.C, Language.CPP, Language.OBJECTIVE_C);
        return js.contains(a) && js.contains(b) || jvm.contains(a) && jvm.contains(b) || c.contains(a) && c.contains(b);
    }

    // ---------------------------------------------------------------- endpoints

    private void addEndpoints(FileModel model, int fileId, int[] ids) {
        for (FileModel.Endpoint e : model.endpoints()) {
            String name = e.method() + " " + e.path();
            Node node = graph.addNode(CodeGraph.ENDPOINT, e.method(), name, model.path() + "@" + name,
                model.language().displayName(), model.path(), e.line(), e.line(), name, null);
            graph.addEdge(fileId, node.id, CodeGraph.CONTAINS);
            int handler = handlerFor(model, e.line());
            if (handler >= 0) {
                graph.addEdge(node.id, ids[handler], CodeGraph.HANDLES);
            }
        }
    }

    /** The function containing the route declaration, or the one declared right after an annotation. */
    private static int handlerFor(FileModel model, int line) {
        int owner = model.ownerAtLine(line);
        if (owner >= 0) return owner;
        for (FileModel.Symbol s : model.symbols()) {
            if (s.kind() != SymbolKind.CLASS && s.startLine() >= line && s.startLine() - line <= 6) return s.index();
        }
        return -1;
    }

    // ---------------------------------------------------------------- helpers

    private static String qualifiedName(List<FileModel.Symbol> symbols, FileModel.Symbol s) {
        StringBuilder sb = new StringBuilder(s.name());
        FileModel.Symbol current = s;
        int guard = 0;
        while (current.parentIndex() >= 0 && guard++ < 20) {
            current = symbols.get(current.parentIndex());
            sb.insert(0, current.name() + ".");
        }
        if (s.parentName() != null && s.parentIndex() < 0) sb.insert(0, s.parentName() + ".");
        return sb.toString();
    }

    private static String normalize(String path) {
        List<String> out = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
            } else {
                out.add(part);
            }
        }
        return String.join("/", out);
    }

    private static String fileSnippet(String content) {
        if (content == null) return null;
        String[] lines = content.split("\n", FILE_SNIPPET_LINES + 1);
        String text = lines.length > FILE_SNIPPET_LINES
            ? String.join("\n", java.util.Arrays.copyOf(lines, FILE_SNIPPET_LINES)) + "\n…" : content;
        return text.length() > FILE_SNIPPET_CHARS ? text.substring(0, FILE_SNIPPET_CHARS) + "\n…" : text;
    }
}
