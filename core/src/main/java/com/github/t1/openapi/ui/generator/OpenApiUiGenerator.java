package com.github.t1.openapi.ui.generator;

import com.github.t1.bulmajava.basic.Color;
import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;
import com.github.t1.openapi.ui.components.SplitPane;
import com.github.t1.openapi.ui.components.Toggle;
import com.github.t1.openapi.ui.components.Tree;
import com.github.t1.openapi.ui.components.TreeContainer;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.github.t1.bulmajava.basic.Color.DANGER;
import static com.github.t1.bulmajava.basic.Color.INFO;
import static com.github.t1.bulmajava.basic.Color.LINK;
import static com.github.t1.bulmajava.basic.Color.SUCCESS;
import static com.github.t1.bulmajava.basic.Color.WARNING;
import static com.github.t1.bulmajava.elements.Box.box;
import static com.github.t1.bulmajava.elements.Tag.tag;
import static com.github.t1.bulmajava.elements.Tag.tagsAddon;
import static com.github.t1.bulmajava.elements.Title.title;
import static com.github.t1.bulmajava.layout.Container.container;
import static com.github.t1.bulmajava.layout.Section.section;
import static com.github.t1.htmljava.Html.html;
import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.htmljava.HtmlBasics.span;
import static com.github.t1.openapi.ui.components.SplitPane.splitPane;
import static com.github.t1.openapi.ui.components.Toggle.toggle;
import static com.github.t1.openapi.ui.components.Tree.tree;
import static com.github.t1.openapi.ui.generator.OperationFragmentGenerator.operationFragment;
import static com.github.t1.openapi.ui.generator.OperationFragmentGenerator.responseFragments;
import static com.github.t1.openapi.ui.generator.PathFragmentGenerator.pathFragment;

public class OpenApiUiGenerator {
    private static final Logger log = LoggerFactory.getLogger(OpenApiUiGenerator.class);

    private final OpenAPI openApi;
    private final BiConsumer<String, byte[]> output;

    public OpenApiUiGenerator(OpenAPI openApi, BiConsumer<String, byte[]> output) {
        this.openApi = openApi;
        this.output = output;
    }

    public void generate() throws IOException {
        var root = new PathNode();
        for (var pathEntry : openApi.getPaths().getPathItems().entrySet()) {
            var segments = splitSegments(pathEntry.getKey());
            root.add(segments, 0, pathEntry.getValue());
        }

        var pathCount = openApi.getPaths().getPathItems().size();
        var operationCount = openApi.getPaths().getPathItems().values().stream()
                .mapToInt(p -> p.getOperations().size()).sum();
        log.info("Found {} paths with {} operations", pathCount, operationCount);

        var operationIdMap = new LinkedHashMap<String, String[]>();
        collectOperationIds(root, ApiPath.ROOT, operationIdMap);

        var pathTree = pathTree(root);
        var tagTree = TagTreeGenerator.tagTree(openApi, root);

        var page = pageLayout(openApi, pathTree, tagTree, shouldDefaultToTagView(openApi, pathCount), operationIdMap);
        generateOutput(page, root, tagTree, pathTree, operationIdMap, openApi.getComponents());
    }

    private boolean shouldDefaultToTagView(OpenAPI openApi, int pathCount) {
        var uniqueTags = openApi.getPaths().getPathItems().values().stream()
                .flatMap(p -> p.getOperations().values().stream())
                .filter(op -> op.getTags() != null)
                .flatMap(op -> op.getTags().stream())
                .distinct().count();
        var singleSegmentPaths = openApi.getPaths().getPathItems().keySet().stream()
                .filter(p -> splitSegments(p).size() == 1).count();
        return uniqueTags > 1 && singleSegmentPaths > pathCount / 2;
    }

    private Renderable pageLayout(OpenAPI openApi, Renderable pathTree, Renderable tagTree, boolean defaultToTags, Map<String, String[]> operationIdMap) {
        var viewToggle = viewToggle(defaultToTags);
        Renderable defaultTree = defaultToTags ? tagTree : pathTree;
        var treeContainer = div().id("tree-container").content(defaultTree);

        var baseUrl = resolveBaseUrl(openApi);
        var modeToggle = modeToggle(baseUrl);

        var pageTitle = openApi.getInfo().getTitle();
        var detail = div().id("detail").attr("tabindex", "0");
        var detailHeader = div().classes("detail-header").content(title(pageTitle), modeToggle);
        var splitLayout = splitPane()
                .first(box().content(viewToggle, treeContainer))
                .second(detail)
                .persistAs("openapi-ui-tree-width");
        var body = section().content(container().content(
                detailHeader,
                globalHeaders(),
                splitLayout
        ), errorBanner());
        return htmlDocument(pageTitle, body, operationIdMap);
    }

