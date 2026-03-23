package com.github.t1.openapi.ui;

import com.github.t1.bulmajava.basic.Color;
import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import static com.github.t1.bulmajava.basic.Size.MEDIUM;
import static com.github.t1.bulmajava.components.Message.message;
import static com.github.t1.bulmajava.components.Message.messageBody;
import static com.github.t1.bulmajava.elements.Box.box;
import static com.github.t1.bulmajava.elements.Button.button;
import static com.github.t1.bulmajava.elements.Tag.tag;
import static com.github.t1.bulmajava.elements.Tag.tagsAddon;
import static com.github.t1.bulmajava.form.Field.field;
import static com.github.t1.bulmajava.form.Input.input;
import static com.github.t1.bulmajava.form.InputType.TEXT;
import static com.github.t1.bulmajava.form.Select.select;
import static com.github.t1.bulmajava.layout.Container.container;
import static com.github.t1.bulmajava.layout.Level.level;
import static com.github.t1.bulmajava.layout.Section.section;
import static com.github.t1.htmljava.Html.html;
import static com.github.t1.htmljava.HtmlBasics.code;
import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.htmljava.HtmlBasics.span;
import static com.github.t1.openapi.ui.SplitPane.splitPane;
import static com.github.t1.openapi.ui.Toggle.toggle;
import static com.github.t1.openapi.ui.Tree.tree;

public class OpenApiUiGenerator {
    private static final Logger log = LoggerFactory.getLogger(OpenApiUiGenerator.class);

    private final Path specFile;
    private final Path outputDir;

    public OpenApiUiGenerator(Path specFile, Path outputDir) {
        this.specFile = specFile;
        this.outputDir = outputDir;
    }

