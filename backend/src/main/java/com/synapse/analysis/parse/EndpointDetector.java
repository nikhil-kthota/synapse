package com.synapse.analysis.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects HTTP API endpoints declared with the common web frameworks: Spring, JAX-RS, ASP.NET,
 * Express/Koa/Fastify/NestJS, Flask, FastAPI, Django, Gin/Echo/net/http, Laravel and Rails.
 */
final class EndpointDetector {

    private static final int M = Pattern.MULTILINE;

    private static final Pattern SPRING = Pattern.compile("@(Get|Post|Put|Delete|Patch|Request)Mapping\\s*(?:\\(([^)]*)\\))?");
    private static final Pattern JAXRS_PATH = Pattern.compile("@Path\\s*\\(\\s*\"([^\"]*)\"");
    private static final Pattern JAXRS_METHOD = Pattern.compile("^\\s*@(GET|POST|PUT|DELETE|PATCH)\\b", M);
    private static final Pattern ASPNET = Pattern.compile("\\[Http(Get|Post|Put|Delete|Patch)(?:\\(\\s*\"([^\"]*)\"\\s*\\))?\\]");
    private static final Pattern ASPNET_ROUTE = Pattern.compile("\\[Route\\(\\s*\"([^\"]*)\"\\s*\\)\\]");
    private static final Pattern NEST = Pattern.compile("@(Get|Post|Put|Delete|Patch|All)\\(\\s*(?:['\"`]([^'\"`]*)['\"`])?\\s*\\)");
    private static final Pattern NEST_CONTROLLER = Pattern.compile("@Controller\\(\\s*['\"`]([^'\"`]*)['\"`]");
    private static final Pattern EXPRESS = Pattern.compile("\\b(?:app|router|server|api|routes?|fastify)\\.(get|post|put|delete|patch|all)\\s*\\(\\s*['\"`]([^'\"`]+)['\"`]");
    private static final Pattern FLASK = Pattern.compile("@\\w+\\.route\\(\\s*['\"]([^'\"]+)['\"]([^)]*)\\)");
    private static final Pattern FASTAPI = Pattern.compile("@\\w+\\.(get|post|put|delete|patch)\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern DJANGO = Pattern.compile("\\b(?:re_)?path\\(\\s*r?['\"]([^'\"]*)['\"]\\s*,\\s*([\\w.]+)");
    private static final Pattern GO_HANDLE = Pattern.compile("\\.(?:HandleFunc|Handle)\\(\\s*\"([^\"]+)\"");
    private static final Pattern GO_ROUTER = Pattern.compile("\\.(GET|POST|PUT|DELETE|PATCH|Get|Post|Put|Delete|Patch)\\(\\s*\"(/[^\"]*)\"");
    private static final Pattern LARAVEL = Pattern.compile("Route::(get|post|put|delete|patch|any)\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern RAILS = Pattern.compile("^\\s*(get|post|put|patch|delete)\\s+['\"]([^'\"]+)['\"]", M);

    private EndpointDetector() {
    }

    static List<FileModel.Endpoint> detect(Language language, String content) {
        List<FileModel.Endpoint> out = new ArrayList<>();
        int[] starts = lineStarts(content);
        switch (language) {
            case JAVA, KOTLIN, GROOVY -> {
                String prefix = classPrefix(content, SPRING, true);
                Matcher m = SPRING.matcher(content);
                while (m.find()) {
                    if (isClassLevel(content, m.end())) continue;
                    String args = m.group(2) == null ? "" : m.group(2);
                    String method = m.group(1).equals("Request") ? requestMethod(args) : m.group(1).toUpperCase(Locale.ROOT);
                    out.add(new FileModel.Endpoint(method, join(prefix, firstString(args)), lineOf(starts, m.start())));
                }
                String jaxPrefix = classPrefix(content, JAXRS_PATH, false);
                Matcher j = JAXRS_METHOD.matcher(content);
                while (j.find()) {
                    String path = nearbyPath(content, j.end());
                    out.add(new FileModel.Endpoint(j.group(1), join(jaxPrefix, path), lineOf(starts, j.start())));
                }
            }
            case CSHARP -> {
                String prefix = "";
                Matcher r = ASPNET_ROUTE.matcher(content);
                if (r.find()) prefix = r.group(1).replace("[controller]", "{controller}");
                Matcher m = ASPNET.matcher(content);
                while (m.find()) {
                    out.add(new FileModel.Endpoint(m.group(1).toUpperCase(Locale.ROOT), join(prefix, m.group(2)), lineOf(starts, m.start())));
                }
            }
            case JAVASCRIPT, TYPESCRIPT, VUE, SVELTE -> {
                Matcher c = NEST_CONTROLLER.matcher(content);
                String prefix = c.find() ? c.group(1) : "";
                Matcher n = NEST.matcher(content);
                while (n.find()) {
                    out.add(new FileModel.Endpoint(n.group(1).toUpperCase(Locale.ROOT), join(prefix, n.group(2)), lineOf(starts, n.start())));
                }
                Matcher e = EXPRESS.matcher(content);
                while (e.find()) {
                    String path = e.group(2);
                    if (path.startsWith("/") || path.equals("*")) {
                        out.add(new FileModel.Endpoint(e.group(1).toUpperCase(Locale.ROOT), path, lineOf(starts, e.start())));
                    }
                }
            }
            case PYTHON -> {
                Matcher f = FLASK.matcher(content);
                while (f.find()) {
                    String methods = f.group(2);
                    Matcher mm = Pattern.compile("['\"](GET|POST|PUT|DELETE|PATCH)['\"]", Pattern.CASE_INSENSITIVE).matcher(methods);
                    List<String> list = new ArrayList<>();
                    while (mm.find()) list.add(mm.group(1).toUpperCase(Locale.ROOT));
                    if (list.isEmpty()) list.add("GET");
                    for (String method : list) {
                        out.add(new FileModel.Endpoint(method, f.group(1), lineOf(starts, f.start())));
                    }
                }
                Matcher a = FASTAPI.matcher(content);
                while (a.find()) {
                    out.add(new FileModel.Endpoint(a.group(1).toUpperCase(Locale.ROOT), a.group(2), lineOf(starts, a.start())));
                }
                if (content.contains("urlpatterns")) {
                    Matcher d = DJANGO.matcher(content);
                    while (d.find()) {
                        out.add(new FileModel.Endpoint("ANY", "/" + d.group(1), lineOf(starts, d.start())));
                    }
                }
            }
            case GO -> {
                Matcher h = GO_HANDLE.matcher(content);
                while (h.find()) out.add(new FileModel.Endpoint("ANY", h.group(1), lineOf(starts, h.start())));
                Matcher g = GO_ROUTER.matcher(content);
                while (g.find()) {
                    out.add(new FileModel.Endpoint(g.group(1).toUpperCase(Locale.ROOT), g.group(2), lineOf(starts, g.start())));
                }
            }
            case PHP -> {
                Matcher l = LARAVEL.matcher(content);
                while (l.find()) {
                    out.add(new FileModel.Endpoint(l.group(1).toUpperCase(Locale.ROOT), l.group(2), lineOf(starts, l.start())));
                }
            }
            case RUBY -> {
                if (content.contains("routes.draw")) {
                    Matcher r = RAILS.matcher(content);
                    while (r.find()) {
                        out.add(new FileModel.Endpoint(r.group(1).toUpperCase(Locale.ROOT), r.group(2), lineOf(starts, r.start())));
                    }
                }
            }
            default -> {
            }
        }
        return out;
    }

    /** A mapping annotation is class-level when the next declaration after it is a class. */
    private static boolean isClassLevel(String content, int from) {
        Matcher next = Pattern.compile("\\b(class|interface|record|object)\\s+\\w+|\\(|\\bfun\\b|\\bvoid\\b").matcher(content);
        if (next.find(from)) {
            return next.group(1) != null;
        }
        return false;
    }

    private static String classPrefix(String content, Pattern pattern, boolean spring) {
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            if (spring && !m.group(1).equals("Request")) continue;
            if (isClassLevel(content, m.end())) {
                return spring ? firstString(m.group(2) == null ? "" : m.group(2)) : m.group(1);
            }
        }
        return "";
    }

    private static String nearbyPath(String content, int from) {
        int end = Math.min(content.length(), from + 300);
        Matcher p = JAXRS_PATH.matcher(content.substring(from, end));
        return p.find() ? p.group(1) : "";
    }

    private static String requestMethod(String args) {
        Matcher m = Pattern.compile("RequestMethod\\.(\\w+)").matcher(args);
        return m.find() ? m.group(1) : "ANY";
    }

    private static String firstString(String args) {
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(args);
        return m.find() ? m.group(1) : "";
    }

    private static String join(String prefix, String path) {
        String p = prefix == null ? "" : prefix.trim();
        String s = path == null ? "" : path.trim();
        String joined = (p.isEmpty() ? "" : "/" + p.replaceAll("^/+|/+$", "")) + (s.isEmpty() ? "" : "/" + s.replaceAll("^/+", ""));
        return joined.isEmpty() ? "/" : joined;
    }

    private static int[] lineStarts(String content) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') starts.add(i + 1);
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int lineOf(int[] starts, int offset) {
        int idx = java.util.Arrays.binarySearch(starts, offset);
        return (idx >= 0 ? idx : -idx - 2) + 1;
    }
}
