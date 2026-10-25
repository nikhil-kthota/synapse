package com.synapse.analysis.parse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;

import com.synapse.analysis.parse.FileModel.SymbolKind;

/**
 * Walks a tree-sitter syntax tree and records declarations and calls using the node types in
 * {@link GrammarSpec}. Not thread-safe: create one per analysis thread.
 */
public class TreeSitterExtractor {

    private static final int MAX_NODES = 400_000;
    private static final int SNIPPET_MAX_LINES = 80;
    private static final int SNIPPET_MAX_CHARS = 6_000;

    private static final Set<String> IDENTIFIER_TYPES = Set.of(
        "identifier", "type_identifier", "field_identifier", "property_identifier", "simple_identifier",
        "constant", "name", "private_property_identifier", "shorthand_property_identifier", "scope_resolution",
        "qualified_identifier", "destructor_name", "operator_name", "template_function", "template_type",
        "primitive_type", "generic_type", "scoped_type_identifier", "scoped_identifier");

    private static final Set<String> FUNCTION_VALUE_TYPES = Set.of(
        "arrow_function", "function_expression", "function", "generator_function");

    private final Map<Language, TSParser> parsers = new EnumMap<>(Language.class);

    public void extract(FileModel model, String content, int lineOffset) {
        Language grammarLanguage = grammarFor(model.language(), model.path());
        GrammarSpec spec = GrammarSpec.of(grammarLanguage);
        if (spec == null) {
            throw new IllegalArgumentException("No grammar for " + model.language());
        }
        TSParser parser = parsers.computeIfAbsent(grammarLanguage, lang -> {
            TSParser p = new TSParser();
            p.setLanguage(lang.newGrammar());
            p.setTimeoutMicros(5_000_000);
            return p;
        });
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        TSTree tree = parser.parseString(null, content);
        if (tree == null) {
            throw new IllegalStateException("Parser timed out");
        }
        new Walk(model, spec, bytes, lineOffset).run(tree.getRootNode());
        model.markParsedWithGrammar();
    }

    /** Vue and Svelte script blocks use the JS or TS grammar. */
    private static Language grammarFor(Language language, String path) {
        if (language == Language.VUE || language == Language.SVELTE) {
            return path.endsWith(".ts") ? Language.TYPESCRIPT : Language.JAVASCRIPT;
        }
        return language;
    }

    private record Frame(TSNode node, int owner, int classIndex, String implName) {
    }

    private static final class Walk {
        private final FileModel model;
        private final GrammarSpec spec;
        private final byte[] bytes;
        private final int lineOffset;

        Walk(FileModel model, GrammarSpec spec, byte[] bytes, int lineOffset) {
            this.model = model;
            this.spec = spec;
            this.bytes = bytes;
            this.lineOffset = lineOffset;
        }

