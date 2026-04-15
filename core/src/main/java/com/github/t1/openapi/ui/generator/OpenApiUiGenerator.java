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
        generateOutput(page, root, tagTree, pathTree, operationIdMap, openApi.getServers(), openApi.getComponents(), openApi.getSecurity());
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
        var modeSelector = modeSelector(baseUrl);

        var pageTitle = openApi.getInfo().getTitle();
        var detail = div().id("detail").attr("tabindex", "0");
        var detailHeader = div().classes("detail-header").content(title(pageTitle), serverSelector(openApi));
        var detailPane = div().classes("detail-pane").content(detail, modeSelector);
        var splitLayout = splitPane()
                .first(box().content(viewToggle, treeContainer))
                .second(detailPane)
                .persistAs("openapi-ui-tree-width");
        var body = section().content(container().content(
                detailHeader,
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

    private static Renderable modeSelector(String baseUrl) {
        var modeToggle = toggle("mode")
                .activeOption("try", o -> o.attr("title", "Send requests directly from the browser"))
                .option("httpie", o -> o.attr("title", "Copy as HTTPie command"))
                .option("curl", o -> o.attr("title", "Copy as curl command"))
                .option("overflow", "▾", o -> o.attr("title", "More formats").attr("data-overflow", "true"))
                .attr("data-mode", "try").attr("data-base-url", baseUrl);
        var sendButton = element("button").attr("type", "button").attr("tabindex", "-1").classes("button", "is-primary", "mode-send-button").content("Send");
        modeToggle.content(sendButton);

        var dropdownMenu = div().classes("mode-dropdown-menu")
                .content(
                        div().classes("mode-dropdown-item").attr("data-generator", "httpie").content("HTTPie"),
                        div().classes("mode-dropdown-item").attr("data-generator", "curl").content("curl"),
                        div().classes("mode-dropdown-item").attr("data-generator", "JS fetch").content("JS fetch"),
                        div().classes("mode-dropdown-item").attr("data-generator", "Java HttpClient").content("Java HttpClient"),
                        div().classes("mode-dropdown-item").attr("data-generator", "JAX-RS").content("JAX-RS"),
                        div().classes("mode-dropdown-item").attr("data-generator", "Python").content("Python"),
                        div().classes("mode-dropdown-item").attr("data-generator", "Go").content("Go"),
                        div().classes("mode-dropdown-item").attr("data-generator", "MP Rest Client").content("MP Rest Client"),
                        div().classes("mode-dropdown-item").attr("data-generator", "Spring WebClient").content("Spring WebClient"),
                        div().classes("mode-dropdown-item").attr("data-generator", "Spring RestTemplate").content("Spring RestTemplate")
                );

        return div().classes("mode-selector-container").content(modeToggle, dropdownMenu);
    }

    private static Renderable serverSelector(OpenAPI openApi) {
        var servers = openApi.getServers();

        // Trigger button
        var triggerUrl = (servers != null && !servers.isEmpty()) ? resolveFirstServerUrl(servers) : "";
        var triggerButton = element("button").attr("type", "button")
                .attr("aria-haspopup", "true")
                .classes("button", "server-dropdown-trigger")
                .content(span(triggerUrl).classes("server-url"), span("▾").classes("dropdown-arrow"));
        var trigger = div().classes("dropdown-trigger").content(triggerButton);

        // Dropdown content
        var dropdownContent = div().classes("dropdown-content");

        if (servers == null || servers.isEmpty()) {
            // No servers case — single radio item resolved from origin
            var radioId = "server-0";
            var radio = element("input").attr("type", "radio").attr("name", "server")
                    .attr("id", radioId).attr("value", "").attr("checked", "");
            var label = element("label").attr("for", radioId).classes("dropdown-item", "server-row");
            label.content(radio);
            label.content(div().classes("server-item-url"));
            label.content(div().classes("server-item-description").content("resolved from origin"));
            dropdownContent.content(label);
        } else {
            for (var i = 0; i < servers.size(); i++) {
                if (i > 0) dropdownContent.content(element("hr").classes("dropdown-divider"));
                var server = servers.get(i);
                var url = server.getUrl();
                var description = server.getDescription();
                var isFirstServer = (i == 0);

                if (server.getVariables() != null && !server.getVariables().isEmpty()) {
                    // Template server — group inside dropdown
                    var templateGroup = div().classes("template-group");
                    var heading = div().classes("template-group-heading");
                    heading.content(div().classes("server-item-url").content(url));
                    if (description != null && !description.isEmpty()) {
                        heading.content(div().classes("server-item-description").content(description));
                    }
                    templateGroup.content(heading);

                    var allVariablesHaveDefaults = server.getVariables().values().stream()
                            .allMatch(v -> v.getDefaultValue() != null);

                    if (allVariablesHaveDefaults) {
                        var resolvedUrl = resolveTemplateVariables(url, server.getVariables());
                        var presetId = "server-" + i + "-preset-0";
                        var radio = element("input").attr("type", "radio").attr("name", "server").attr("id", presetId)
                                .attr("value", resolvedUrl);
                        if (isFirstServer) radio.attr("checked", "");

                        var label = element("label").attr("for", presetId).classes("template-preset-label");
                        label.content(radio, span().classes("server-item-url").content(resolvedUrl));
                        templateGroup.content(label);

                        var addPresetBtn = element("button").attr("type", "button")
                                .classes("template-preset-add")
                                .attr("data-server-index", String.valueOf(i))
                                .attr("data-url-template", url);
                        var variablesJson = serializeVariables(server.getVariables());
                        addPresetBtn.attr("data-variables", variablesJson);
                        addPresetBtn.content("+ Add preset");
                        templateGroup.content(addPresetBtn);
                    }

                    dropdownContent.content(templateGroup);
                } else {
                    // Non-template server — radio item in dropdown
                    var radioId = "server-" + i;
                    var radio = element("input").attr("type", "radio").attr("name", "server")
                            .attr("id", radioId).attr("value", url);
                    if (isFirstServer) radio.attr("checked", "");

                    var label = element("label").attr("for", radioId).classes("dropdown-item", "server-row");
                    label.content(radio);
                    label.content(div().classes("server-item-url").content(url));
                    if (description != null && !description.isEmpty()) {
                        label.content(div().classes("server-item-description").content(description));
                    }
                    dropdownContent.content(label);
                }
            }
        }

        // Server override OOB swap target
        dropdownContent.content(div().id("server-override"));

        // Custom URL section
        dropdownContent.content(element("hr").classes("dropdown-divider"));
        dropdownContent.content(div().classes("custom-url-block")
                .content(element("button").attr("type", "button").classes("custom-url-add")
                        .content("+ Add custom URL")));

        var menu = div().classes("dropdown-menu").attr("role", "menu").content(dropdownContent);

        return div().id("server-selector").classes("dropdown", "is-right").content(trigger, menu);
    }

    private static String resolveFirstServerUrl(List<org.eclipse.microprofile.openapi.models.servers.Server> servers) {
        var first = servers.getFirst();
        if (first.getVariables() != null && !first.getVariables().isEmpty()) {
            var allHaveDefaults = first.getVariables().values().stream()
                    .allMatch(v -> v.getDefaultValue() != null);
            if (allHaveDefaults) return resolveTemplateVariables(first.getUrl(), first.getVariables());
        }
        return first.getUrl();
    }

    private static String resolveTemplateVariables(String url, Map<String, org.eclipse.microprofile.openapi.models.servers.ServerVariable> variables) {
        var resolvedUrl = url;
        for (var entry : variables.entrySet()) {
            resolvedUrl = resolvedUrl.replace("{" + entry.getKey() + "}", entry.getValue().getDefaultValue());
        }
        return resolvedUrl;
    }

    private static String serializeVariables(Map<String, org.eclipse.microprofile.openapi.models.servers.ServerVariable> variables) {
        var json = new StringBuilder("[");
        var first = true;
        for (var entry : variables.entrySet()) {
            if (!first) json.append(",");
            first = false;
            var variable = entry.getValue();
            json.append("{")
                    .append("\"name\":\"").append(entry.getKey()).append("\",")
                    .append("\"default\":\"").append(variable.getDefaultValue() != null ? variable.getDefaultValue() : "").append("\"");
            if (variable.getEnumeration() != null && !variable.getEnumeration().isEmpty()) {
                json.append(",\"enum\":[");
                json.append(String.join(",", variable.getEnumeration().stream()
                        .map(v -> "\"" + v + "\"").toList()));
                json.append("]");
            }
            if (variable.getDescription() != null) {
                json.append(",\"description\":\"").append(variable.getDescription()).append("\"");
            }
            json.append("}");
        }
        json.append("]");
        return json.toString();
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

    private void generateOutput(Renderable page, PathNode root, Renderable tagTree, Renderable pathTree, Map<String, String[]> operationIdMap, List<org.eclipse.microprofile.openapi.models.servers.Server> globalServers, Components components, List<org.eclipse.microprofile.openapi.models.security.SecurityRequirement> globalSecurity) throws IOException {
        output.accept("index.html", page.render().getBytes());
        output.accept("tag-tree.html", tagTree.render().getBytes());
        output.accept("path-tree.html", pathTree.render().getBytes());
        var tagFilterCss = generateTagFilterCss();
        output.accept("openapi-ui.css", (Toggle.css() + Tree.css() + SplitPane.css() + loadResource("app.css") + tagFilterCss).getBytes());
        generateFragments(root, ApiPath.ROOT, operationIdMap, globalServers, components, globalSecurity);
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

    private Renderable pathTree(PathNode root) {
        var t = tree();
        addTreeItems(t, root, ApiPath.ROOT);
        
        var tags = collectUniqueTags();
        if (tags.size() < 2) {
            return t;
        }
        
        // Filter icon + pill panel + tree
        var wrapper = div();
        var filterIcon = element("i").classes("fa-solid", "fa-filter", "filter-icon").attr("role", "button").attr("tabindex", "0");
        wrapper.content(filterIcon);
        
        var pillPanel = div().classes("filter-pill-panel");
        for (var tag : tags) {
            var sanitized = sanitizeTagName(tag);
            var pill = span(tag).classes("tag", "tag-" + sanitized).attr("tabindex", "0").attr("role", "button");
            pillPanel.content(pill);
        }
        wrapper.content(pillPanel);
        wrapper.content(t);
        
        var statusLine = div().classes("filter-status-line");
        wrapper.content(statusLine);
        
        return wrapper;
    }

    private void addTreeItems(TreeContainer tree, PathNode node, ApiPath path) {
        for (var entry : node.children().entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var childPath = path.resolve(segment);
            var label = span().content(span(segment).classes(segmentClass(segment)));
            var tagClasses = collectTagClasses(child);
            if (!child.operations().isEmpty()) {
                var group = tagsAddon();
                for (var methodEntry : child.operations().entrySet()) {
                    var method = methodEntry.getKey();
                    var operation = methodEntry.getValue();
                    var badge = tag(method.name()).is(methodColor(method));
                    if (operation.getTags() != null && !operation.getTags().isEmpty()) {
                        for (var tagName : operation.getTags()) {
                            badge.classes("tag-" + sanitizeTagName(tagName));
                        }
                    }
                    group.content(badge);
                }
                label.content(group);
            }
            if (!child.children().isEmpty()) {
                if (!child.operations().isEmpty()) {
                    label.attr("hx-get", childPath + "/index.html")
                            .attr("hx-target", "#detail")
                            .attr("hx-swap", "innerHTML");
                }
                tree.node(label, sub -> {
                    if (!tagClasses.isEmpty()) {
                        sub.customizeItem(item -> item.classes(tagClasses.toArray(new String[0])));
                    }
                    addTreeItems(sub, child, childPath);
                });
            } else {
                tree.item(label, item -> {
                    if (!tagClasses.isEmpty()) {
                        item.classes(tagClasses.toArray(new String[0]));
                    }
                    if (!child.operations().isEmpty()) {
                        var treeLabel = item.findElement("tree-label")
                                .orElseThrow(() -> new IllegalStateException("tree-label not found in item"));
                        treeLabel.attr("hx-get", childPath + "/index.html")
                                .attr("hx-target", "#detail")
                                .attr("hx-swap", "innerHTML");
                    }
                });
            }
        }
    }

    private List<String> collectTagClasses(PathNode node) {
        return node.operations().values().stream()
                .filter(op -> op.getTags() != null)
                .flatMap(op -> op.getTags().stream())
                .distinct()
                .map(this::sanitizeTagName)
                .map(tag -> "tag-" + tag)
                .toList();
    }

    private String sanitizeTagName(String tagName) {
        return tagName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private List<String> collectUniqueTags() {
        return openApi.getPaths().getPathItems().values().stream()
                .flatMap(p -> p.getOperations().values().stream())
                .filter(op -> op.getTags() != null)
                .flatMap(op -> op.getTags().stream())
                .distinct()
                .toList();
    }

    private String generateTagFilterCss() {
        var tags = collectUniqueTags();
        if (tags.isEmpty()) {
            return "";
        }
        var css = new StringBuilder();
        for (var tag : tags) {
            var sanitized = sanitizeTagName(tag);
            // Hide tree items that don't match the filter AND don't contain matching descendants
            css.append(".filter-").append(sanitized).append(" [role=\"treeitem\"]:not(.tag-").append(sanitized).append("):not(:has(.tag-").append(sanitized).append(")) {\n");
            css.append("    display: none;\n");
            css.append("}\n");
            // Hide method badges that don't match the filter
            css.append(".filter-").append(sanitized).append(" .tags > :not(.tag-").append(sanitized).append(") {\n");
            css.append("    display: none;\n");
            css.append("}\n");
        }
        return css.toString();
    }

    private static String segmentClass(String segment) {
        return PathNode.isPathParam(segment) ? "tree-param" : "tree-segment";
    }

    private void generateFragments(PathNode node, ApiPath path, Map<String, String[]> operationIdMap, List<org.eclipse.microprofile.openapi.models.servers.Server> globalServers, Components components, List<org.eclipse.microprofile.openapi.models.security.SecurityRequirement> globalSecurity) {
        for (var entry : node.children().entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var childPath = path.resolve(segment);
            for (var opEntry : child.operations().entrySet()) {
                var operation = new Operation(opEntry.getKey(), opEntry.getValue(), childPath, child.pathItem(), globalServers, components, globalSecurity);
                var fragment = operationFragment(operation, operationIdMap);
                output.accept(childPath + "/" + opEntry.getKey().name() + ".html", fragment.render().getBytes());
                for (var responseEntry : responseFragments(operation, operationIdMap).entrySet()) {
                    output.accept(childPath + "/" + responseEntry.getKey(), responseEntry.getValue().getBytes());
                }
            }
            if (!child.operations().isEmpty()) {
                var pathFrag = pathFragment(childPath, child.operations(), operationIdMap, child.pathItem(), globalServers, components, globalSecurity);
                output.accept(childPath + "/index.html", pathFrag.render().getBytes());
            }
            generateFragments(child, childPath, operationIdMap, globalServers, components, globalSecurity);
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
            case PUT, PATCH -> WARNING;
            case DELETE -> DANGER;
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
