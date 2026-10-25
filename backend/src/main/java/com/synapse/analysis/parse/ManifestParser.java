package com.synapse.analysis.parse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads declared third-party dependencies from package manifests (npm, Maven, Gradle, pip, Go, Cargo, ...). */
public final class ManifestParser {

    public record Dependency(String name, String ecosystem) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern MAVEN = Pattern.compile("<dependency>\\s*<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>", Pattern.DOTALL);
    private static final Pattern GRADLE = Pattern.compile("(?:implementation|api|compile|compileOnly|runtimeOnly|testImplementation|kapt|annotationProcessor)\\s*\\(?\\s*['\"]([\\w.-]+):([\\w.-]+)");
    private static final Pattern REQUIREMENT = Pattern.compile("^\\s*([A-Za-z0-9][A-Za-z0-9._-]*)", Pattern.MULTILINE);
    private static final Pattern GO_REQUIRE = Pattern.compile("^\\s*(?:require\\s+)?([\\w.-]+\\.[\\w.-]+/[\\w./-]+)\\s+v", Pattern.MULTILINE);
    private static final Pattern TOML_SECTION_DEP = Pattern.compile("^\\s*([A-Za-z0-9_-]+)\\s*=", Pattern.MULTILINE);
    private static final Pattern GEM = Pattern.compile("^\\s*gem\\s+['\"]([^'\"]+)['\"]", Pattern.MULTILINE);
    private static final Pattern CSPROJ = Pattern.compile("<PackageReference\\s+Include=\"([^\"]+)\"");
    private static final Pattern PYPROJECT_DEP = Pattern.compile("['\"]([A-Za-z0-9][A-Za-z0-9._-]*)\\s*(?:[<>=!~;\\[].*?)?['\"]");

    private ManifestParser() {
    }

    public static boolean isManifest(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.equals("package.json") || n.equals("pom.xml") || n.equals("build.gradle") || n.equals("build.gradle.kts")
            || n.equals("requirements.txt") || n.equals("go.mod") || n.equals("cargo.toml") || n.equals("composer.json")
            || n.equals("gemfile") || n.equals("pyproject.toml") || n.endsWith(".csproj") || n.equals("pipfile");
    }

    public static List<Dependency> parse(String fileName, String content) {
        String n = fileName.toLowerCase(Locale.ROOT);
        Set<Dependency> deps = new LinkedHashSet<>();
        try {
            if (n.equals("package.json") || n.equals("composer.json")) {
                JsonNode root = JSON.readTree(content);
                String eco = n.equals("package.json") ? "npm" : "composer";
                for (String section : List.of("dependencies", "devDependencies", "peerDependencies", "require", "require-dev")) {
                    JsonNode node = root.path(section);
                    for (var entry : node.properties()) {
                        String name = entry.getKey();
                        if (!name.equals("php") && !name.startsWith("ext-")) deps.add(new Dependency(name, eco));
                    }
                }
            } else if (n.equals("pom.xml")) {
                Matcher m = MAVEN.matcher(content);
                while (m.find()) deps.add(new Dependency(m.group(1).trim() + ":" + m.group(2).trim(), "maven"));
            } else if (n.startsWith("build.gradle")) {
                Matcher m = GRADLE.matcher(content);
                while (m.find()) deps.add(new Dependency(m.group(1) + ":" + m.group(2), "maven"));
            } else if (n.equals("requirements.txt")) {
                Matcher m = REQUIREMENT.matcher(content);
                while (m.find()) deps.add(new Dependency(m.group(1).toLowerCase(Locale.ROOT), "pypi"));
            } else if (n.equals("go.mod")) {
                Matcher m = GO_REQUIRE.matcher(content);
                while (m.find()) deps.add(new Dependency(m.group(1), "go"));
            } else if (n.equals("cargo.toml") || n.equals("pipfile")) {
                String eco = n.equals("cargo.toml") ? "cargo" : "pypi";
                for (String block : content.split("(?m)^\\[")) {
                    if (block.startsWith("dependencies]") || block.startsWith("dev-dependencies]") || block.startsWith("packages]")
                        || block.startsWith("dev-packages]")) {
                        Matcher m = TOML_SECTION_DEP.matcher(block.substring(block.indexOf(']') + 1));
                        while (m.find()) deps.add(new Dependency(m.group(1), eco));
                    }
                }
            } else if (n.equals("pyproject.toml")) {
                Matcher section = Pattern.compile("dependencies\\s*=\\s*\\[([^\\]]*)\\]", Pattern.DOTALL).matcher(content);
                while (section.find()) {
                    Matcher m = PYPROJECT_DEP.matcher(section.group(1));
                    while (m.find()) deps.add(new Dependency(m.group(1).toLowerCase(Locale.ROOT), "pypi"));
                }
            } else if (n.equals("gemfile")) {
                Matcher m = GEM.matcher(content);
                while (m.find()) deps.add(new Dependency(m.group(1), "rubygems"));
            } else if (n.endsWith(".csproj")) {
                Matcher m = CSPROJ.matcher(content);
                while (m.find()) deps.add(new Dependency(m.group(1), "nuget"));
            }
        } catch (Exception e) {
            // A malformed manifest should not stop the analysis.
        }
        return List.copyOf(deps);
    }
}
