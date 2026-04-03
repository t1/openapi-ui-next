package com.github.t1.openapi.ui.generator;

import com.github.t1.bulmajava.basic.Color;
import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;
import com.github.t1.openapi.ui.components.SplitPane;
import com.github.t1.openapi.ui.components.Toggle;
import com.github.t1.openapi.ui.components.Tree;
import com.github.t1.openapi.ui.components.TreeContainer;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import static com.github.t1.bulmajava.basic.Color.DANGER;
import static com.github.t1.bulmajava.basic.Color.INFO;
import static com.github.t1.bulmajava.basic.Color.LINK;
import static com.github.t1.bulmajava.basic.Color.PRIMARY;
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

public class OpenApiUiGenerator {
    private static final Logger log = LoggerFactory.getLogger(OpenApiUiGenerator.class);

    private final Path specFile;
    private final Path outputDir;

    public OpenApiUiGenerator(Path specFile, Path outputDir) {
        this.specFile = specFile;
        this.outputDir = outputDir;
    }

    public void generate() throws IOException {
        var openApi = parseSpec();

        var root = new PathNode();
        for (var pathEntry : openApi.getPaths().entrySet()) {
            var segments = splitSegments(pathEntry.getKey());
            root.add(segments, 0, pathEntry.getValue());
        }

        var pathCount = openApi.getPaths().size();
        var operationCount = openApi.getPaths().values().stream()
                .mapToInt(p -> p.readOperationsMap().size()).sum();
        log.info("Found {} paths with {} operations", pathCount, operationCount);

        var pathTree = buildTree(root);
        var tagTree = TagTreeGenerator.buildTagTree(openApi, root);

        var page = buildPageLayout(openApi, pathTree, tagTree, shouldDefaultToTagView(openApi, pathCount));
        writeOutput(page, root, tagTree, pathTree);
    }

    private boolean shouldDefaultToTagView(io.swagger.v3.oas.models.OpenAPI openApi, int pathCount) {
        var uniqueTags = openApi.getPaths().values().stream()
                .flatMap(p -> p.readOperationsMap().values().stream())
                .filter(op -> op.getTags() != null)
                .flatMap(op -> op.getTags().stream())
                .distinct().count();
        var singleSegmentPaths = openApi.getPaths().keySet().stream()
                .filter(p -> splitSegments(p).size() == 1).count();
        return uniqueTags > 1 && singleSegmentPaths > pathCount / 2;
    }

    private io.swagger.v3.oas.models.OpenAPI parseSpec() {
        log.info("Parsing {}", specFile);
        System.setProperty(Schema.BIND_TYPE_AND_TYPES, "true"); // make getType() work for OpenAPI 3.1 schemas
        var parseOptions = new ParseOptions();
        parseOptions.setResolveFully(true);
        return new OpenAPIV3Parser().read(specFile.toString(), null, parseOptions);
    }

    private Renderable buildPageLayout(io.swagger.v3.oas.models.OpenAPI openApi, Renderable pathTree, Renderable tagTree, boolean defaultToTags) {
        var viewToggle = toggle("view")
                .option("paths", hxLoad("path-tree.html"))
                .option("tags", hxLoad("tag-tree.html"))
                .activate(defaultToTags ? "tags" : "paths");
        viewToggle.persistAs("openapi-ui-view");
        Renderable defaultTree = defaultToTags ? tagTree : pathTree;
        var treeContainer = div().id("tree-container").content(defaultTree);

        var servers = openApi.getServers();
        var baseUrl = (servers != null && !servers.isEmpty()) ? servers.getFirst().getUrl() : "/";

        var modeToggle = toggle("mode")
                .activeOption("try", o -> o.attr("title", "Send requests directly from the browser"))
                .option("httpie", o -> o.attr("title", "Copy as HTTPie command"))
                .option("curl", o -> o.attr("title", "Copy as curl command"))
                .attr("data-mode", "try").attr("data-base-url", baseUrl);

        var pageTitle = openApi.getInfo().getTitle();
        var detail = div().id("detail").attr("tabindex", "0");
        var detailHeader = div().classes("detail-header").content(
                title(pageTitle),
                modeToggle
        );
        var globalHeaders = div().id("global-headers").classes("global-headers", "is-collapsed")
                .content(
                        element("button").attr("type", "button").classes("global-headers-toggle")
                                .content(span("Global Headers"), span("0").classes("global-headers-count")),
                        div().classes("global-headers-body")
                                .content(element("button").attr("type", "button").classes("custom-header-add")
                                        .content("+ Add global header"))
                );
        var splitLayout = splitPane()
                .first(box().content(viewToggle, treeContainer))
                .second(detail)
                .persistAs("openapi-ui-tree-width");
        var errorBanner = div().id("error-banner").classes("notification", "is-danger")
                .style("display:none; position:fixed; bottom:0; left:0; right:0; margin:0; z-index:100; border-radius:0")
                .content("Backend not reachable — retrying...");
        var body = section().content(container().content(
                detailHeader,
                globalHeaders,
                splitLayout
        ), errorBanner);
        return html(pageTitle)
                .stylesheet("bulma.min.css")
                .stylesheet("css/all.min.css")
                .stylesheet("openapi-ui.css")
                .script("htmx.min.js")
                .script("highlight.min.js")
                .javaScriptCode(Toggle.js())
                .javaScriptCode(Tree.js())
                .javaScriptCode(SplitPane.js())
                .javaScriptCode(loadResource("app.js"))
                .body(body);
    }

