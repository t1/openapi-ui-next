package com.github.t1.openapi.ui;

import com.github.t1.bulmajava.basic.Color;
import com.github.t1.htmljava.Element;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.github.t1.bulmajava.basic.Color.*;
import static com.github.t1.bulmajava.basic.Size.MEDIUM;
import static com.github.t1.bulmajava.columns.Column.column;
import static com.github.t1.bulmajava.columns.Columns.columns;
import static com.github.t1.bulmajava.elements.Box.box;
import static com.github.t1.bulmajava.elements.Button.button;
import static com.github.t1.bulmajava.elements.Tag.tag;
import static com.github.t1.bulmajava.form.Field.field;
import static com.github.t1.bulmajava.form.Input.input;
import static com.github.t1.bulmajava.form.InputType.TEXT;
import static com.github.t1.bulmajava.layout.Container.container;
import static com.github.t1.bulmajava.layout.Section.section;
import static com.github.t1.htmljava.Html.html;
import static com.github.t1.htmljava.HtmlBasics.*;
import static com.github.t1.openapi.ui.Tree.tree;

public class OpenApiUiGenerator {
    private final Path specFile;
    private final Path outputDir;

    public OpenApiUiGenerator(Path specFile, Path outputDir) {
        this.specFile = specFile;
        this.outputDir = outputDir;
    }

    public void generate() throws IOException {
        var openApi = new OpenAPIV3Parser().read(specFile.toString());

        var root = new PathNode();
        for (var pathEntry : openApi.getPaths().entrySet()) {
            var pathString = pathEntry.getKey();
            var pathItem = pathEntry.getValue();
            var segments = splitSegments(pathString);
            root.add(segments, 0, pathItem);
        }

        var list = buildTree(root);

        var servers = openApi.getServers();
        var baseUrl = (servers != null && !servers.isEmpty()) ? servers.get(0).getUrl() : "/";

        var modeToggle = div().attr("data-mode", "try").attr("data-base-url", baseUrl)
                .classes("buttons", "has-addons").content(
                        button("Try").is(PRIMARY).classes("is-selected").attr("data-mode-btn", "try"),
                        button("httpie").attr("data-mode-btn", "httpie"),
                        button("curl").attr("data-mode-btn", "curl")
                );

        var pageTitle = openApi.getInfo().getTitle();
        var detail = box().id("detail").attr("tabindex", "0");
        var detailHeader = div().classes("detail-header").content(
                element("h1").classes("title").content(pageTitle),
                modeToggle
        );
        var body = section().content(container().content(
                detailHeader,
                columns().classes("is-desktop").content(
                        column().classes("is-one-third").content(list),
                        column().classes("detail-column").content(detail)
                )
        ));
        var page = html(pageTitle)
                .stylesheet("bulma.min.css")
                .stylesheet("openapi-ui.css")
                .script("htmx.min.js")
                .javaScriptCode(Tree.js())
                .javaScriptCode(APP_JS)
                .body(body);

        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("index.html"), page.render());
        Files.writeString(outputDir.resolve("openapi-ui.css"), Tree.css() + APP_CSS);

        generateFragments(root, "");

