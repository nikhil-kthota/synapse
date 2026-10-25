package com.synapse.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.synapse.analysis.graph.CodeGraph;
import com.synapse.analysis.graph.GraphBuilder;
import com.synapse.analysis.ingest.FileScanner.SourceFile;
import com.synapse.analysis.parse.FileModel;
import com.synapse.analysis.parse.Language;
import com.synapse.analysis.parse.SourceParser;

class ModuleDetectionTest {

    private static DocumentationService.Facts facts(List<SourceFile> files) {
        SourceParser parser = new SourceParser();
        List<FileModel> models = files.stream().map(f -> parser.parse(f.path(), f.language(), f.content())).toList();
        CodeGraph graph = new GraphBuilder().build("demo", files, models);
        return new DocumentationService.Facts(graph);
    }

    @Test
    void mavenPackagesBecomeModulesEvenWithResourcesBesideThem() {
        String pkg = "src/main/java/org/acme/shop/";
        DocumentationService.Facts facts = facts(List.of(
            new SourceFile(pkg + "owner/Owner.java", Language.JAVA,
                "package org.acme.shop.owner;\nimport org.acme.shop.model.Person;\npublic class Owner extends Person {}\n"),
            new SourceFile(pkg + "owner/OwnerController.java", Language.JAVA,
                "package org.acme.shop.owner;\npublic class OwnerController { Owner o; }\n"),
            new SourceFile(pkg + "model/Person.java", Language.JAVA, "package org.acme.shop.model;\npublic class Person {}\n"),
            new SourceFile(pkg + "vet/Vet.java", Language.JAVA,
                "package org.acme.shop.vet;\nimport org.acme.shop.model.Person;\npublic class Vet extends Person {}\n"),
            new SourceFile("src/test/java/org/acme/shop/owner/OwnerTests.java", Language.JAVA,
                "package org.acme.shop.owner;\nclass OwnerTests {}\n"),
            new SourceFile("src/main/resources/db/h2/schema.sql", Language.SQL, "CREATE TABLE owners (id INT);\n")));

        assertThat(facts.moduleList(10)).contains("owner: 3", "model: 1", "vet: 1", "db: 1");
        assertThat(facts.moduleDependencies(10)).anyMatch(d -> d.startsWith("owner -> model"));
        assertThat(facts.moduleDiagram()).contains("```mermaid", "owner", "model");
    }

    @Test
    void frontendFoldersUnderSrcBecomeModules() {
        DocumentationService.Facts facts = facts(List.of(
            new SourceFile("src/pages/Home.jsx", Language.JAVASCRIPT,
                "import Button from '../components/Button';\nexport default function Home() { return Button(); }\n"),
            new SourceFile("src/components/Button.jsx", Language.JAVASCRIPT, "export default function Button() { return 1; }\n"),
            new SourceFile("src/main.jsx", Language.JAVASCRIPT, "import Home from './pages/Home';\n")));
        assertThat(facts.moduleList(10)).contains("pages: 1", "components: 1", "src: 1");
    }
}
