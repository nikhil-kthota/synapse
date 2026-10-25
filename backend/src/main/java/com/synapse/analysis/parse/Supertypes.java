package com.synapse.analysis.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads `extends` / `implements` style relationships from a class declaration header. */
final class Supertypes {

    private static final Pattern EXTENDS = Pattern.compile("\\bextends\\s+(.+?)(?=\\bimplements\\b|\\bwith\\b|\\{|$)", Pattern.DOTALL);
    private static final Pattern IMPLEMENTS = Pattern.compile("\\bimplements\\s+(.+?)(?=\\{|$)", Pattern.DOTALL);
    private static final Pattern PYTHON_BASES = Pattern.compile("class\\s+\\w+\\s*(?:\\[[^\\]]*\\])?\\s*\\((.*)\\)", Pattern.DOTALL);
    private static final Pattern RUBY_BASE = Pattern.compile("class\\s+[\\w:]+\\s*<\\s*([\\w:]+)");
    private static final Pattern COLON_BASES = Pattern.compile("^[^:(]*?(?:\\([^)]*\\))?\\s*:\\s*([^{]+)", Pattern.DOTALL);
    private static final Set<String> IGNORED = Set.of("object", "Object", "public", "private", "protected", "virtual",
        "metaclass", "ABC", "Generic", "Protocol", "where");

    private Supertypes() {
    }

    /** Returns [extends, implements]. */
    static List<List<String>> parse(Language language, String header, String className) {
        List<String> ext = new ArrayList<>();
        List<String> impl = new ArrayList<>();
        String h = header.replaceAll("//[^\n]*|/\\*.*?\\*/|@\\w+(\\([^)]*\\))?", " ");
        switch (language) {
            case PYTHON -> {
                Matcher m = PYTHON_BASES.matcher(h);
                if (m.find()) {
                    // Drop keyword arguments such as metaclass=ABCMeta before reading base classes.
                    ext.addAll(split(m.group(1).replaceAll("\\w+\\s*=\\s*[^,]+", "")));
                }
            }
            case RUBY -> {
                Matcher m = RUBY_BASE.matcher(h);
                if (m.find()) ext.add(m.group(1));
            }
            case CPP, CSHARP, KOTLIN, SWIFT -> {
                int nameAt = h.indexOf(className);
                String afterName = nameAt >= 0 ? h.substring(nameAt + className.length()) : h;
                afterName = afterName.replaceAll("<[^<>]*>", "");
                Matcher m = COLON_BASES.matcher(afterName);
                if (m.find()) {
                    List<String> bases = split(m.group(1).replaceAll("\\bwhere\\b.*", ""));
                    for (int i = 0; i < bases.size(); i++) {
                        String base = bases.get(i);
                        boolean interfaceLike = (language == Language.CSHARP && base.matches("I[A-Z]\\w*"))
                            || (language == Language.KOTLIN && !m.group(1).contains(base + "("))
                            || (language == Language.CSHARP && i > 0);
                        (interfaceLike ? impl : ext).add(base);
                    }
                }
            }
            default -> {
                Matcher e = EXTENDS.matcher(h);
                if (e.find()) ext.addAll(split(e.group(1)));
                Matcher i = IMPLEMENTS.matcher(h);
                if (i.find()) impl.addAll(split(i.group(1)));
                if (language == Language.JAVA || language == Language.TYPESCRIPT) {
                    // Java/TS interfaces "extend" other interfaces; treat both the same way.
                }
            }
        }
        return List.of(ext, impl);
    }

    private static List<String> split(String list) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : list.toCharArray()) {
            if (c == '<' || c == '(' || c == '[') depth++;
            if (c == '>' || c == ')' || c == ']') depth--;
            if (c == ',' && depth == 0) {
                add(out, current.toString());
                current.setLength(0);
            } else if (depth == 0 && c != '>' && c != ')' && c != ']') {
                current.append(c);
            }
        }
        add(out, current.toString());
        return out;
    }

    private static void add(List<String> out, String raw) {
        String words = raw.replaceAll("\\b(public|private|protected|virtual|internal|open|abstract)\\b", " ").trim();
        String name = TreeSitterExtractor.clean(words.contains(" ") ? words.substring(words.lastIndexOf(' ') + 1) : words);
        if (name != null && !IGNORED.contains(name)) {
            out.add(name);
        }
    }
}