    public void generate() throws IOException {
        log.info("Parsing {}", specFile);
        var parseOptions = new ParseOptions();
        parseOptions.setResolveFully(true);
        var openApi = new OpenAPIV3Parser().read(specFile.toString(), null, parseOptions);

        var root = new PathNode();
        for (var pathEntry : openApi.getPaths().entrySet()) {
            var pathString = pathEntry.getKey();
            var pathItem = pathEntry.getValue();
            var segments = splitSegments(pathString);
            root.add(segments, 0, pathItem);
        }

        var pathCount = openApi.getPaths().size();
        var operationCount = openApi.getPaths().values().stream()
                .mapToInt(p -> p.readOperationsMap().size()).sum();
        log.info("Found {} paths with {} operations", pathCount, operationCount);

        var pathTree = buildTree(root);
        var tagTree = buildTagTree(openApi, root);

        var uniqueTags = openApi.getPaths().values().stream()
                .flatMap(p -> p.readOperationsMap().values().stream())
                .filter(op -> op.getTags() != null)
                .flatMap(op -> op.getTags().stream())
                .distinct().count();
        var singleSegmentPaths = openApi.getPaths().keySet().stream()
                .filter(p -> splitSegments(p).size() == 1).count();
        var totalPaths = openApi.getPaths().size();
        var defaultToTags = uniqueTags > 1 && singleSegmentPaths > totalPaths / 2;

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
                .activeOption("try", o -> o.attr("title", "Send requests directly from the browser (1)"))
                .option("httpie", o -> o.attr("title", "Copy as HTTPie command (2)"))
                .option("curl", o -> o.attr("title", "Copy as curl command (3)"))
                .attr("data-mode", "try").attr("data-base-url", baseUrl);

        var pageTitle = openApi.getInfo().getTitle();
        var detail = div().id("detail").attr("tabindex", "0");
        var detailHeader = div().classes("detail-header").content(
                element("h1").classes("title").content(pageTitle),
                modeToggle
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
                splitLayout
        ), errorBanner);
        var page = html(pageTitle)
                .stylesheet("bulma.min.css")
                .stylesheet("openapi-ui.css")
                .script("htmx.min.js")
                .javaScriptCode(Toggle.js())
                .javaScriptCode(Tree.js())
                .javaScriptCode(SplitPane.js())
                .javaScriptCode(APP_JS)
                .body(body);

        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("index.html"), page.render());
        Files.writeString(outputDir.resolve("openapi-ui.css"), Toggle.css() + Tree.css() + SplitPane.css() + APP_CSS);

        generateFragments(root, "");
        Files.writeString(outputDir.resolve("tag-tree.html"), tagTree.render());
        Files.writeString(outputDir.resolve("path-tree.html"), pathTree.render());

        copyWebJarResource("bulma", "css/bulma.min.css", "bulma.min.css");
        copyWebJarResource("htmx.org", "dist/htmx.min.js", "htmx.min.js");
        log.info("Done. Output written to {}", outputDir);
    }

    record TaggedOperation(HttpMethod method, String path, io.swagger.v3.oas.models.Operation operation, List<String> allTags) {}

    private Renderable buildTagTree(OpenAPI openApi, PathNode root) {
        var tagOps = new LinkedHashMap<String, List<TaggedOperation>>();
        collectTaggedOperations(root, "", tagOps);

        // Use declared tag order, then append undeclared tags
        var orderedTags = new LinkedHashSet<String>();
        if (openApi.getTags() != null) {
            for (var t : openApi.getTags()) orderedTags.add(t.getName());
        }
        orderedTags.addAll(tagOps.keySet());

        var uniqueTags = orderedTags.stream().filter(t -> !"Other".equals(t)).count();
        if (uniqueTags == 0) {
            return element("p").classes("no-tags-message")
                    .content("This API doesn't define any tags. Use the path view to browse operations.");
        }
        if (uniqueTags == 1) {
            return element("p").classes("no-tags-message")
                    .content("This API has only one tag. Use the path view to browse operations.");
        }

        var tagTree = tree();
        for (var tagName : orderedTags) {
            var ops = tagOps.get(tagName);
            if (ops == null) continue;
            tagTree.node(tagName, node -> {
                for (var op : ops) {
                    var label = span().content(
                            tag(op.method.name()).is(methodColor(op.method)).classes("method-tag"),
                            span("/" + op.path).classes("tree-segment").style("margin-left:0.5rem")
                    );
                    if (op.allTags.size() > 1) {
                        var otherTags = op.allTags.stream()
                                .filter(t -> !t.equals(tagName))
                                .toList();
                        label.content(span("also in: " + String.join(", ", otherTags)).classes("also-in"));
                    }
                    node.item(label, item -> item
                            .attr("hx-get", op.path + "/" + op.method.name() + ".html")
                            .attr("hx-target", "#detail")
                            .attr("hx-swap", "innerHTML"));
                }
            });
        }
        return tagTree;
    }

    private void collectTaggedOperations(PathNode node, String pathPrefix, Map<String, List<TaggedOperation>> tagOps) {
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
            for (var opEntry : child.operations.entrySet()) {
                var operation = opEntry.getValue();
                var tags = operation.getTags();
                if (tags != null && !tags.isEmpty()) {
                    var taggedOp = new TaggedOperation(opEntry.getKey(), fullPath, operation, List.copyOf(tags));
                    for (var t : tags) {
                        tagOps.computeIfAbsent(t, k -> new ArrayList<>()).add(taggedOp);
                    }
                } else {
                    var taggedOp = new TaggedOperation(opEntry.getKey(), fullPath, operation, List.of());
                    tagOps.computeIfAbsent("Other", k -> new ArrayList<>()).add(taggedOp);
                }
            }
            collectTaggedOperations(child, fullPath, tagOps);
        }
    }

    private void copyWebJarResource(String artifactId, String resourcePath, String outputName) throws IOException {
        var props = new Properties();
        try (var pom = getClass().getResourceAsStream(
                "/META-INF/maven/org.webjars.npm/" + artifactId + "/pom.properties")) {
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
        addNodes(t, root, "");
        return t;
    }

    private void addNodes(TreeContainer tree, PathNode node, String pathPrefix) {
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
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
        return segment.startsWith("{") && segment.endsWith("}") ? "tree-param" : "tree-segment";
    }

    private void generateFragments(PathNode node, String pathPrefix) throws IOException {
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
            for (var opEntry : child.operations.entrySet()) {
                var fragment = buildMethodFragmentContent(opEntry.getKey(), opEntry.getValue(), fullPath);
                var fragmentDir = outputDir.resolve(fullPath);
                Files.createDirectories(fragmentDir);
                Files.writeString(fragmentDir.resolve(opEntry.getKey().name() + ".html"), fragment.render());
            }
            if (!child.operations.isEmpty()) {
                generatePathFragment(child, fullPath);
            }
            generateFragments(child, fullPath);
        }
    }

    private void generatePathFragment(PathNode child, String fullPath) throws IOException {
        var tabList = element("ul");
        var first = true;
        Element firstMethodContent = null;
        for (var opEntry : child.operations.entrySet()) {
            var method = opEntry.getKey();
            var li = element("li");
            if (first) li.classes("is-active");
            li.content(element("a").content(method.name())
                    .attr("tabindex", "0")
                    .attr("hx-get", fullPath + "/" + method.name() + ".html")
                    .attr("hx-target", "#method-content")
                    .attr("hx-swap", "innerHTML"));
            tabList.content(li);
            if (first) {
                firstMethodContent = buildMethodFragmentContent(method, opEntry.getValue(), fullPath);
                first = false;
            }
        }
        var fragment = div().content(
                div().classes("tabs").content(tabList),
                div().id("method-content").content(firstMethodContent));
        var fragmentDir = outputDir.resolve(fullPath);
        Files.createDirectories(fragmentDir);
        Files.writeString(fragmentDir.resolve("index.html"), fragment.render());
    }

    private static Element buildMethodFragmentContent(HttpMethod method, io.swagger.v3.oas.models.Operation operation, String fullPath) {
        var summary = operation.getSummary() != null ? operation.getSummary() : "";
        var headingBadge = tag(method.name()).is(methodColor(method), MEDIUM);
        var headerRow = div().classes("is-flex", "is-align-items-center", "mb-5").style("gap:0.75rem").content(
                headingBadge,
                element("h2").classes("title", "is-4", "mb-0", "endpoint-path")
                        .content("/" + fullPath));
        var hasTags = operation.getTags() != null && !operation.getTags().isEmpty();
        var isDeprecated = Boolean.TRUE.equals(operation.getDeprecated());
        if (hasTags || isDeprecated) {
            var tagsRow = div().classes("tags").style("margin-left:auto");
            if (hasTags) {
                for (var t : operation.getTags()) {
                    tagsRow.content(span(t).classes("tag", "op-tag"));
                }
            }
            if (isDeprecated) {
                tagsRow.content(span("DEPRECATED").classes("tag", "is-warning", "deprecated-badge"));
            }
            headerRow.content(tagsRow);
        }
        var descriptionSpan = span().classes("op-description").content(
                element("strong").content(summary));
        if (operation.getDescription() != null) {
            descriptionSpan.content(" — " + operation.getDescription());
        }
        var descriptionWrapper = div().classes("op-description-wrapper").content(
                descriptionSpan,
                element("button").classes("desc-toggle")
                        .attr("tabindex", "0")
                        .attr("aria-label", "Expand description")
                        .content(span("▸")));
        var fragment = div().content(
                headerRow,
                message().content(messageBody().content(descriptionWrapper))
        );
        if (operation.getExternalDocs() != null) {
            fragment.content(div().classes("external-docs").content(
                    element("a").attr("href", operation.getExternalDocs().getUrl())
                            .attr("target", "_blank")
                            .content("External docs")));
        }
        if (operation.getParameters() != null) {
            for (var param : operation.getParameters()) {
                var schema = param.getSchema();
                var enumValues = (schema != null) ? schema.getEnum() : null;
                var inputField = field(param.getName());
                if (enumValues != null && !enumValues.isEmpty()) {
                    var sel = select(param.getName()).option("", "(any)");
                    for (var value : enumValues) {
                        sel.option(value.toString(), value.toString());
                    }
                    inputField.content(sel);
                } else {
                    inputField.content(input(TEXT).attr("name", param.getName()));
                }
                if (param.getDescription() != null) {
                    inputField.help(param.getDescription());
                }
                fragment.content(inputField);
            }
        }
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            var content = operation.getRequestBody().getContent();
            var jsonContent = content.get("application/json");
            if (jsonContent == null) jsonContent = content.get("*/*");
            if (jsonContent != null && jsonContent.getSchema() != null) {
                var skeleton = generateJsonSkeleton(jsonContent.getSchema());
                if ("{}".equals(skeleton)) skeleton = mediaTypeExample(jsonContent);
                var requestBodyField = field("Request Body (application/json)");
                if (jsonContent.getExamples() != null && jsonContent.getExamples().size() > 1) {
                    var select = element("select")
                            .attr("data-example-select", "true");
                    for (var entry : jsonContent.getExamples().entrySet()) {
                        var value = entry.getValue().getValue();
                        var formatted = (value instanceof com.fasterxml.jackson.databind.JsonNode node)
                                ? node.toPrettyString() : value.toString();
                        var label = entry.getValue().getSummary() != null
                                ? entry.getValue().getSummary() : entry.getKey();
                        select.content(element("option")
                                .attr("value", formatted)
                                .content(label));
                    }
                    requestBodyField.content(
                            div().classes("select", "is-small")
                                    .style("float: right; margin-top: -2rem")
                                    .content(select));
                }
                requestBodyField.content(
                        element("textarea")
                                .attr("data-request-body", "true")
                                .classes("textarea", "is-family-code")
                                .attr("rows", "6")
                                .content(skeleton));
                fragment.content(requestBodyField);
            }
        }
        if (operation.getResponses() != null) {
            var response200 = operation.getResponses().get("200");
            if (response200 != null && response200.getContent() != null) {
                var jsonMedia = response200.getContent().get("application/json");
                if (jsonMedia != null && jsonMedia.getSchema() != null) {
                    @SuppressWarnings("unchecked")
                    var properties = (Map<String, Schema<?>>) jsonMedia.getSchema().getProperties();
                    if (properties != null) {
                        var sb = new StringBuilder();
                        sb.append("{\n");
                        var first = true;
                        for (var propEntry : properties.entrySet()) {
                            if (!first) sb.append(",\n");
                            first = false;
                            sb.append("  \"").append(propEntry.getKey()).append("\": ")
                                    .append(propEntry.getValue().getType());
                        }
                        sb.append("\n}");
                        fragment.content(element("pre").content(code(sb.toString())));
                    }
                }
            }
        }
        var sendRow = level().content(
                div().classes("level-left").content(
                        button("Send").is(PRIMARY)
                                .attr("data-path", "/" + fullPath)
                                .attr("data-method", method.name())),
                div().classes("level-right"));
        fragment.content(sendRow);
        return fragment;
    }

    private List<String> splitSegments(String path) {
        var stripped = path.startsWith("/") ? path.substring(1) : path;
        return List.of(stripped.split("/"));
    }

    @SuppressWarnings("rawtypes")
    private static String generateJsonSkeleton(Schema<?> schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) return "{}";
        var sb = new StringBuilder("{\n");
        var first = true;
        for (var entry : properties.entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("  \"").append(entry.getKey()).append("\": ");
            sb.append(sampleValue(entry.getValue()));
        }
        sb.append("\n}");
        return sb.toString();
    }

    private static String sampleValue(Schema<?> schema) {
        if (schema.getExample() != null) {
            return formatSampleValue(schema.getType(), schema.getExample());
        }
        if (schema.getDefault() != null) {
            return formatSampleValue(schema.getType(), schema.getDefault());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return formatSampleValue(schema.getType(), schema.getEnum().getFirst());
        }
        if (schema.getFormat() != null) {
            var formatted = formatBasedSample(schema.getFormat());
            if (formatted != null) return "\"" + formatted + "\"";
        }
        var type = schema.getType();
        if (type == null) return "null";
        return switch (type) {
            case "string" -> "\"\"";
            case "integer", "number" -> "0";
            case "boolean" -> "false";
            case "array" -> "[]";
            case "object" -> "{}";
            default -> "null";
        };
    }

    private static String mediaTypeExample(io.swagger.v3.oas.models.media.MediaType mediaType) {
        var example = mediaType.getExample();
        if (example == null && mediaType.getExamples() != null)
            example = mediaType.getExamples().values().iterator().next().getValue();
        if (example == null) return "{}";
        if (example instanceof com.fasterxml.jackson.databind.JsonNode node)
            return node.toPrettyString();
        return example.toString();
    }

    private static String formatSampleValue(String type, Object value) {
        var quote = type == null || "string".equals(type);
        return quote ? "\"" + value + "\"" : value.toString();
    }

    private static String formatBasedSample(String format) {
        return switch (format) {
            case "date" -> "2024-01-15";
            case "date-time" -> "2024-01-15T12:00:00Z";
            case "email" -> "user@example.com";
            case "uri", "url" -> "https://example.com";
            case "uuid" -> "3fa85f64-5717-4562-b3fc-2c963f66afa6";
            default -> null;
        };
    }

    private static Consumer<Element> hxLoad(String snippet) {
        return o -> o.attr("hx-get", snippet).attr("hx-target", "#tree-container").attr("hx-swap", "innerHTML");
    }

    private static Color methodColor(PathItem.HttpMethod method) {
        return switch (method) {
            case GET -> SUCCESS;
            case POST -> LINK;
            case PUT -> WARNING;
            case DELETE -> DANGER;
            case PATCH -> PRIMARY;
            default -> INFO;
        };
    }

    private static final String APP_CSS = """
            .section {
                padding-top: 1.5rem;
                min-height: 100vh;
            }
            .tags.has-addons {
                margin-left: auto;
                margin-right: 4px;
                margin-bottom: 0;
                flex-wrap: nowrap;
                flex-shrink: 0;
                gap: 0;
            }
            .tags.has-addons .tag {
                font-size: 0.65rem;
                padding: 2px 6px;
                height: auto;
                margin-bottom: 0;
            }
            .split-first {
                background-color: var(--bulma-scheme-main-bis);
                padding: 1.25rem 0 1.25rem 1.25rem;
                display: flex;
                flex-direction: column;
                min-width: 0;
            }
            .split-first > .box {
                flex: 1;
                min-width: 0;
                overflow: hidden;
                border-radius: 6px 0 0 6px;
            }
            @media screen and (min-width: 1024px) {
                .split-first {
                    min-height: calc(100vh - 4rem);
                }
                .split-second {
                    min-height: calc(100vh - 4rem);
                }
            }
            .tabs a:focus {
                outline: none;
                box-shadow: inset 0 0 0 2px var(--bulma-link);
            }
            /* --- Detail pane --- */
            .detail-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                flex-wrap: wrap;
                gap: 0.75rem;
                margin-bottom: 1.25rem;
                padding-bottom: 0.75rem;
                border-bottom: 2px solid var(--bulma-border);
            }
            .detail-header .title {
                margin-bottom: 0;
            }
            .split-second {
                padding-left: 2rem;
                padding-right: 2rem;
                background-color: var(--bulma-scheme-main-bis);
            }
            #detail .endpoint-path {
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-weight: 500;
                color: var(--bulma-text-strong);
            }
            .op-description-wrapper {
                position: relative;
                margin-bottom: 1rem;
            }
            #detail .op-description {
                color: var(--bulma-text-weak);
                line-height: 1.6;
                display: -webkit-box;
                -webkit-box-orient: vertical;
                -webkit-line-clamp: 3;
                overflow: hidden;
            }
            #detail .op-description-wrapper.is-expanded .op-description {
                display: inline;
                overflow: visible;
            }
            .desc-toggle {
                border: none;
                background: linear-gradient(90deg, transparent, var(--bulma-scheme-main-bis) 30%);
                cursor: pointer;
                padding: 0 0 0 1.5rem;
                color: var(--bulma-text-weak);
                font-size: 0.85rem;
                line-height: 1.6;
                display: none;
                position: absolute;
                right: 0;
                bottom: 0;
            }
            .desc-toggle:focus-visible {
                outline: 2px solid var(--bulma-link);
                outline-offset: 2px;
                border-radius: 2px;
            }
            .op-description-wrapper.is-clamped .desc-toggle {
                display: inline;
            }
            .op-description-wrapper.is-expanded .desc-toggle {
                display: inline;
                position: static;
                background: none;
                padding: 0 0 0 0.25rem;
            }
            .deprecated-badge {
                font-weight: 600;
            }
            #detail .external-docs a {
                color: var(--bulma-link);
                text-decoration: underline;
            }
            #detail pre {
                border: 1px solid var(--bulma-border);
                border-radius: 6px;
                padding: 1rem 1.25rem;
                background-color: var(--bulma-scheme-main);
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-size: 0.875rem;
                margin-top: 1rem;
                margin-bottom: 1rem;
            }
            #detail .field {
                margin-bottom: 1rem;
            }
            #detail textarea[data-request-body] {
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-size: 0.875rem;
                resize: vertical;
            }
            #detail button[data-path] {
                margin-top: 0.75rem;
            }
            @keyframes bump-vertical {
                0%, 100% { transform: translateY(0); }
                25% { transform: translateY(-2px); }
                75% { transform: translateY(2px); }
            }
            @keyframes bump-horizontal {
                0%, 100% { transform: translateX(0); }
                25% { transform: translateX(-2px); }
                75% { transform: translateX(2px); }
            }
            .bump-v { animation: bump-vertical 0.2s ease; }
            .bump-h { animation: bump-horizontal 0.2s ease; }
            .method-tag {
                min-width: 4.5rem;
                justify-content: center;
            }
            .also-in {
                font-size: 0.7rem;
                color: var(--bulma-text-weak);
                margin-left: 1.75rem;
                font-style: italic;
            }
            .no-tags-message {
                padding: 1.5rem;
                color: var(--bulma-text-weak);
                text-align: center;
            }
            [data-toggle="view"] {
                margin-bottom: 0.75rem;
            }
            .response-status {
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-weight: 600;
                font-size: 0.85rem;
            }
            .response-status.is-success {
                color: var(--bulma-success);
            }
            .response-status.is-error {
                color: var(--bulma-danger);
            }
            .response-no-body {
                color: var(--bulma-text-weak);
                font-style: italic;
                margin-top: 0.75rem;
            }
            """;

    private static final String APP_JS = """
            document.addEventListener('DOMContentLoaded', function() {
                var detail = document.getElementById('detail');
            
                // Mode toggle consumer
                var modeContainer = document.querySelector('[data-toggle="mode"]');
                if (modeContainer) {
                    modeContainer.addEventListener('toggle', function(e) {
                        modeContainer.setAttribute('data-mode', e.detail.value);
                        var sendBtns = document.querySelectorAll('#detail button[data-path]');
                        sendBtns.forEach(function(b) { b.textContent = e.detail.value === 'try' ? 'Send' : 'Copy'; });
                    });
                    modeContainer.addEventListener('keydown', function(e) {
                        if (e.key === 'ArrowDown') {
                            e.preventDefault();
                            var viewToggle = document.querySelector('[data-toggle="view"]');
                            if (viewToggle) viewToggle.focus();
                        }
                    });
                    document.addEventListener('keydown', function(e) {
                        if (e.key >= '1' && e.key <= '3') {
                            var tag = document.activeElement.tagName;
                            if (tag === 'INPUT' || tag === 'TEXTAREA') return;
                            if (document.activeElement.isContentEditable) return;
                            e.preventDefault();
                            var values = Array.from(modeContainer.querySelectorAll('[data-toggle-value]')).map(function(el) {
                                return el.getAttribute('data-toggle-value');
                            });
                            modeContainer._select(values[parseInt(e.key) - 1]);
                        }
                    });
                }

                // View toggle consumer
                var viewToggle = document.querySelector('[data-toggle="view"]');
                if (viewToggle) {
                    viewToggle.addEventListener('toggle', function(e) {
                        var btn = viewToggle.querySelector('[data-toggle-value=' + e.detail.value + ']');
                        htmx.ajax('GET', btn.getAttribute('hx-get'), {target: '#tree-container', swap: 'innerHTML'}).then(function() {
                            viewToggle.focus();
                        });
                    });
                    viewToggle.addEventListener('keydown', function(e) {
                        if (e.key === 'ArrowUp') {
                            e.preventDefault();
                            var modeToggle = document.querySelector('[data-toggle="mode"]');
                            if (modeToggle) modeToggle.focus();
                        } else if (e.key === 'ArrowDown') {
                            e.preventDefault();
                            var tree = document.querySelector('[role="tree"]');
                            if (tree) tree.focus();
                        } else if (e.key === 'Tab') {
                            e.preventDefault();
                            if (e.shiftKey) {
                                var modeToggle = document.querySelector('[data-toggle="mode"]');
                                if (modeToggle) modeToggle.focus();
                            } else {
                                var tree = document.querySelector('[role="tree"]');
                                if (tree) tree.focus();
                            }
                        }
                    });
                }

                // Restore persisted toggle state (after consumers registered)
                document.querySelectorAll('.segmented-control[data-persist]').forEach(function(container) {
                    var saved = localStorage.getItem(container.getAttribute('data-persist'));
                    if (saved && saved !== container.querySelector('.is-active').getAttribute('data-toggle-value')) {
                        container._select(saved);
                    }
                });

                // Tab switching — toggle is-active when HTMX swaps method content
                document.body.addEventListener('htmx:afterRequest', function(e) {
                    var tabLink = e.detail.elt;
                    if (tabLink && tabLink.closest && tabLink.closest('.tabs')) {
                        var tabs = tabLink.closest('.tabs');
                        tabs.querySelectorAll('li').forEach(function(li) { li.classList.remove('is-active'); });
                        tabLink.closest('li').classList.add('is-active');
                    }
                });

                function initDescriptionToggle() {
                    document.querySelectorAll('.op-description-wrapper').forEach(function(wrapper) {
                        var desc = wrapper.querySelector('.op-description');
                        var toggle = wrapper.querySelector('.desc-toggle');
                        if (!desc || !toggle) return;
                        if (desc.scrollHeight > desc.clientHeight) {
                            wrapper.classList.add('is-clamped');
                        } else {
                            wrapper.classList.remove('is-clamped');
                        }
                        toggle.onclick = function() {
                            var expanded = wrapper.classList.toggle('is-expanded');
                            wrapper.classList.toggle('is-clamped', !expanded);
                            toggle.querySelector('span').textContent = expanded ? '▾' : '▸';
                            toggle.setAttribute('aria-label', expanded ? 'Collapse description' : 'Expand description');
                        };
                    });
                }

                document.body.addEventListener('htmx:afterSettle', function(e) {
                    if (e.detail.target && e.detail.target.id === 'tree-container' && viewToggle) {
                        viewToggle.focus();
                    }
                });
                document.body.addEventListener('htmx:afterSwap', function(e) {
                    var currentMode = modeContainer ? modeContainer.getAttribute('data-mode') : 'try';
                    if (currentMode !== 'try') {
                        var sendBtns = document.querySelectorAll('#detail button[data-path]');
                        sendBtns.forEach(function(b) { b.textContent = 'Copy'; });
                    }
                    initDescriptionToggle();
                    document.querySelectorAll('select[data-example-select]').forEach(function(sel) {
                        sel.addEventListener('change', function() {
                            var textarea = sel.closest('.field').querySelector('textarea[data-request-body]');
                            if (textarea) textarea.value = sel.value;
                        });
                    });
                    // After detail content swaps, check if a specific method tab should be activated
                    var trigger = e.detail.elt;
                    if (trigger && trigger.getAttribute && trigger.getAttribute('data-method')) {
                        var method = trigger.getAttribute('data-method');
                        var tabLinks = document.querySelectorAll('.tabs li a');
                        tabLinks.forEach(function(a) {
                            if (a.textContent.trim() === method) {
                                a.click();
                            }
                        });
                    }
                });
            
                function clearPreviousResponse() {
                    var existing = detail.querySelector('pre.response');
                    if (existing) existing.remove();
                    var noBody = detail.querySelector('.response-no-body');
                    if (noBody) noBody.remove();
                }

                function showResponseStatus(btn, status, statusText) {
                    var levelRight = btn.closest('.level').querySelector('.level-right');
                    levelRight.textContent = '';
                    var badge = document.createElement('span');
                    badge.className = 'response-status';
                    badge.textContent = status + ' ' + statusText;
                    if (status >= 200 && status < 300) badge.classList.add('is-success');
                    else badge.classList.add('is-error');
                    levelRight.appendChild(badge);
                }

                function showCopied(btn) {
                    var original = btn.textContent;
                    btn.textContent = 'Copied!';
                    setTimeout(function() { btn.textContent = original; }, 1500);
                }
            
                // Tab keyboard navigation
                document.addEventListener('keydown', function(e) {
                    var focused = document.activeElement;
                    if (!focused || !focused.closest || !focused.closest('.tabs')) return;
                    if (['ArrowRight','ArrowLeft','ArrowDown','ArrowUp','Enter','Escape'].indexOf(e.key) < 0) return;
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    function activateTab(li) {
                        var tabs = li.closest('.tabs');
                        tabs.querySelectorAll('li').forEach(function(l) { l.classList.remove('is-active'); });
                        li.classList.add('is-active');
                        var link = li.querySelector('a');
                        link.focus();
                        htmx.ajax('GET', link.getAttribute('hx-get'), link.getAttribute('hx-target'));
                    }
                    if (e.key === 'ArrowRight') {
                        var nextLi = focused.closest('li').nextElementSibling;
                        if (nextLi) activateTab(nextLi);
                        else bump(focused, 'h');
                    } else if (e.key === 'ArrowUp') {
                        bump(focused, 'v');
                    } else if (e.key === 'ArrowLeft') {
                        var prevLi = focused.closest('li').previousElementSibling;
                        if (prevLi) {
                            activateTab(prevLi);
                        } else {
                            document.querySelector('[role="tree"]').focus();
                        }
                    } else if (e.key === 'ArrowDown' || e.key === 'Enter') {
                        var firstInput = document.querySelector('#method-content input, #method-content select, #method-content textarea, #method-content button[data-path]');
                        if (firstInput) firstInput.focus();
                    } else if (e.key === 'Escape') {
                        document.querySelector('[role="tree"]').focus();
                    }
                }, true);

                // Description toggle arrow key navigation
                document.addEventListener('keydown', function(e) {
                    if (!document.activeElement.classList.contains('desc-toggle')) return;
                    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    var all = Array.from(document.querySelectorAll('[tabindex], a, button, input, textarea, select'));
                    all = all.filter(function(el) { return el.tabIndex >= 0 && el.offsetParent !== null; });
                    var idx = all.indexOf(document.activeElement);
                    var next = e.key === 'ArrowDown' ? all[idx + 1] : all[idx - 1];
                    if (next) next.focus();
                }, true);

                // Field navigation within method content
                document.addEventListener('keydown', function(e) {
                    var mc = document.getElementById('method-content') || document.getElementById('detail');
                    if (!mc) return;
                    var focusables = Array.from(mc.querySelectorAll('input, select, textarea, button[data-path]'));
                    var idx = focusables.indexOf(document.activeElement);
                    if (idx < 0) return;

                    var el = document.activeElement;
                    if (el.tagName === 'TEXTAREA' && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
                        var val = el.value;
                        var pos = el.selectionStart;
                        if (e.key === 'ArrowDown') {
                            var atLastLine = val.indexOf('\\n', pos) < 0;
                            if (!atLastLine) return;
                        } else {
                            var atFirstLine = val.lastIndexOf('\\n', pos - 1) < 0;
                            if (!atFirstLine) return;
                        }
                    }
                    var handled = true;
                    if (e.key === 'ArrowDown') {
                        if (idx < focusables.length - 1) focusables[idx + 1].focus();
                        else bump(focusables[idx], 'v');
                    } else if (e.key === 'ArrowUp') {
                        if (idx > 0) {
                            focusables[idx - 1].focus();
                        } else {
                            var activeTabLink = document.querySelector('.tabs .is-active a');
                            if (activeTabLink) activeTabLink.focus();
                            else {
                                var tree = document.querySelector('[role="tree"]');
                                if (tree) tree.focus();
                            }
                        }
                    } else if (e.key === 'Enter') {
                        var sendBtn = mc.querySelector('button[data-path]');
                        if (sendBtn) sendBtn.click();
                    } else if (e.key === 'Escape') {
                        document.querySelector('[role="tree"]').focus();
                    } else {
                        handled = false;
                    }
                    if (handled) { e.preventDefault(); e.stopPropagation(); }
                }, true);

                // Auto-load the first operation
                var firstHxEl = document.querySelector('#tree-container [hx-get]');
                if (firstHxEl) htmx.ajax('GET', firstHxEl.getAttribute('hx-get'), '#detail');
            
                // Send button handler (delegated from detail pane)
                if (detail) {
                    detail.addEventListener('click', function(e) {
                        var sendBtn = e.target.closest('button[data-path]');
                        if (!sendBtn) return;
            
                        var pathTemplate = sendBtn.getAttribute('data-path');
                        var method = sendBtn.getAttribute('data-method');
                        var modeEl = document.querySelector('[data-toggle="mode"]');
                        var mode = modeEl ? modeEl.getAttribute('data-mode') : 'try';
                        var baseUrl = modeEl ? (modeEl.getAttribute('data-base-url') || '') : '';
            
                        // Collect input values
                        var inputs = detail.querySelectorAll('input[name], select[name]');
                        var resolvedPath = pathTemplate;
                        var queryParams = [];
                        inputs.forEach(function(inp) {
                            var name = inp.getAttribute('name');
                            var val = inp.value;
                            if (pathTemplate.includes('{' + name + '}')) {
                                resolvedPath = resolvedPath.replace('{' + name + '}', encodeURIComponent(val));
                            } else if (val) {
                                queryParams.push(name + '=' + encodeURIComponent(val));
                            }
                        });
                        var url = baseUrl.startsWith('http') ? baseUrl + resolvedPath
                                : new URL((baseUrl + resolvedPath).replace(/\\/+/g, '/'), window.location.origin).href;
                        if (queryParams.length > 0) url += '?' + queryParams.join('&');
            
                        var bodyTextarea = detail.querySelector('textarea[data-request-body]');
                        var bodyValue = bodyTextarea ? bodyTextarea.value : '';
            
                        if (mode === 'curl') {
                            var cmd = bodyValue
                                    ? 'curl -X ' + method + " -H 'Content-Type: application/json' -d '" + bodyValue + "' " + url
                                    : 'curl -X ' + method + ' ' + url;
                            navigator.clipboard.writeText(cmd);
                            showCopied(sendBtn);
                        } else if (mode === 'httpie') {
                            var cmd = bodyValue
                                    ? "echo '" + bodyValue + "' | http " + method + ' ' + url + " Content-Type:application/json"
                                    : 'http ' + method + ' ' + url;
                            navigator.clipboard.writeText(cmd);
                            showCopied(sendBtn);
                        } else if (mode === 'try') {
                            sendBtn.disabled = true;
                            sendBtn.textContent = 'Sending...';
                            var fetchOptions = { method: method };
                            if (bodyValue) {
                                fetchOptions.body = bodyValue;
                                fetchOptions.headers = { 'Content-Type': 'application/json' };
                            }
                            fetch(url, fetchOptions).then(function(resp) {
                                var ct = resp.headers.get('Content-Type') || '';
                                return resp.text().then(function(text) {
                                    showResponseStatus(sendBtn, resp.status, resp.statusText);
                                    if (ct.includes('json')) {
                                        try { text = JSON.stringify(JSON.parse(text), null, 2); } catch(e) {}
                                    }
                                    clearPreviousResponse();
                                    if (text.trim()) {
                                        var pre = document.createElement('pre');
                                        pre.textContent = text;
                                        pre.className = 'response';
                                        detail.appendChild(pre);
                                    } else {
                                        var msg = document.createElement('p');
                                        msg.textContent = 'no body';
                                        msg.className = 'response-no-body';
                                        detail.appendChild(msg);
                                    }
                                });
                            }).catch(function(err) {
                                showResponseStatus(sendBtn, 0, 'Network error');
                                clearPreviousResponse();
                                var pre = document.createElement('pre');
                                pre.textContent = err.message;
                                pre.className = 'response';
                                detail.appendChild(pre);
                            }).finally(function() {
                                sendBtn.disabled = false;
                                sendBtn.textContent = 'Send';
                                sendBtn.focus();
                            });
                        }
                    });
                }
                // htmx error retry with banner
                var banner = document.getElementById('error-banner');
                var activeRetries = 0;
                function showBanner() {
                    activeRetries++;
                    banner.style.display = '';
                }
                function hideBannerIfDone() {
                    activeRetries--;
                    if (activeRetries <= 0) {
                        activeRetries = 0;
                        banner.style.display = 'none';
                    }
                }
                function retryHtmx(url, targetEl) {
                    showBanner();
                    var interval = setInterval(function() {
                        fetch(url).then(function(resp) {
                            if (!resp.ok) return;
                            clearInterval(interval);
                            return resp.text().then(function(html) {
                                if (targetEl) targetEl.innerHTML = html;
                                hideBannerIfDone();
                                if (typeof htmx !== 'undefined') htmx.process(targetEl);
                            });
                        }).catch(function() {});
                    }, 1000);
                }
                function htmxErrorUrl(e) {
                    return (e.detail.pathInfo && e.detail.pathInfo.requestPath)
                        || (e.detail.elt && e.detail.elt.getAttribute('hx-get'));
                }
                document.body.addEventListener('htmx:sendError', function(e) {
                    var url = htmxErrorUrl(e);
                    if (!url) return;
                    retryHtmx(url, e.detail.target || document.getElementById('detail'));
                });
                document.body.addEventListener('htmx:responseError', function(e) {
                    var url = htmxErrorUrl(e);
                    if (!url) return;
                    retryHtmx(url, e.detail.target || document.getElementById('detail'));
                });
            });
            """;

    private static class PathNode {
        final Map<String, PathNode> children = new LinkedHashMap<>();
        final Map<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> operations = new LinkedHashMap<>();

        void add(List<String> segments, int index, PathItem pathItem) {
            if (index >= segments.size()) {
                operations.putAll(pathItem.readOperationsMap());
                return;
            }
            var segment = segments.get(index);
            children.computeIfAbsent(segment, k -> new PathNode())
                    .add(segments, index + 1, pathItem);
        }
    }
}
