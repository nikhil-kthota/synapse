package com.synapse.analysis.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.synapse.analysis.parse.FileModel.SymbolKind;

/**
 * Pattern-based extraction for languages without a tree-sitter grammar (COBOL, Scala, Swift, SQL,
 * shell, Fortran, ...). Less precise than a syntax tree, but it finds the declarations and the
 * calls between them, which is what the knowledge graph needs.
 */
public class HeuristicExtractor {

    private record Rule(Pattern pattern, SymbolKind kind, String subkind, int nameGroup) {
    }

    private static final int FLAGS = Pattern.MULTILINE;
    private static final int FLAGS_CI = Pattern.MULTILINE | Pattern.CASE_INSENSITIVE;

    private static final List<Rule> GENERIC = List.of(
        new Rule(Pattern.compile("^[ \\t]*(?:(?:public|private|protected|internal|abstract|final|sealed|static|data|open|export|default|partial|case|inline|value|fileprivate|override)\\s+)*"
            + "(class|interface|struct|trait|object|enum|record|protocol|extension|actor)\\s+([A-Za-z_]\\w*)", FLAGS), SymbolKind.CLASS, null, 2),
        new Rule(Pattern.compile("^[ \\t]*(?:(?:public|private|protected|internal|static|async|override|open|final|inline|export|mutating|fileprivate|suspend|pub)\\s+)*"
            + "(?:def|func|function|fun|fn|sub|proc|defp|defn|defun|defmacro)\\s+([A-Za-z_][\\w.:!?']*)", FLAGS), SymbolKind.FUNCTION, null, 1),
        new Rule(Pattern.compile("^[ \\t]*(?:local\\s+)?function\\s+([A-Za-z_][\\w.:]*)\\s*\\(", FLAGS), SymbolKind.FUNCTION, null, 1),
        new Rule(Pattern.compile("^[ \\t]*([A-Za-z_][\\w.]*)\\s*(?:<-|=)\\s*function\\s*\\(", FLAGS), SymbolKind.FUNCTION, null, 1));

    private static final List<Rule> COBOL = List.of(
        new Rule(Pattern.compile("PROGRAM-ID\\.\\s*([A-Z0-9][\\w-]*)", FLAGS_CI), SymbolKind.CLASS, "program", 1),
        new Rule(Pattern.compile("^.{0,7}\\s{0,4}([A-Z0-9][A-Z0-9-]*)\\s+SECTION\\s*\\.", FLAGS_CI), SymbolKind.FUNCTION, "section", 1),
        new Rule(Pattern.compile("^.{6}[ ]([A-Z0-9][A-Z0-9-]*)\\s*\\.\\s*$", FLAGS_CI), SymbolKind.FUNCTION, "paragraph", 1));

    private static final List<Rule> SQL = List.of(
        new Rule(Pattern.compile("CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:GLOBAL\\s+|TEMP(?:ORARY)?\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.\"`\\[\\]]+)", FLAGS_CI), SymbolKind.CLASS, "table", 1),
        new Rule(Pattern.compile("CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:MATERIALIZED\\s+)?VIEW\\s+([\\w.\"`\\[\\]]+)", FLAGS_CI), SymbolKind.CLASS, "view", 1),
        new Rule(Pattern.compile("CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:FUNCTION|PROCEDURE|TRIGGER|PACKAGE(?:\\s+BODY)?)\\s+([\\w.\"`\\[\\]]+)", FLAGS_CI), SymbolKind.FUNCTION, "routine", 1));

    private static final List<Rule> VB_PASCAL_FORTRAN_ADA = List.of(
        new Rule(Pattern.compile("^[ \\t]*(?:(?:public|private|friend|protected|static|shared|overrides|recursive|pure|elemental)\\s+)*(?:sub|function|subroutine|procedure|program|module)\\s+([A-Za-z_][\\w.]*)", FLAGS_CI), SymbolKind.FUNCTION, null, 1),
        new Rule(Pattern.compile("^[ \\t]*(?:(?:public|private|friend)\\s+)?(?:class|type|package(?:\\s+body)?)\\s+([A-Za-z_][\\w.]*)\\s*(?:=\\s*class|is|$)", FLAGS_CI), SymbolKind.CLASS, null, 1));

