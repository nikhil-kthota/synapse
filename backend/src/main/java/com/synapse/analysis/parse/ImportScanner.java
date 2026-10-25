package com.synapse.analysis.parse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the modules a file imports, as written in the source (resolution happens in the graph builder). */
final class ImportScanner {

    private static final int M = Pattern.MULTILINE;

    private static final Map<Language, List<Pattern>> PATTERNS = Map.ofEntries(
        Map.entry(Language.JAVA, List.of(Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;", M))),
        Map.entry(Language.KOTLIN, List.of(Pattern.compile("^\\s*import\\s+([\\w.]+(?:\\.\\*)?)", M))),
        Map.entry(Language.SCALA, List.of(Pattern.compile("^\\s*import\\s+([\\w.]+)", M))),
        Map.entry(Language.GROOVY, List.of(Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)", M))),
        Map.entry(Language.PYTHON, List.of(
            Pattern.compile("^\\s*from\\s+(\\.*[\\w.]*)\\s+import\\s+", M),
            Pattern.compile("^\\s*import\\s+([\\w.]+(?:\\s*,\\s*[\\w.]+)*)", M))),
        Map.entry(Language.JAVASCRIPT, jsPatterns()),
        Map.entry(Language.TYPESCRIPT, jsPatterns()),
        Map.entry(Language.VUE, jsPatterns()),
        Map.entry(Language.SVELTE, jsPatterns()),
        Map.entry(Language.GO, List.of(
            Pattern.compile("^\\s*import\\s+(?:\\w+\\s+)?\"([^\"]+)\"", M),
            Pattern.compile("^\\s+(?:[\\w.]+\\s+)?\"([^\"]+)\"\\s*$", M))),
        Map.entry(Language.C, List.of(Pattern.compile("^\\s*#\\s*include\\s*[<\"]([^>\"]+)[>\"]", M))),
        Map.entry(Language.CPP, List.of(Pattern.compile("^\\s*#\\s*include\\s*[<\"]([^>\"]+)[>\"]", M))),
        Map.entry(Language.OBJECTIVE_C, List.of(Pattern.compile("^\\s*#\\s*(?:include|import)\\s*[<\"]([^>\"]+)[>\"]", M))),
        Map.entry(Language.CSHARP, List.of(Pattern.compile("^\\s*using\\s+(?:static\\s+)?(?:\\w+\\s*=\\s*)?([\\w.]+)\\s*;", M))),
        Map.entry(Language.FSHARP, List.of(Pattern.compile("^\\s*open\\s+([\\w.]+)", M))),
        Map.entry(Language.PHP, List.of(
            Pattern.compile("^\\s*use\\s+([\\w\\\\]+)", M),
            Pattern.compile("(?:require|include)(?:_once)?\\s*\\(?\\s*(?:__DIR__\\s*\\.\\s*)?['\"]([^'\"]+)['\"]"))),
        Map.entry(Language.RUBY, List.of(Pattern.compile("^\\s*require(?:_relative)?\\s*\\(?\\s*['\"]([^'\"]+)['\"]", M))),
        Map.entry(Language.RUST, List.of(
            Pattern.compile("^\\s*(?:pub\\s+)?use\\s+([\\w:]+)", M),
            Pattern.compile("^\\s*(?:pub\\s+)?mod\\s+(\\w+)\\s*;", M))),
        Map.entry(Language.SWIFT, List.of(Pattern.compile("^\\s*import\\s+(\\w+)", M))),
        Map.entry(Language.DART, List.of(Pattern.compile("^\\s*import\\s+['\"]([^'\"]+)['\"]", M))),
        Map.entry(Language.ELIXIR, List.of(Pattern.compile("^\\s*(?:alias|import|use|require)\\s+([\\w.]+)", M))),
        Map.entry(Language.HASKELL, List.of(Pattern.compile("^import\\s+(?:qualified\\s+)?([\\w.]+)", M))),
        Map.entry(Language.PERL, List.of(Pattern.compile("^\\s*(?:use|require)\\s+([\\w:]+)", M))),
        Map.entry(Language.LUA, List.of(Pattern.compile("require\\s*\\(?\\s*['\"]([^'\"]+)['\"]"))),
        Map.entry(Language.R, List.of(
            Pattern.compile("(?:library|require)\\(\\s*['\"]?([\\w.]+)", M),
            Pattern.compile("source\\(\\s*['\"]([^'\"]+)['\"]", M))),
        Map.entry(Language.JULIA, List.of(Pattern.compile("^\\s*(?:using|import)\\s+([\\w.]+)", M))),
        Map.entry(Language.COBOL, List.of(Pattern.compile("\\bCOPY\\s+['\"]?([\\w-]+)", Pattern.CASE_INSENSITIVE))),
        Map.entry(Language.SHELL, List.of(Pattern.compile("^\\s*(?:source|\\.)\\s+['\"]?([\\w./-]+)", M))),
        Map.entry(Language.HTML, List.of(Pattern.compile("<(?:script|link)[^>]+(?:src|href)=['\"]([^'\"#?]+\\.(?:js|css|mjs|ts))['\"]"))),
        Map.entry(Language.CSS, List.of(Pattern.compile("@import\\s+(?:url\\()?['\"]([^'\"]+)['\"]"))));

    private ImportScanner() {
    }

    private static List<Pattern> jsPatterns() {
        return List.of(
            Pattern.compile("^\\s*import\\s+(?:type\\s+)?(?:[\\s\\S]*?\\s+from\\s+)?['\"]([^'\"]+)['\"]", M),
            Pattern.compile("^\\s*export\\s+(?:\\*|\\{[^}]*\\}|\\*\\s+as\\s+\\w+)\\s+from\\s+['\"]([^'\"]+)['\"]", M),
            Pattern.compile("\\brequire\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)"),
            Pattern.compile("\\bimport\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)"));
    }

    static List<String> scan(Language language, String content) {
        List<Pattern> patterns = PATTERNS.get(language);
        if (patterns == null) return List.of();
        Set<String> modules = new LinkedHashSet<>();
        boolean goBlock = language == Language.GO;
        for (Pattern p : patterns) {
            String source = content;
            if (goBlock && p.pattern().startsWith("^\\s+")) {
                source = goImportBlocks(content);
            }
            Matcher m = p.matcher(source);
            while (m.find() && modules.size() < 500) {
                String value = m.group(1).trim();
                if (language == Language.PYTHON && value.contains(",")) {
                    for (String part : value.split("\\s*,\\s*")) modules.add(part);
                } else if (!value.isEmpty()) {
                    modules.add(value);
                }
            }
        }
        return List.copyOf(modules);
    }

    private static final Pattern GO_BLOCK = Pattern.compile("import\\s*\\(([^)]*)\\)", Pattern.DOTALL);

    private static String goImportBlocks(String content) {
        StringBuilder sb = new StringBuilder();
        Matcher m = GO_BLOCK.matcher(content);
        while (m.find()) sb.append(m.group(1)).append('\n');
        return sb.toString();
    }
}