        copyWebJarResource("bulma", "css/bulma.min.css", "bulma.min.css");
        copyWebJarResource("htmx.org", "dist/htmx.min.js", "htmx.min.js");
    }

    private void copyWebJarResource(String artifactId, String resourcePath, String outputName) throws IOException {
        var props = new Properties();
        try (var pom = getClass().getResourceAsStream(
                "/META-INF/maven/org.webjars.npm/" + artifactId + "/pom.properties")) {
            props.load(pom);
        }
        var version = props.getProperty("version");
        try (var resource = getClass().getResourceAsStream(
                "/META-INF/resources/webjars/" + artifactId + "/" + version + "/" + resourcePath)) {
            Files.copy(resource, outputDir.resolve(outputName), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Tree buildTree(PathNode root) {
        var t = tree();
        addNodes(t, root, "");
        return t;
    }

    private void addNodes(Tree tree, PathNode node, String pathPrefix) {
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
            if (!child.children.isEmpty()) {
                tree.node(span(segment).classes(segmentClass(segment)), sub -> {
                    addNodeOperations(sub, child, fullPath);
                    addNodes(sub, child, fullPath);
                });
            } else {
                tree.item(span(segment).classes(segmentClass(segment)), item ->
                        addLeafOperations(item, child, fullPath));
            }
        }
    }

    private void addNodes(Tree.Node node, PathNode pathNode, String pathPrefix) {
        for (var entry : pathNode.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
            if (!child.children.isEmpty()) {
                node.node(span(segment).classes(segmentClass(segment)), sub -> {
                    addNodeOperations(sub, child, fullPath);
                    addNodes(sub, child, fullPath);
                });
            } else {
                node.item(span(segment).classes(segmentClass(segment)), item ->
                        addLeafOperations(item, child, fullPath));
            }
        }
    }

    private static String segmentClass(String segment) {
        return segment.startsWith("{") && segment.endsWith("}") ? "tree-param" : "tree-segment";
    }

    private void addNodeOperations(Tree.Node node, PathNode child, String fullPath) {
        for (var opEntry : child.operations.entrySet()) {
            node.content(operationLabel(opEntry.getKey(), opEntry.getValue(), fullPath));
        }
    }

    private void addLeafOperations(Element item, PathNode child, String fullPath) {
        for (var opEntry : child.operations.entrySet()) {
            item.content(operationLabel(opEntry.getKey(), opEntry.getValue(), fullPath));
        }
    }

    private Element operationLabel(HttpMethod method, Operation operation, String fullPath) {
        var badge = tag(method.name()).is(methodColor(method));
        var label = span().classes("tree-op-label").content(badge);
        if (operation.getSummary() != null) {
            label.content(span(" — " + operation.getSummary()).classes("tree-op-summary"));
        }
        return label
                .attr("hx-get", fullPath + "/" + method.name() + ".html")
                .attr("hx-target", "#detail")
                .attr("hx-swap", "innerHTML");
    }

    private void generateFragments(PathNode node, String pathPrefix) throws IOException {
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
            for (var opEntry : child.operations.entrySet()) {
                var method = opEntry.getKey();
                var operation = opEntry.getValue();
                var summary = operation.getSummary() != null ? operation.getSummary() : "";
                var headingBadge = tag(method.name()).is(methodColor(method), MEDIUM);
                var fragment = div().content(
                        div().classes("is-flex", "is-align-items-center", "mb-5").style("gap:0.75rem").content(
                                headingBadge,
                                element("h2").classes("title", "is-4", "mb-0", "endpoint-path")
                                        .content("/" + fullPath)),
                        p(summary).classes("op-summary")
                );
                if (operation.getParameters() != null) {
                    for (var param : operation.getParameters()) {
                        var inputField = field(param.getName())
                                .content(input(TEXT).attr("name", param.getName()));
                        if (param.getDescription() != null) {
                            inputField.help(param.getDescription());
                        }
                        fragment.content(inputField);
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
                fragment.content(button("Send").is(PRIMARY)
                        .attr("data-path", "/" + fullPath)
                        .attr("data-method", method.name()));
                var fragmentDir = outputDir.resolve(fullPath);
                Files.createDirectories(fragmentDir);
                Files.writeString(fragmentDir.resolve(method.name() + ".html"), fragment.render());
            }
            generateFragments(child, fullPath);
        }
    }

    private List<String> splitSegments(String path) {
        var stripped = path.startsWith("/") ? path.substring(1) : path;
        return List.of(stripped.split("/"));
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
            .tree-op-label {
                color: var(--bulma-text-weak);
                font-size: 0.85rem;
            }
            .tree-op-summary {
                display: none;
            }
            [aria-selected="true"] > .tree-op-label > .tree-op-summary {
                display: inline;
            }
            .columns.is-desktop > .column.is-one-third {
                background-color: var(--bulma-scheme-main-bis);
                border-right: 1px solid var(--bulma-border);
                padding: 1.25rem 1.5rem;
            }
            @media screen and (min-width: 1024px) {
                .columns.is-desktop > .column.is-one-third {
                    min-height: calc(100vh - 4rem);
                }
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
            .detail-column {
                padding-left: 2rem;
            }
            #detail .endpoint-path {
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-weight: 500;
                color: var(--bulma-text-strong);
            }
            #detail .op-summary {
                color: var(--bulma-text-weak);
                margin-bottom: 1.25rem;
            }
            #detail pre {
                border: 1px solid var(--bulma-border);
                border-radius: 6px;
                padding: 1rem 1.25rem;
                background-color: var(--bulma-scheme-main-bis);
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-size: 0.875rem;
                margin-top: 1rem;
                margin-bottom: 1rem;
            }
            #detail .field {
                margin-bottom: 1rem;
            }
            #detail button[data-path] {
                margin-top: 0.75rem;
            }
            """;

    private static final String APP_JS = """
            document.addEventListener('DOMContentLoaded', function() {
                var detail = document.getElementById('detail');

                // Mode toggle
                var modeContainer = document.querySelector('[data-mode]');
                if (modeContainer) {
                    modeContainer.querySelectorAll('[data-mode-btn]').forEach(function(btn) {
                        btn.addEventListener('click', function() {
                            modeContainer.setAttribute('data-mode', btn.getAttribute('data-mode-btn'));
                            modeContainer.querySelectorAll('[data-mode-btn]').forEach(function(b) {
                                b.classList.remove('is-selected', 'is-primary');
                            });
                            btn.classList.add('is-selected', 'is-primary');
                            var newMode = btn.getAttribute('data-mode-btn');
                            var sendBtns = document.querySelectorAll('#detail button[data-path]');
                            sendBtns.forEach(function(b) { b.textContent = newMode === 'try' ? 'Send' : 'Copy'; });
                        });
                    });
                }

                document.body.addEventListener('htmx:afterSwap', function() {
                    var currentMode = modeContainer ? modeContainer.getAttribute('data-mode') : 'try';
                    if (currentMode !== 'try') {
                        var sendBtns = document.querySelectorAll('#detail button[data-path]');
                        sendBtns.forEach(function(b) { b.textContent = 'Copy'; });
                    }
                });

                function showCopied(btn) {
                    var original = btn.textContent;
                    btn.textContent = 'Copied!';
                    setTimeout(function() { btn.textContent = original; }, 1500);
                }

                // Auto-load the first operation
                var firstHxEl = document.querySelector('[hx-get]');
                if (firstHxEl) htmx.ajax('GET', firstHxEl.getAttribute('hx-get'), '#detail');

                // Send button handler (delegated from detail pane)
                if (detail) {
                    detail.addEventListener('click', function(e) {
                        var sendBtn = e.target.closest('button[data-path]');
                        if (!sendBtn) return;

                        var pathTemplate = sendBtn.getAttribute('data-path');
                        var method = sendBtn.getAttribute('data-method');
                        var modeEl = document.querySelector('[data-mode]');
                        var mode = modeEl ? modeEl.getAttribute('data-mode') : 'try';
                        var baseUrl = modeEl ? (modeEl.getAttribute('data-base-url') || '') : '';

                        // Collect input values
                        var inputs = detail.querySelectorAll('input[name]');
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

                        if (mode === 'curl') {
                            navigator.clipboard.writeText('curl ' + url);
                            showCopied(sendBtn);
                        } else if (mode === 'httpie') {
                            navigator.clipboard.writeText('http ' + method + ' ' + url);
                            showCopied(sendBtn);
                        } else if (mode === 'try') {
                            sendBtn.disabled = true;
                            sendBtn.textContent = 'Sending...';
                            fetch(url).then(function(resp) {
                                var ct = resp.headers.get('Content-Type') || '';
                                return resp.text().then(function(text) {
                                    var pre = document.createElement('pre');
                                    if (!resp.ok) {
                                        text = resp.status + ' ' + resp.statusText + '\\n' + text;
                                    } else if (ct.includes('json')) {
                                        try { text = JSON.stringify(JSON.parse(text), null, 2); } catch(e) {}
                                    }
                                    pre.textContent = text;
                                    var existing = detail.querySelector('pre.response');
                                    if (existing) existing.remove();
                                    pre.className = 'response';
                                    detail.appendChild(pre);
                                    sendBtn.disabled = false;
                                    sendBtn.textContent = 'Send';
                                });
                            }).catch(function(err) {
                                var pre = document.createElement('pre');
                                pre.textContent = 'Network error: ' + err.message;
                                var existing = detail.querySelector('pre.response');
                                if (existing) existing.remove();
                                pre.className = 'response';
                                detail.appendChild(pre);
                                sendBtn.disabled = false;
                                sendBtn.textContent = 'Send';
                            });
                        }
                    });
                }
            });
            """;

    private static class PathNode {
        final Map<String, PathNode> children = new LinkedHashMap<>();
        final Map<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> operations = new LinkedHashMap<>();

        void add(List<String> segments, int index, PathItem pathItem) {
            if (index >= segments.size()) {
                for (var opEntry : pathItem.readOperationsMap().entrySet()) {
                    operations.put(opEntry.getKey(), opEntry.getValue());
                }
                return;
            }
            var segment = segments.get(index);
            children.computeIfAbsent(segment, k -> new PathNode())
                    .add(segments, index + 1, pathItem);
        }
    }
}