    private static final List<Rule> SHELL = List.of(
        new Rule(Pattern.compile("^[ \\t]*(?:function\\s+)?([A-Za-z_][\\w:-]*)\\s*\\(\\s*\\)\\s*\\{?", FLAGS), SymbolKind.FUNCTION, null, 1),
        new Rule(Pattern.compile("^[ \\t]*function\\s+([A-Za-z_][\\w-]*)", FLAGS_CI), SymbolKind.FUNCTION, null, 1),
        new Rule(Pattern.compile("^:([A-Za-z_][\\w-]*)", FLAGS), SymbolKind.FUNCTION, "label", 1));

    private static final List<Rule> HASKELL = List.of(
        new Rule(Pattern.compile("^([a-z_][\\w']*)\\s*::", FLAGS), SymbolKind.FUNCTION, null, 1),
        new Rule(Pattern.compile("^(?:data|newtype|class|type)\\s+([A-Z][\\w']*)", FLAGS), SymbolKind.CLASS, null, 1));

    private static final Pattern CALL = Pattern.compile("(?<![\\w.$])([A-Za-z_][\\w]*)\\s*\\(");
    private static final Pattern MEMBER_CALL = Pattern.compile("[.:>]([A-Za-z_][\\w]*)\\s*\\(");
    private static final Pattern NEW_CALL = Pattern.compile("\\bnew\\s+([A-Z][\\w]*)");
    private static final Pattern COBOL_PERFORM = Pattern.compile("\\bPERFORM\\s+([A-Z0-9][A-Z0-9-]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COBOL_CALL = Pattern.compile("\\bCALL\\s+['\"]([\\w-]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_REF = Pattern.compile("\\b(?:FROM|JOIN|INTO|UPDATE|EXEC(?:UTE)?|CALL)\\s+([\\w.\"`\\[\\]]+)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> KEYWORDS = Set.of("if", "for", "while", "switch", "return", "catch", "function",
        "sizeof", "typeof", "elif", "else", "when", "match", "until", "unless", "foreach", "print", "echo", "and", "or",
        "not", "in", "is", "with", "assert", "await", "yield", "super", "this", "self", "new", "def", "fn", "func", "lambda");

    private static final int SNIPPET_MAX_LINES = 80;

    public void extract(FileModel model, String content) {
        List<Rule> rules = rulesFor(model.language());
        String[] lines = content.split("\n", -1);

        record Hit(int line, Rule rule, String name) {
        }
        int[] lineStarts = lineStarts(content);
        List<Hit> hits = new ArrayList<>();
        for (Rule rule : rules) {
            Matcher m = rule.pattern().matcher(content);
            while (m.find()) {
                String name = m.group(rule.nameGroup()).replaceAll("[\"`\\[\\]]", "");
                if (name.isBlank() || KEYWORDS.contains(name.toLowerCase(Locale.ROOT))) continue;
                if (model.language() == Language.COBOL && isCobolReserved(name)) continue;
                hits.add(new Hit(lineOf(lineStarts, m.start(rule.nameGroup())), rule, name));
            }
        }
        hits.sort((a, b) -> Integer.compare(a.line(), b.line()));

        // Each declaration runs until the next one; classes enclose the functions inside them by indentation.
        int lastClass = -1;
        int lastClassIndent = -1;
        for (int h = 0; h < hits.size(); h++) {
            Hit hit = hits.get(h);
            if (h > 0 && hits.get(h - 1).line() == hit.line()) continue;
            int end = h + 1 < hits.size() ? Math.max(hit.line(), hits.get(h + 1).line() - 1) : lines.length;
            int indent = indentOf(lines[hit.line() - 1]);
            boolean insideClass = lastClass >= 0 && hit.rule().kind() != SymbolKind.CLASS && indent > lastClassIndent;
            SymbolKind kind = insideClass ? SymbolKind.METHOD : hit.rule().kind();
            if (hit.rule().kind() == SymbolKind.CLASS) {
                end = endOfIndentedBlock(lines, hit.line(), indent);
            }
            String signature = lines[hit.line() - 1].trim();
            if (signature.length() > 300) signature = signature.substring(0, 300) + "…";
            int symbolEnd = Math.min(end, lines.length);
            FileModel.Symbol symbol = model.addSymbol(insideClass ? lastClass : -1, null, kind, hit.rule().subkind(),
                lastSegment(hit.name()), hit.line(), symbolEnd, signature,
                snippet(lines, hit.line(), symbolEnd), List.of(), List.of());
            if (hit.rule().kind() == SymbolKind.CLASS) {
                lastClass = symbol.index();
                lastClassIndent = indent;
            } else if (!insideClass) {
                lastClass = -1;
            }
        }

        collectCalls(model, lines);
    }

    private void collectCalls(FileModel model, String[] lines) {
        Language language = model.language();
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i], language);
            if (line.isBlank()) continue;
            int owner = model.ownerAtLine(i + 1);
            if (language == Language.COBOL) {
                addAll(model, owner, COBOL_PERFORM.matcher(line), false, i + 1);
                addAll(model, owner, COBOL_CALL.matcher(line), false, i + 1);
                continue;
            }
            if (language == Language.SQL) {
                addAll(model, owner, SQL_REF.matcher(line), false, i + 1);
            }
            if (owner >= 0 && model.symbols().get(owner).startLine() == i + 1) {
                continue; // the declaration line itself
            }
            addAll(model, owner, CALL.matcher(line), false, i + 1);
            addAll(model, owner, MEMBER_CALL.matcher(line), false, i + 1);
            addAll(model, owner, NEW_CALL.matcher(line), true, i + 1);
        }
    }

    private static void addAll(FileModel model, int owner, Matcher m, boolean instantiation, int lineNumber) {
        while (m.find()) {
            String name = lastSegment(m.group(1).replaceAll("[\"`\\[\\]]", ""));
            if (!KEYWORDS.contains(name.toLowerCase(Locale.ROOT))) {
                model.calls().add(new FileModel.Call(owner, name, instantiation, lineNumber));
            }
        }
    }

    private static List<Rule> rulesFor(Language language) {
        return switch (language) {
            case COBOL -> COBOL;
            case SQL -> SQL;
            case VISUAL_BASIC, PASCAL, FORTRAN, ADA, ABAP -> VB_PASCAL_FORTRAN_ADA;
            case SHELL, BATCH, POWERSHELL -> SHELL;
            case HASKELL -> HASKELL;
            default -> GENERIC;
        };
    }

    private static boolean isCobolReserved(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        return n.equals("EXIT") || n.equals("GOBACK") || n.endsWith("-EXIT") && n.length() < 6
            || n.equals("PROCEDURE") || n.equals("DATA") || n.equals("WORKING-STORAGE") || n.equals("FILE")
            || n.equals("LINKAGE") || n.equals("INPUT-OUTPUT") || n.equals("CONFIGURATION") || n.equals("END-IF")
            || n.equals("STOP") || n.equals("IDENTIFICATION") || n.equals("ENVIRONMENT") || n.equals("DIVISION");
    }

    private static String stripComment(String line, Language language) {
        String trimmed = line.trim();
        if (language == Language.COBOL) {
            return line.length() > 6 && (line.charAt(6) == '*' || line.charAt(6) == '/') ? "" : line;
        }
        if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--") || trimmed.startsWith("*")
            || trimmed.startsWith("'") || trimmed.startsWith("REM ") || trimmed.startsWith("!") || trimmed.startsWith(";")) {
            return "";
        }
        return line;
    }

    private static int[] lineStarts(String content) {
        int count = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') count++;
        }
        int[] starts = new int[count];
        int line = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') starts[line++] = i + 1;
        }
        return starts;
    }

    /** 1-based line number containing the character offset. */
    private static int lineOf(int[] lineStarts, int offset) {
        int idx = java.util.Arrays.binarySearch(lineStarts, offset);
        return (idx >= 0 ? idx : -idx - 2) + 1;
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) n++;
        return n;
    }

    private static int endOfIndentedBlock(String[] lines, int startLine, int indent) {
        for (int i = startLine; i < lines.length; i++) {
            String l = lines[i];
            if (!l.isBlank() && indentOf(l) <= indent && !l.trim().startsWith("}") && !l.trim().startsWith("end")) {
                return i;
            }
        }
        return lines.length;
    }

    private static String snippet(String[] lines, int start, int end) {
        int last = Math.min(end, Math.min(lines.length, start - 1 + SNIPPET_MAX_LINES));
        StringBuilder sb = new StringBuilder();
        for (int i = start - 1; i < last; i++) {
            sb.append(lines[i]).append('\n');
        }
        if (last < end) sb.append("…\n");
        return sb.toString();
    }

    private static String lastSegment(String name) {
        String[] parts = name.split("[.:]+");
        return parts.length == 0 ? name : parts[parts.length - 1];
    }
}