    private Toggle viewToggle(boolean defaultToTags) {
        var viewToggle = toggle("view")
                .option("paths", hxLoad("path-tree.html"))
                .option("tags", hxLoad("tag-tree.html"))
                .activate(defaultToTags ? "tags" : "paths");
        viewToggle.persistAs("openapi-ui-view");
        return viewToggle;
    }

    private static String resolveBaseUrl(OpenAPI openApi) {
        var servers = openApi.getServers();
        return (servers != null && !servers.isEmpty()) ? servers.getFirst().getUrl() : "/";
    }

    private static Renderable modeToggle(String baseUrl) {
        return toggle("mode")
                .activeOption("try", o -> o.attr("title", "Send requests directly from the browser"))
                .option("httpie", o -> o.attr("title", "Copy as HTTPie command"))
                .option("curl", o -> o.attr("title", "Copy as curl command"))
                .attr("data-mode", "try").attr("data-base-url", baseUrl);
    }

    private static Element globalHeaders() {
        return div().id("global-headers").classes("global-headers", "is-collapsed")
                .content(
                        element("button").attr("type", "button").classes("global-headers-toggle")
                                .content(span("Global Headers"), span("0").classes("global-headers-count")),
                        div().classes("global-headers-body")
                                .content(element("button").attr("type", "button").classes("custom-header-add")
                                        .content("+ Add global header"))
                );
    }

    private static Element errorBanner() {
        return div().id("error-banner").classes("notification", "is-danger")
                .style("display:none; position:fixed; bottom:0; left:0; right:0; margin:0; z-index:100; border-radius:0")
                .content("Backend not reachable — retrying...");
    }

    private static Renderable htmlDocument(String pageTitle, Renderable body, Map<String, String[]> operationIdMap) {
        return html(pageTitle)
                .stylesheet("vendor/bulma.min.css")
                .stylesheet("vendor/css/fontawesome.min.css")
                .stylesheet("vendor/css/solid.min.css")
                .stylesheet("openapi-ui.css")
                .script("vendor/htmx.min.js")
                .script("vendor/highlight.min.js")
                .javaScriptCode(operationIdMapScript(operationIdMap))
                .javaScriptCode(Toggle.js())
                .javaScriptCode(Tree.js())
                .javaScriptCode(SplitPane.js())
                .javaScriptCode(loadResource("app.js"))
                .body(body);
    }

