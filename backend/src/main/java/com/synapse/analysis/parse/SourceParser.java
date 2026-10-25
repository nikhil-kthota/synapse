package com.synapse.analysis.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns one source file into a {@link FileModel}: syntax-tree extraction where a grammar exists,
 * pattern extraction otherwise, plus imports and HTTP endpoints. One instance per thread.
 */
public class SourceParser {

    private static final Logger log = LoggerFactory.getLogger(SourceParser.class);
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)", Pattern.MULTILINE);
    private static final Pattern NAMESPACE = Pattern.compile("^\\s*namespace\\s+([\\w.\\\\]+)", Pattern.MULTILINE);
    private static final Pattern SFC_SCRIPT = Pattern.compile("<script([^>]*)>([\\s\\S]*?)</script>");

    private final TreeSitterExtractor treeSitter = new TreeSitterExtractor();
    private final HeuristicExtractor heuristic = new HeuristicExtractor();

    public FileModel parse(String path, Language language, String content) {
        int lineCount = content.isEmpty() ? 0 : content.split("\n", -1).length;
        FileModel model = new FileModel(path, language, lineCount);
        model.imports().addAll(ImportScanner.scan(language, content));
        model.setPackageName(packageOf(language, content));
        if (!language.isCode()) {
            return model;
        }
        try {
            if (language == Language.VUE || language == Language.SVELTE) {
                Matcher m = SFC_SCRIPT.matcher(content);
                while (m.find()) {
                    boolean ts = m.group(1).contains("ts");
                    int offset = lineOfOffset(content, m.start(2)) - 1;
                    FileModel scriptModel = new FileModel(ts ? path + ".ts" : path + ".js", language, lineCount);
                    treeSitter.extract(scriptModel, m.group(2), offset);
                    copySymbols(scriptModel, model);
                }
            } else if (language.hasGrammar()) {
                treeSitter.extract(model, content, 0);
            } else {
                heuristic.extract(model, content);
            }
        } catch (Throwable e) {
            log.debug("Falling back to heuristic extraction for {}: {}", path, e.toString());
            FileModel fallback = new FileModel(path, language, lineCount);
            heuristic.extract(fallback, content);
            copySymbols(fallback, model);
        }
        model.endpoints().addAll(EndpointDetector.detect(language, content));
        return model;
    }

    /** Declared package or namespace, used to resolve package-style imports (Java, Kotlin, C#, PHP, ...). */
    private static String packageOf(Language language, String content) {
        Pattern pattern = switch (language) {
            case JAVA, KOTLIN, SCALA, GROOVY -> PACKAGE;
            case CSHARP, PHP, CPP -> NAMESPACE;
            default -> null;
        };
        if (pattern == null) return null;
        Matcher m = pattern.matcher(content);
        return m.find() ? m.group(1).replace('\\', '.') : null;
    }

    private static void copySymbols(FileModel from, FileModel to) {
        int base = to.symbols().size();
        for (FileModel.Symbol s : from.symbols()) {
            to.addSymbol(s.parentIndex() < 0 ? -1 : s.parentIndex() + base, s.parentName(), s.kind(), s.subkind(), s.name(),
                s.startLine(), s.endLine(), s.signature(), s.snippet(), s.extendsNames(), s.implementsNames());
        }
        for (FileModel.Call c : from.calls()) {
            to.calls().add(new FileModel.Call(c.ownerIndex() < 0 ? -1 : c.ownerIndex() + base, c.callee(), c.instantiation(), c.line()));
        }
        to.relations().addAll(from.relations());
        if (from.parsedWithGrammar()) to.markParsedWithGrammar();
    }

    private static int lineOfOffset(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }
}
