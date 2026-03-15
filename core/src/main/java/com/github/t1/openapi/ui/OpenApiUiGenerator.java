package com.github.t1.openapi.ui;

import com.github.t1.bulmajava.basic.Color;
import com.github.t1.htmljava.Element;
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

import static com.github.t1.bulmajava.basic.Color.DANGER;
import static com.github.t1.bulmajava.basic.Color.INFO;
import static com.github.t1.bulmajava.basic.Color.LINK;
import static com.github.t1.bulmajava.basic.Color.PRIMARY;
import static com.github.t1.bulmajava.basic.Color.SUCCESS;
import static com.github.t1.bulmajava.basic.Color.WARNING;
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
import static com.github.t1.htmljava.HtmlBasics.code;
import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.htmljava.HtmlBasics.p;
import static com.github.t1.htmljava.HtmlBasics.span;
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
        var baseUrl = (servers != null && !servers.isEmpty()) ? servers.getFirst().getUrl() : "/";

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

    private void addNodes(Tree tree, PathNode node, String pathPrefix) {
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
            var label = span().content(span(segment).classes(segmentClass(segment)));
            for (var method : child.operations.keySet()) {
                label.content(methodAddon(method));
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

    private void addNodes(Tree.Node treeNode, PathNode pathNode, String pathPrefix) {
        for (var entry : pathNode.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
            var label = span().content(span(segment).classes(segmentClass(segment)));
            for (var method : child.operations.keySet()) {
                label.content(methodAddon(method));
            }
            if (!child.children.isEmpty()) {
                if (!child.operations.isEmpty()) {
                    label.attr("hx-get", fullPath + "/index.html")
                            .attr("hx-target", "#detail")
                            .attr("hx-swap", "innerHTML");
                }
                treeNode.node(label, sub -> addNodes(sub, child, fullPath));
            } else {
                treeNode.item(label, item -> {
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

    private static Element methodAddon(HttpMethod method) {
        return span(method.name()).classes("method-addon", "method-" + method.name().toLowerCase());
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
        var fragment = div().content(
                div().classes("is-flex", "is-align-items-center", "mb-5").style("gap:0.75rem").content(
                        headingBadge,
                        element("h2").classes("title", "is-4", "mb-0", "endpoint-path")
                                .content("/" + fullPath)),
                p(summary).classes("op-summary")
        );
        if (operation.getDescription() != null) {
            fragment.content(p(operation.getDescription()).classes("op-description"));
        }
        if (Boolean.TRUE.equals(operation.getDeprecated())) {
            fragment.content(span("DEPRECATED").classes("tag", "is-warning", "deprecated-badge"));
        }
        if (operation.getTags() != null && !operation.getTags().isEmpty()) {
            var tagsRow = div().classes("tags");
            for (var t : operation.getTags()) {
                tagsRow.content(span(t).classes("tag", "op-tag"));
            }
            fragment.content(tagsRow);
        }
        if (operation.getExternalDocs() != null) {
            fragment.content(div().classes("external-docs").content(
                    element("a").attr("href", operation.getExternalDocs().getUrl())
                            .attr("target", "_blank")
                            .content("External docs")));
        }
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
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            var content = operation.getRequestBody().getContent();
            var jsonContent = content.get("application/json");
            if (jsonContent == null) jsonContent = content.get("*/*");
            if (jsonContent != null && jsonContent.getSchema() != null) {
                var skeleton = generateJsonSkeleton(jsonContent.getSchema());
                fragment.content(
                        field("Request Body (application/json)").content(
                                element("textarea")
                                        .attr("data-request-body", "true")
                                        .classes("textarea", "is-family-code")
                                        .attr("rows", "6")
                                        .content(skeleton)));
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
            sb.append(defaultValue(entry.getValue().getType()));
        }
        sb.append("\n}");
        return sb.toString();
    }

    private static String defaultValue(String type) {
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
            .method-addon {
                font-size: 0.65rem;
                padding: 1px 5px;
                border-radius: 3px;
                color: white;
                font-weight: 600;
                margin-left: 3px;
                opacity: 0.85;
                vertical-align: middle;
            }
            .method-get { background: var(--bulma-success); }
            .method-post { background: var(--bulma-link); }
            .method-put { background: var(--bulma-warning); color: var(--bulma-text-strong); }
            .method-delete { background: var(--bulma-danger); }
            .method-patch { background: var(--bulma-primary); }
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
            #detail .op-description {
                color: var(--bulma-text);
                margin-bottom: 1rem;
                line-height: 1.6;
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
                background-color: var(--bulma-scheme-main-bis);
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
            
                // Tab switching — toggle is-active when HTMX swaps method content
                document.body.addEventListener('htmx:afterRequest', function(e) {
                    var tabLink = e.detail.elt;
                    if (tabLink && tabLink.closest && tabLink.closest('.tabs')) {
                        var tabs = tabLink.closest('.tabs');
                        tabs.querySelectorAll('li').forEach(function(li) { li.classList.remove('is-active'); });
                        tabLink.closest('li').classList.add('is-active');
                    }
                });

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
            
                // Tab keyboard navigation
                document.addEventListener('keydown', function(e) {
                    var focused = document.activeElement;
                    if (!focused || !focused.closest || !focused.closest('.tabs')) return;
                    if (['ArrowRight','ArrowLeft','ArrowDown','ArrowUp','Enter','Tab','Escape'].indexOf(e.key) < 0) return;
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
                    } else if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === 'Tab') {
                        var firstInput = document.querySelector('#method-content input, #method-content textarea, #method-content button[data-path]');
                        if (firstInput) firstInput.focus();
                    } else if (e.key === 'Escape') {
                        document.querySelector('[role="tree"]').focus();
                    }
                }, true);

                // Field navigation within method content
                document.addEventListener('keydown', function(e) {
                    var mc = document.getElementById('method-content');
                    if (!mc) return;
                    var focusables = Array.from(mc.querySelectorAll('input, textarea, button[data-path]'));
                    var idx = focusables.indexOf(document.activeElement);
                    if (idx < 0) return;

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
                operations.putAll(pathItem.readOperationsMap());
                return;
            }
            var segment = segments.get(index);
            children.computeIfAbsent(segment, k -> new PathNode())
                    .add(segments, index + 1, pathItem);
        }
    }
}