    private void writeOutput(Renderable page, PathNode root, Renderable tagTree, Renderable pathTree) throws IOException {
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("index.html"), page.render());
        Files.writeString(outputDir.resolve("openapi-ui.css"), Toggle.css() + Tree.css() + SplitPane.css() + loadResource("app.css"));
        generateFragments(root, ApiPath.ROOT);
        Files.writeString(outputDir.resolve("tag-tree.html"), tagTree.render());
        Files.writeString(outputDir.resolve("path-tree.html"), pathTree.render());
        copyWebJarResource("bulma", "css/bulma.min.css", "bulma.min.css");
        copyWebJarResource("htmx.org", "dist/htmx.min.js", "htmx.min.js");
        copyWebJarResource("highlightjs", "highlight.min.js", "highlight.min.js");
        Files.createDirectories(outputDir.resolve("css"));
        Files.createDirectories(outputDir.resolve("webfonts"));
        copyWebJarResource("fortawesome__fontawesome-free", "css/all.min.css", "css/all.min.css");
        copyWebJarResource("fortawesome__fontawesome-free", "webfonts/fa-solid-900.woff2", "webfonts/fa-solid-900.woff2");
        copyWebJarResource("fortawesome__fontawesome-free", "webfonts/fa-regular-400.woff2", "webfonts/fa-regular-400.woff2");
        log.info("Done. Output written to {}", outputDir);
    }

    private String resolveWebJarGroupId(String artifactId) {
        if (getClass().getResource("/META-INF/maven/org.webjars.npm/" + artifactId + "/pom.properties") != null)
            return "org.webjars.npm";
        return "org.webjars";
    }

    private void copyWebJarResource(String artifactId, String resourcePath, String outputName) throws IOException {
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
            Files.copy(resource, outputDir.resolve(outputName), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Tree buildTree(PathNode root) {
        var t = tree();
        addNodes(t, root, ApiPath.ROOT);
        return t;
    }

    private void addNodes(TreeContainer tree, PathNode node, ApiPath pathPrefix) {
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.resolve(segment);
            var label = span().content(span(segment).classes(segmentClass(segment)));
            if (!child.operations.isEmpty()) {
                var group = tagsAddon();
                for (var method : child.operations.keySet()) {
                    group.content(tag(method.name()).is(methodColor(method)));
                }
                label.content(group);
            }
            if (!child.children.isEmpty()) {
                if (!child.operations.isEmpty()) {
                    label.attr("hx-get", fullPath + "/index.html")
                            .attr("hx-target", "#detail")
                            .attr("hx-swap", "innerHTML");
                }
                tree.node(label, sub -> addNodes(sub, child, fullPath));
            } else {
                tree.item(label, item -> {
                    if (!child.operations.isEmpty()) {
                        item.attr("hx-get", fullPath + "/index.html")
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

    private void generateFragments(PathNode node, ApiPath pathPrefix) throws IOException {
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.resolve(segment);
            for (var opEntry : child.operations.entrySet()) {
                var ctx = new OperationContext(opEntry.getKey(), opEntry.getValue(), fullPath);
                var fragment = MethodFragmentGenerator.buildContent(ctx);
                var fragmentDir = outputDir.resolve(fullPath.toString());
                Files.createDirectories(fragmentDir);
                Files.writeString(fragmentDir.resolve(opEntry.getKey().name() + ".html"), fragment.render());
                for (var responseEntry : MethodFragmentGenerator.buildResponseFragments(ctx).entrySet()) {
                    Files.writeString(fragmentDir.resolve(responseEntry.getKey()), responseEntry.getValue());
                }
            }
            if (!child.operations.isEmpty()) {
                var pathFragment = PathFragmentGenerator.buildContent(fullPath, child.operations);
                var pathFragmentDir = outputDir.resolve(fullPath.toString());
                Files.createDirectories(pathFragmentDir);
                Files.writeString(pathFragmentDir.resolve("index.html"), pathFragment.render());
            }
            generateFragments(child, fullPath);
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


    static class PathNode {
        final Map<String, PathNode> children = new LinkedHashMap<>();
        final Map<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> operations = new LinkedHashMap<>();

        void add(List<String> segments, int index, PathItem pathItem) {
            if (index >= segments.size()) {
                operations.putAll(pathItem.readOperationsMap());
                return;
            }
            var segment = segments.get(index);
            var key = isPathParam(segment) ? existingParamKeyOrElse(segment) : segment;
            children.computeIfAbsent(key, k -> new PathNode())
                    .add(segments, index + 1, pathItem);
        }

        private String existingParamKeyOrElse(String segment) {
            return children.keySet().stream()
                    .filter(PathNode::isPathParam)
                    .findFirst()
                    .orElse(segment);
        }

        private static boolean isPathParam(String segment) {
            return segment.startsWith("{") && segment.endsWith("}");
        }
    }
}
