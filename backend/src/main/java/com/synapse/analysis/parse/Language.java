package com.synapse.analysis.parse;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.treesitter.TSLanguage;
import org.treesitter.TreeSitterC;
import org.treesitter.TreeSitterCSharp;
import org.treesitter.TreeSitterCpp;
import org.treesitter.TreeSitterGo;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterJavascript;
import org.treesitter.TreeSitterKotlin;
import org.treesitter.TreeSitterPhp;
import org.treesitter.TreeSitterPython;
import org.treesitter.TreeSitterRuby;
import org.treesitter.TreeSitterRust;
import org.treesitter.TreeSitterTypescript;

/**
 * Every file type Synapse recognises. Languages with a tree-sitter grammar get full syntax-tree
 * extraction; other code languages fall back to {@link HeuristicExtractor}; config and docs
 * become plain file nodes.
 */
public enum Language {

    JAVA("Java", Category.CODE, TreeSitterJava::new, "java"),
    KOTLIN("Kotlin", Category.CODE, TreeSitterKotlin::new, "kt", "kts"),
    PYTHON("Python", Category.CODE, TreeSitterPython::new, "py", "pyw"),
    JAVASCRIPT("JavaScript", Category.CODE, TreeSitterJavascript::new, "js", "jsx", "mjs", "cjs"),
    TYPESCRIPT("TypeScript", Category.CODE, TreeSitterTypescript::new, "ts", "tsx", "mts", "cts"),
    GO("Go", Category.CODE, TreeSitterGo::new, "go"),
    C("C", Category.CODE, TreeSitterC::new, "c", "h"),
    CPP("C++", Category.CODE, TreeSitterCpp::new, "cpp", "cc", "cxx", "hpp", "hh", "hxx", "ino"),
    CSHARP("C#", Category.CODE, TreeSitterCSharp::new, "cs"),
    PHP("PHP", Category.CODE, TreeSitterPhp::new, "php", "phtml"),
    RUBY("Ruby", Category.CODE, TreeSitterRuby::new, "rb", "rake", "gemspec"),
    RUST("Rust", Category.CODE, TreeSitterRust::new, "rs"),

    // Script blocks inside single-file components are parsed with the JS/TS grammar.
    VUE("Vue", Category.CODE, null, "vue"),
    SVELTE("Svelte", Category.CODE, null, "svelte"),

    SCALA("Scala", Category.CODE, null, "scala", "sc"),
    SWIFT("Swift", Category.CODE, null, "swift"),
    DART("Dart", Category.CODE, null, "dart"),
    GROOVY("Groovy", Category.CODE, null, "groovy", "gvy", "gradle"),
    OBJECTIVE_C("Objective-C", Category.CODE, null, "m", "mm"),
    COBOL("COBOL", Category.CODE, null, "cbl", "cob", "cpy", "cobol"),
    FORTRAN("Fortran", Category.CODE, null, "f", "f77", "f90", "f95", "for"),
    PASCAL("Pascal", Category.CODE, null, "pas", "pp", "dpr"),
    VISUAL_BASIC("Visual Basic", Category.CODE, null, "vb", "bas", "cls", "frm", "vbs"),
    PERL("Perl", Category.CODE, null, "pl", "pm"),
    LUA("Lua", Category.CODE, null, "lua"),
    R("R", Category.CODE, null, "r"),
    JULIA("Julia", Category.CODE, null, "jl"),
    ELIXIR("Elixir", Category.CODE, null, "ex", "exs"),
    ERLANG("Erlang", Category.CODE, null, "erl", "hrl"),
    HASKELL("Haskell", Category.CODE, null, "hs"),
    FSHARP("F#", Category.CODE, null, "fs", "fsx"),
    CLOJURE("Clojure", Category.CODE, null, "clj", "cljs", "cljc"),
    ADA("Ada", Category.CODE, null, "adb", "ads"),
    ABAP("ABAP", Category.CODE, null, "abap"),
    ASSEMBLY("Assembly", Category.CODE, null, "asm", "s"),
    SQL("SQL", Category.CODE, null, "sql", "pls", "pks", "pkb", "prc"),
    SHELL("Shell", Category.CODE, null, "sh", "bash", "zsh", "ksh"),
    POWERSHELL("PowerShell", Category.CODE, null, "ps1", "psm1"),
    BATCH("Batch", Category.CODE, null, "bat", "cmd"),
    JCL("JCL", Category.CODE, null, "jcl"),

    HTML("HTML", Category.MARKUP, null, "html", "htm", "xhtml", "jsp", "asp", "aspx", "cshtml", "erb", "hbs", "ejs"),
    CSS("CSS", Category.MARKUP, null, "css", "scss", "sass", "less"),

    JSON("JSON", Category.CONFIG, null, "json"),
    YAML("YAML", Category.CONFIG, null, "yml", "yaml"),
    XML("XML", Category.CONFIG, null, "xml", "xsd", "wsdl"),
    TOML("TOML", Category.CONFIG, null, "toml"),
    PROPERTIES("Properties", Category.CONFIG, null, "properties", "ini", "cfg", "conf"),
    DOCKER("Docker", Category.CONFIG, null),
    MAKE("Make", Category.CONFIG, null, "mk"),

    MARKDOWN("Markdown", Category.DOCS, null, "md", "markdown", "rst", "adoc", "txt");

    public enum Category { CODE, MARKUP, CONFIG, DOCS }

    private static final Map<String, Language> BY_EXTENSION = new HashMap<>();

    static {
        for (Language language : values()) {
            for (String ext : language.extensions) {
                BY_EXTENSION.putIfAbsent(ext, language);
            }
        }
    }

    private static final Set<String> SPECIAL_NAMES_DOCKER = Set.of("dockerfile", "containerfile");
    private static final Set<String> SPECIAL_NAMES_MAKE = Set.of("makefile", "gnumakefile");

    private final String displayName;
    private final Category category;
    private final Supplier<TSLanguage> grammar;
    private final String[] extensions;

    Language(String displayName, Category category, Supplier<TSLanguage> grammar, String... extensions) {
        this.displayName = displayName;
        this.category = category;
        this.grammar = grammar;
        this.extensions = extensions;
    }

    public String displayName() { return displayName; }
    public Category category() { return category; }
    public boolean isCode() { return category == Category.CODE; }
    public boolean hasGrammar() { return grammar != null; }
    public TSLanguage newGrammar() { return grammar.get(); }

    /** Detects the language from a file name, or returns null for unsupported files. */
    public static Language detect(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (SPECIAL_NAMES_DOCKER.contains(lower) || lower.startsWith("dockerfile.")) return DOCKER;
        if (SPECIAL_NAMES_MAKE.contains(lower)) return MAKE;
        if (lower.equals("gemfile") || lower.equals("rakefile")) return RUBY;
        if (lower.equals("jenkinsfile")) return GROOVY;
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) return null;
        return BY_EXTENSION.get(lower.substring(dot + 1));
    }
}
