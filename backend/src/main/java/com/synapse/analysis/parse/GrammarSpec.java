package com.synapse.analysis.parse;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Tells the generic tree-sitter walker which syntax node types declare classes, functions and calls
 * for each grammar. Node type names come from each grammar's node-types.json.
 */
record GrammarSpec(
    Map<String, String> classTypes,      // node type -> subkind label
    Set<String> functionTypes,
    Set<String> callTypes,
    Set<String> newTypes,
    Set<String> implScopeTypes) {        // blocks that attach methods to a type declared elsewhere

    private static final Map<Language, GrammarSpec> SPECS = new EnumMap<>(Language.class);

    static {
        SPECS.put(Language.JAVA, new GrammarSpec(
            Map.of("class_declaration", "class", "interface_declaration", "interface", "enum_declaration", "enum",
                "record_declaration", "record", "annotation_type_declaration", "annotation"),
            Set.of("method_declaration", "constructor_declaration", "compact_constructor_declaration"),
            Set.of("method_invocation"),
            Set.of("object_creation_expression"),
            Set.of()));

        SPECS.put(Language.KOTLIN, new GrammarSpec(
            Map.of("class_declaration", "class", "object_declaration", "object", "companion_object", "object"),
            Set.of("function_declaration", "secondary_constructor"),
            Set.of("call_expression"),
            Set.of(),
            Set.of()));

        SPECS.put(Language.PYTHON, new GrammarSpec(
            Map.of("class_definition", "class"),
            Set.of("function_definition"),
            Set.of("call"),
            Set.of(),
            Set.of()));

        GrammarSpec js = new GrammarSpec(
            Map.of("class_declaration", "class", "class", "class", "abstract_class_declaration", "class",
                "interface_declaration", "interface", "enum_declaration", "enum"),
            Set.of("function_declaration", "generator_function_declaration", "method_definition",
                "variable_declarator", "public_field_definition", "field_definition", "method_signature",
                "abstract_method_signature"),
            Set.of("call_expression"),
            Set.of("new_expression"),
            Set.of());
        SPECS.put(Language.JAVASCRIPT, js);
        SPECS.put(Language.TYPESCRIPT, js);

        SPECS.put(Language.GO, new GrammarSpec(
            Map.of("type_spec", "type"),
            Set.of("function_declaration", "method_declaration"),
            Set.of("call_expression"),
            Set.of("composite_literal"),
            Set.of()));

        SPECS.put(Language.C, new GrammarSpec(
            Map.of("struct_specifier", "struct", "union_specifier", "union", "enum_specifier", "enum"),
            Set.of("function_definition"),
            Set.of("call_expression"),
            Set.of(),
            Set.of()));

        SPECS.put(Language.CPP, new GrammarSpec(
            Map.of("class_specifier", "class", "struct_specifier", "struct", "union_specifier", "union",
                "enum_specifier", "enum"),
            Set.of("function_definition"),
            Set.of("call_expression"),
            Set.of("new_expression"),
            Set.of()));

        SPECS.put(Language.CSHARP, new GrammarSpec(
            Map.of("class_declaration", "class", "interface_declaration", "interface", "struct_declaration", "struct",
                "enum_declaration", "enum", "record_declaration", "record"),
            Set.of("method_declaration", "constructor_declaration", "local_function_statement"),
            Set.of("invocation_expression"),
            Set.of("object_creation_expression"),
            Set.of()));

        SPECS.put(Language.PHP, new GrammarSpec(
            Map.of("class_declaration", "class", "interface_declaration", "interface", "trait_declaration", "trait",
                "enum_declaration", "enum"),
            Set.of("function_definition", "method_declaration"),
            Set.of("function_call_expression", "member_call_expression", "scoped_call_expression"),
            Set.of("object_creation_expression"),
            Set.of()));

        SPECS.put(Language.RUBY, new GrammarSpec(
            Map.of("class", "class", "module", "module"),
            Set.of("method", "singleton_method"),
            Set.of("call"),
            Set.of(),
            Set.of()));

        SPECS.put(Language.RUST, new GrammarSpec(
            Map.of("struct_item", "struct", "enum_item", "enum", "trait_item", "trait", "union_item", "union"),
            Set.of("function_item", "function_signature_item"),
            Set.of("call_expression"),
            Set.of("struct_expression"),
            Set.of("impl_item")));
    }

    static GrammarSpec of(Language language) {
        return SPECS.get(language);
    }
}
