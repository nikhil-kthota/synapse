package com.synapse.analysis.parse;

import java.util.ArrayList;
import java.util.List;

/** Everything extracted from one source file, before it is linked into the project graph. */
public final class FileModel {

    public enum SymbolKind { CLASS, FUNCTION, METHOD }

    /**
     * A declared class-like or function-like symbol.
     *
     * @param parentIndex index of the enclosing symbol in this file, or -1 for top level
     * @param parentName  enclosing type name when it is declared elsewhere (Go receivers, Rust impls)
     */
    public record Symbol(int index, int parentIndex, String parentName, SymbolKind kind, String subkind,
                         String name, int startLine, int endLine, String signature, String snippet,
                         List<String> extendsNames, List<String> implementsNames) {
    }

    /** A call or instantiation made from inside a symbol (ownerIndex -1 means file level) on a 1-based line. */
    public record Call(int ownerIndex, String callee, boolean instantiation, int line) {
    }

    /** A type relationship declared outside the class itself, e.g. Rust {@code impl Trait for Type}. */
    public record Relation(String fromName, String toName, String kind) {
    }

    public record Endpoint(String method, String path, int line) {
    }

    private final String path;
    private final Language language;
    private final int lineCount;
    private final List<Symbol> symbols = new ArrayList<>();
    private final List<String> imports = new ArrayList<>();
    private final List<Call> calls = new ArrayList<>();
    private final List<Relation> relations = new ArrayList<>();
    private final List<Endpoint> endpoints = new ArrayList<>();
    private boolean parsedWithGrammar;
    private String packageName;

    public FileModel(String path, Language language, int lineCount) {
        this.path = path;
        this.language = language;
        this.lineCount = lineCount;
    }

    public Symbol addSymbol(int parentIndex, String parentName, SymbolKind kind, String subkind, String name,
                            int startLine, int endLine, String signature, String snippet,
                            List<String> extendsNames, List<String> implementsNames) {
        Symbol symbol = new Symbol(symbols.size(), parentIndex, parentName, kind, subkind, name, startLine, endLine,
            signature, snippet, List.copyOf(extendsNames), List.copyOf(implementsNames));
        symbols.add(symbol);
        return symbol;
    }

    /** Innermost function-like symbol whose line range contains the given line, or -1. */
    public int ownerAtLine(int line) {
        int best = -1;
        int bestSpan = Integer.MAX_VALUE;
        for (Symbol s : symbols) {
            if (s.kind() != SymbolKind.CLASS && s.startLine() <= line && line <= s.endLine()) {
                int span = s.endLine() - s.startLine();
                if (span < bestSpan) {
                    best = s.index();
                    bestSpan = span;
                }
            }
        }
        return best;
    }

    public String path() { return path; }
    public Language language() { return language; }
    public int lineCount() { return lineCount; }
    public List<Symbol> symbols() { return symbols; }
    public List<String> imports() { return imports; }
    public List<Call> calls() { return calls; }
    public List<Relation> relations() { return relations; }
    public List<Endpoint> endpoints() { return endpoints; }
    public boolean parsedWithGrammar() { return parsedWithGrammar; }
    public String packageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public void markParsedWithGrammar() { this.parsedWithGrammar = true; }
}