        void run(TSNode root) {
            Deque<Frame> stack = new ArrayDeque<>();
            stack.push(new Frame(root, -1, -1, null));
            int visited = 0;
            while (!stack.isEmpty() && visited++ < MAX_NODES) {
                Frame frame = stack.pop();
                TSNode node = frame.node();
                String type = node.getType();
                int owner = frame.owner();
                int classIndex = frame.classIndex();
                String implName = frame.implName();

                if (spec.classTypes().containsKey(type) && isClassDeclaration(node, type)) {
                    String name = nameOf(node);
                    if (name != null) {
                        String header = header(node);
                        List<List<String>> supers = Supertypes.parse(model.language(), header, name);
                        int idx = model.addSymbol(owner, null, SymbolKind.CLASS, classSubkind(node, type), name,
                            line(node.getStartPoint().getRow()), line(node.getEndPoint().getRow()),
                            signature(header), snippet(node), supers.get(0), supers.get(1)).index();
                        owner = idx;
                        classIndex = idx;
                        implName = null;
                    }
                } else if (spec.implScopeTypes().contains(type)) {
                    implName = rustImpl(node);
                    classIndex = -1;
                } else if (spec.functionTypes().contains(type) && isFunctionDeclaration(node, type)) {
                    String name = nameOf(node);
                    boolean nestedInFunction = owner >= 0 && model.symbols().get(owner).kind() != SymbolKind.CLASS;
                    if (name != null && !nestedInFunction) {
                        boolean member = (classIndex >= 0 && owner == classIndex) || implName != null
                            || type.equals("method_declaration") && model.language() == Language.GO;
                        String parentName = implName;
                        if (model.language() == Language.GO && type.equals("method_declaration")) {
                            parentName = goReceiver(node);
                        }
                        String header = header(node);
                        int idx = model.addSymbol(owner, parentName, member ? SymbolKind.METHOD : SymbolKind.FUNCTION,
                            functionSubkind(type, name, classIndex), name,
                            line(node.getStartPoint().getRow()), line(node.getEndPoint().getRow()),
                            signature(header), snippet(node), List.of(), List.of()).index();
                        owner = idx;
                    }
                } else if (spec.callTypes().contains(type)) {
                    String callee = calleeOf(node, false);
                    if (callee != null) {
                        model.calls().add(new FileModel.Call(owner, callee, false, line(node.getStartPoint().getRow())));
                    }
                } else if (spec.newTypes().contains(type)) {
                    String callee = calleeOf(node, true);
                    if (callee != null) {
                        model.calls().add(new FileModel.Call(owner, callee, true, line(node.getStartPoint().getRow())));
                    }
                }

                int count = node.getNamedChildCount();
                for (int i = count - 1; i >= 0; i--) {
                    TSNode child = node.getNamedChild(i);
                    if (!child.isNull()) {
                        stack.push(new Frame(child, owner, classIndex, implName));
                    }
                }
            }
        }

        private boolean isClassDeclaration(TSNode node, String type) {
            Language lang = model.language();
            if (lang == Language.C || lang == Language.CPP) {
                // `struct foo x;` is a use, not a declaration.
                return !field(node, "body").isNull();
            }
            if (lang == Language.GO) {
                String t = field(node, "type").isNull() ? "" : field(node, "type").getType();
                return t.equals("struct_type") || t.equals("interface_type");
            }
            return true;
        }

        private String classSubkind(TSNode node, String type) {
            if (model.language() == Language.GO) {
                return field(node, "type").getType().equals("interface_type") ? "interface" : "struct";
            }
            if (model.language() == Language.KOTLIN && type.equals("class_declaration")) {
                String head = text(node);
                int brace = head.indexOf('{');
                head = brace > 0 ? head.substring(0, brace) : head;
                if (head.matches("(?s).*\\binterface\\b.*")) return "interface";
                if (head.matches("(?s).*\\benum\\s+class\\b.*")) return "enum";
            }
            return spec.classTypes().get(type);
        }

        private boolean isFunctionDeclaration(TSNode node, String type) {
            if (type.equals("variable_declarator") || type.endsWith("field_definition")) {
                TSNode value = field(node, "value");
                return !value.isNull() && FUNCTION_VALUE_TYPES.contains(value.getType());
            }
            return true;
        }

        private String functionSubkind(String type, String name, int classIndex) {
            if (type.contains("constructor") || (classIndex >= 0 && name.equals(model.symbols().get(classIndex).name()))
                || name.equals("__init__") || name.equals("constructor") || name.equals("initialize")) {
                return "constructor";
            }
            return type.contains("signature") ? "abstract" : null;
        }

        private String nameOf(TSNode node) {
            TSNode name = field(node, "name");
            if (!name.isNull()) {
                return clean(text(name));
            }
            TSNode declarator = field(node, "declarator");
            for (int depth = 0; declarator != null && !declarator.isNull() && depth < 8; depth++) {
                if (IDENTIFIER_TYPES.contains(declarator.getType())) {
                    return clean(text(declarator));
                }
                TSNode next = field(declarator, "declarator");
                if (next.isNull()) {
                    next = firstNamed(declarator, IDENTIFIER_TYPES);
                }
                declarator = next;
            }
            TSNode ident = firstNamed(node, Set.of("type_identifier", "simple_identifier", "identifier", "constant", "name"));
            return ident == null ? null : clean(text(ident));
        }