    private static String operationIdMapScript(Map<String, String[]> operationIdMap) {
        var sb = new StringBuilder("window._operationIdMap={");
        var first = true;
        for (var entry : operationIdMap.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":")
                    .append("{path:\"").append(entry.getValue()[0]).append("\",method:\"").append(entry.getValue()[1]).append("\"}");
        }
        sb.append("};");
        return sb.toString();
    }

    private void generateOutput(Renderable page, PathNode root, Renderable tagTree, Renderable pathTree, Map<String, String[]> operationIdMap, Components components) throws IOException {
        output.accept("index.html", page.render().getBytes());
        output.accept("tag-tree.html", tagTree.render().getBytes());
        output.accept("path-tree.html", pathTree.render().getBytes());
        output.accept("openapi-ui.css", (Toggle.css() + Tree.css() + SplitPane.css() + loadResource("app.css")).getBytes());
        generateFragments(root, ApiPath.ROOT, operationIdMap, components);
        outputVendorResources();
        log.info("Done");
    }

    private void outputVendorResources() throws IOException {
        outputWebJarResource("bulma", "css/bulma.min.css", "vendor/bulma.min.css");
        outputWebJarResource("htmx.org", "dist/htmx.min.js", "vendor/htmx.min.js");
        outputWebJarResource("highlightjs", "highlight.min.js", "vendor/highlight.min.js");
        outputWebJarResource("fortawesome__fontawesome-free", "css/fontawesome.min.css", "vendor/css/fontawesome.min.css");
        outputWebJarResource("fortawesome__fontawesome-free", "css/solid.min.css", "vendor/css/solid.min.css");
        outputWebJarResource("fortawesome__fontawesome-free", "webfonts/fa-solid-900.woff2", "vendor/webfonts/fa-solid-900.woff2");
    }

    private String resolveWebJarGroupId(String artifactId) {
        if (getClass().getResource("/META-INF/maven/org.webjars.npm/" + artifactId + "/pom.properties") != null)
            return "org.webjars.npm";
        return "org.webjars";
    }

    private void outputWebJarResource(String artifactId, String resourcePath, String outputName) throws IOException {
        var groupId = resolveWebJarGroupId(artifactId);
        var props = new Properties();
        try (var pom = getClass().getResourceAsStream(
                "/META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties")) {
            props.load(pom);
        }
        var version = props.getProperty("version");
        var path = "/META-INF/resources/webjars/" + artifactId + "/" + version + "/" + resourcePath;
        try (var resource = getClass().getResourceAsStream(path)) {
            assert resource != null : "webjar resource not found: " + path;
            output.accept(outputName, resource.readAllBytes());
        }
    }

    private static void collectOperationIds(PathNode node, ApiPath path, Map<String, String[]> map) {
        for (var entry : node.children().entrySet()) {
            var child = entry.getValue();
            var childPath = path.resolve(entry.getKey());
            for (var opEntry : child.operations().entrySet()) {
                var operationId = opEntry.getValue().getOperationId();
                if (operationId != null) {
                    map.put(operationId, new String[]{childPath.toString(), opEntry.getKey().name()});
                }
            }
            collectOperationIds(child, childPath, map);
        }
    }

    private Tree pathTree(PathNode root) {
        var t = tree();
        addTreeItems(t, root, ApiPath.ROOT);
        return t;
    }

    private void addTreeItems(TreeContainer tree, PathNode node, ApiPath path) {
        for (var entry : node.children().entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var childPath = path.resolve(segment);
            var label = span().content(span(segment).classes(segmentClass(segment)));
            if (!child.operations().isEmpty()) {
                var group = tagsAddon();
                for (var method : child.operations().keySet()) {
                    group.content(tag(method.name()).is(methodColor(method)));
                }
                label.content(group);
            }
            if (!child.children().isEmpty()) {
                if (!child.operations().isEmpty()) {
                    label.attr("hx-get", childPath + "/index.html")
                            .attr("hx-target", "#detail")
                            .attr("hx-swap", "innerHTML");
                }
                tree.node(label, sub -> addTreeItems(sub, child, childPath));
            } else {
                tree.item(label, item -> {
                    if (!child.operations().isEmpty()) {
                        item.attr("hx-get", childPath + "/index.html")
                                .attr("hx-target", "#detail")
                                .attr("hx-swap", "innerHTML");
                    }
                });
            }
        }
    }

    private static String segmentClass(String segment) {
        return PathNode.isPathParam(segment) ? "tree-param" : "tree-segment";
    }

    private void generateFragments(PathNode node, ApiPath path, Map<String, String[]> operationIdMap, Components components) {
        for (var entry : node.children().entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var childPath = path.resolve(segment);
            for (var opEntry : child.operations().entrySet()) {
                var operation = new Operation(opEntry.getKey(), opEntry.getValue(), childPath, components);
                var fragment = operationFragment(operation, operationIdMap);
                output.accept(childPath + "/" + opEntry.getKey().name() + ".html", fragment.render().getBytes());
                for (var responseEntry : responseFragments(operation, operationIdMap).entrySet()) {
                    output.accept(childPath + "/" + responseEntry.getKey(), responseEntry.getValue().getBytes());
                }
            }
            if (!child.operations().isEmpty()) {
                var pathFrag = pathFragment(childPath, child.operations(), operationIdMap, components);
                output.accept(childPath + "/index.html", pathFrag.render().getBytes());
            }
            generateFragments(child, childPath, operationIdMap, components);
        }
    }

    private List<String> splitSegments(String path) {
        var stripped = path.startsWith("/") ? path.substring(1) : path;
        return List.of(stripped.split("/"));
    }

    private static Consumer<Element> hxLoad(String snippet) {
        return o -> o.attr("hx-get", snippet).attr("hx-target", "#tree-container").attr("hx-swap", "innerHTML");
    }

    static Color methodColor(PathItem.HttpMethod method) {
        return switch (method) {
            case GET -> SUCCESS;
            case POST -> LINK;
            case PUT -> WARNING;
            case DELETE -> DANGER;
            case PATCH -> WARNING;
            default -> INFO;
        };
    }

    private static String loadResource(String name) {
        try (var stream = OpenApiUiGenerator.class.getResourceAsStream(name)) {
            if (stream == null) throw new IllegalStateException("resource not found: " + name);
            return new String(stream.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("could not load resource: " + name, e);
        }
    }

}
