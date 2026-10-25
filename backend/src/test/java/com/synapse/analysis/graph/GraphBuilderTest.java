package com.synapse.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.synapse.analysis.ingest.FileScanner.SourceFile;
import com.synapse.analysis.parse.FileModel;
import com.synapse.analysis.parse.Language;
import com.synapse.analysis.parse.SourceParser;

class GraphBuilderTest {

    private static CodeGraph build(List<SourceFile> files) {
        SourceParser parser = new SourceParser();
        List<FileModel> models = files.stream().map(f -> parser.parse(f.path(), f.language(), f.content())).toList();
        return new GraphBuilder().build("demo", files, models);
    }

    private static Set<String> edges(CodeGraph g, String kind) {
        return g.edges().stream().filter(e -> e.kind().equals(kind))
            .map(e -> g.node(e.source()).name + " -> " + g.node(e.target()).name)
            .collect(Collectors.toSet());
    }

    @Test
    void linksJavaAcrossFilesAndPackages() {
        CodeGraph g = build(List.of(
            new SourceFile("src/main/java/com/shop/model/BaseEntity.java", Language.JAVA,
                "package com.shop.model;\npublic class BaseEntity { public Long getId() { return id; } }\n"),
            new SourceFile("src/main/java/com/shop/model/Order.java", Language.JAVA,
                "package com.shop.model;\npublic class Order extends BaseEntity { public long total() { return 1; } }\n"),
            new SourceFile("src/main/java/com/shop/web/OrderController.java", Language.JAVA, """
                package com.shop.web;
                import com.shop.model.Order;
                import org.springframework.web.bind.annotation.*;
                @RestController
                @RequestMapping("/orders")
                public class OrderController {
                    @GetMapping("/{id}")
                    public long show(Order order) { return order.total() + order.getId(); }
                    @PostMapping
                    public Order create() { return new Order(); }
                }
                """),
            new SourceFile("pom.xml", Language.XML, """
                <project><dependencies><dependency><groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-web</artifactId></dependency></dependencies></project>
                """)));

        assertThat(edges(g, CodeGraph.IMPORTS)).contains("OrderController.java -> Order.java", "OrderController.java -> org.springframework.web");
        assertThat(edges(g, CodeGraph.EXTENDS)).containsExactly("Order -> BaseEntity");
        assertThat(edges(g, CodeGraph.CALLS)).contains("show -> total", "show -> getId");
        assertThat(edges(g, CodeGraph.INSTANTIATES)).contains("create -> Order");
        assertThat(edges(g, CodeGraph.HANDLES)).containsExactlyInAnyOrder("GET /orders/{id} -> show", "POST /orders -> create");
        assertThat(edges(g, CodeGraph.DEPENDS_ON)).contains("pom.xml -> org.springframework.boot");

        // Call sites keep the line they occur on (line 8 of OrderController.java).
        CodeGraph.Edge showToTotal = g.edges().stream()
            .filter(e -> e.kind().equals(CodeGraph.CALLS) && g.node(e.source()).name.equals("show") && g.node(e.target()).name.equals("total"))
            .findFirst().orElseThrow();
        assertThat(showToTotal.lines()).containsExactly(8);
    }

    @Test
    void resolvesRelativeJsAndPythonImports() {
        CodeGraph g = build(List.of(
            new SourceFile("web/src/api/client.js", Language.JAVASCRIPT, "export function get(url) { return fetch(url); }\n"),
            new SourceFile("web/src/pages/Home.jsx", Language.JAVASCRIPT,
                "import { get } from '../api/client';\nimport React from 'react';\nexport default function Home() { return get('/x'); }\n"),
            new SourceFile("app/models.py", Language.PYTHON, "class User:\n    def save(self):\n        pass\n"),
            new SourceFile("app/views.py", Language.PYTHON,
                "from .models import User\nimport numpy as np\ndef create():\n    u = User()\n    u.save()\n")));

        assertThat(edges(g, CodeGraph.IMPORTS)).contains(
            "Home.jsx -> client.js", "Home.jsx -> react", "views.py -> models.py", "views.py -> numpy");
        // `get` is a common name, but the caller imports client.js so the call is safe to link.
        assertThat(edges(g, CodeGraph.CALLS)).contains("Home -> get", "create -> save");
        assertThat(edges(g, CodeGraph.INSTANTIATES)).contains("create -> User");
    }

    @Test
    void sameNamedTestHelperStillLinksToTheRealMethod() {
        CodeGraph g = build(List.of(
            new SourceFile("src/main/java/org/acme/owner/PetValidator.java", Language.JAVA, """
                package org.acme.owner;
                public class PetValidator {
                    public void validate(Object target, Object errors) { }
                }
                """),
            new SourceFile("src/test/java/org/acme/owner/PetValidatorTests.java", Language.JAVA, """
                package org.acme.owner;
                class PetValidatorTests {
                    private PetValidator petValidator = new PetValidator();
                    private Object validate(Object pet) {
                        Object errors = new Object();
                        petValidator.validate(pet, errors);
                        return errors;
                    }
                    void rejectsBlankName() { validate(null); }
                }
                """)));
        Set<String> calls = g.edges().stream().filter(e -> e.kind().equals(CodeGraph.CALLS))
            .map(e -> g.node(e.source()).name + " -> " + g.node(e.target()).filePath.replaceAll(".*/", "") + "#" + g.node(e.target()).name + " @" + e.lines())
            .collect(Collectors.toSet());
        assertThat(calls).contains(
            "validate -> PetValidator.java#validate @[6]",
            "rejectsBlankName -> PetValidatorTests.java#validate @[9]");
    }

    @Test
    void ignoresAmbiguousCommonNamesWithoutImports() {
        CodeGraph g = build(List.of(
            new SourceFile("a/cache.py", Language.PYTHON, "def get(key):\n    return key\n"),
            new SourceFile("b/http.py", Language.PYTHON, "import requests\ndef fetch():\n    return requests.get('x')\n")));
        assertThat(edges(g, CodeGraph.CALLS)).doesNotContain("fetch -> get");
    }
}