        private String calleeOf(TSNode node, boolean instantiation) {
            TSNode target = null;
            for (String f : instantiation
                ? new String[] {"type", "constructor", "name", "class"}
                : new String[] {"function", "method", "name"}) {
                TSNode candidate = field(node, f);
                if (!candidate.isNull()) {
                    target = candidate;
                    break;
                }
            }
            if (target == null) {
                target = node.getNamedChildCount() > 0 ? node.getNamedChild(0) : null;
            }
            for (int depth = 0; target != null && !target.isNull() && depth < 6; depth++) {
                String t = target.getType();
                if (IDENTIFIER_TYPES.contains(t) || t.endsWith("identifier")) {
                    return clean(text(target));
                }
                TSNode next = null;
                for (String f : new String[] {"property", "attribute", "field", "name", "type"}) {
                    TSNode candidate = field(target, f);
                    if (!candidate.isNull()) {
                        next = candidate;
                        break;
                    }
                }
                if (next == null && target.getNamedChildCount() > 0) {
                    next = target.getNamedChild(target.getNamedChildCount() - 1);
                }
                target = next;
            }
            return null;
        }

        private String rustImpl(TSNode node) {
            TSNode type = field(node, "type");
            TSNode trait = field(node, "trait");
            String typeName = type.isNull() ? null : clean(text(type));
            if (typeName != null && !trait.isNull()) {
                String traitName = clean(text(trait));
                if (traitName != null) {
                    model.relations().add(new FileModel.Relation(typeName, traitName, "IMPLEMENTS"));
                }
            }
            return typeName;
        }

        private String goReceiver(TSNode node) {
            TSNode receiver = field(node, "receiver");
            if (receiver.isNull()) return null;
            String text = text(receiver).replaceAll("[()*\\[\\]]", " ").trim();
            String[] parts = text.split("\\s+");
            return parts.length == 0 ? null : clean(parts[parts.length - 1]);
        }

        private String header(TSNode node) {
            TSNode body = field(node, "body");
            int start = node.getStartByte();
            int end = body.isNull() ? node.getEndByte() : body.getStartByte();
            String text = slice(start, end);
            if (body.isNull()) {
                int newline = text.indexOf('\n');
                if (newline > 0) text = text.substring(0, newline);
            }
            return text;
        }

        private String signature(String header) {
            String s = header.replaceAll("\\s+", " ").trim();
            s = s.replaceAll("[{:]\\s*$", "").trim();
            return s.length() > 300 ? s.substring(0, 300) + "…" : s;
        }

        private String snippet(TSNode node) {
            String text = text(node);
            String[] lines = text.split("\n", SNIPPET_MAX_LINES + 1);
            if (lines.length > SNIPPET_MAX_LINES) {
                text = String.join("\n", java.util.Arrays.copyOf(lines, SNIPPET_MAX_LINES)) + "\n…";
            }
            return text.length() > SNIPPET_MAX_CHARS ? text.substring(0, SNIPPET_MAX_CHARS) + "\n…" : text;
        }

        private int line(int row) {
            return row + 1 + lineOffset;
        }

        private TSNode field(TSNode node, String name) {
            return node.getChildByFieldName(name);
        }

        private TSNode firstNamed(TSNode node, Set<String> types) {
            int count = node.getNamedChildCount();
            for (int i = 0; i < count; i++) {
                TSNode child = node.getNamedChild(i);
                if (types.contains(child.getType())) {
                    return child;
                }
            }
            return null;
        }

        private String text(TSNode node) {
            return slice(node.getStartByte(), node.getEndByte());
        }

        private String slice(int start, int end) {
            start = Math.max(0, Math.min(start, bytes.length));
            end = Math.max(start, Math.min(end, bytes.length));
            return new String(bytes, start, end - start, StandardCharsets.UTF_8);
        }
    }

    /** Reduces `a.b::C<T>` style references to the bare identifier `C`. */
    static String clean(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("<[^<>]*>", "").replaceAll("\\s+", "");
        int cut = Math.max(Math.max(s.lastIndexOf('.'), s.lastIndexOf(':')), Math.max(s.lastIndexOf('>'), s.lastIndexOf('\\')));
        if (cut >= 0) s = s.substring(cut + 1);
        s = s.replaceAll("[^\\w$!?]", "");
        if (s.isEmpty() || s.length() > 120 || Character.isDigit(s.charAt(0))) return null;
        return s;
    }
}
