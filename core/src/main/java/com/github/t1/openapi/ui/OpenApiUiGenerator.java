package com.github.t1.openapi.ui;

import com.github.t1.htmljava.Element;
import io.swagger.v3.oas.models.PathItem;
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

import static com.github.t1.bulmajava.basic.Color.PRIMARY;
import static com.github.t1.bulmajava.columns.Column.column;
import static com.github.t1.bulmajava.columns.Columns.columns;
import static com.github.t1.bulmajava.elements.Button.button;
import static com.github.t1.bulmajava.form.Field.field;
import static com.github.t1.bulmajava.form.Input.input;
import static com.github.t1.bulmajava.form.InputType.TEXT;
import static com.github.t1.bulmajava.layout.Container.container;
import static com.github.t1.bulmajava.layout.Section.section;
import static com.github.t1.htmljava.Html.html;
import static com.github.t1.htmljava.HtmlBasics.*;

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

        var list = renderNode(root, "", true);

        var servers = openApi.getServers();
        var baseUrl = (servers != null && !servers.isEmpty()) ? servers.get(0).getUrl() : "/";

        var modeToggle = div().attr("data-mode", "try").attr("data-base-url", baseUrl)
                .classes("buttons", "has-addons").content(
                        button("Try").is(PRIMARY).classes("is-selected").attr("data-mode-btn", "try"),
                        button("httpie").attr("data-mode-btn", "httpie"),
                        button("curl").attr("data-mode-btn", "curl")
                );

        var pageTitle = openApi.getInfo().getTitle();
        var detail = div().id("detail").attr("tabindex", "0");
        var detailHeader = div().classes("detail-header").content(
                element("h1").classes("title").content(pageTitle),
                modeToggle
        );
        var body = section().content(container().content(
                columns().classes("is-desktop").content(
                        column().classes("is-one-third").content(list),
                        column().classes("detail-column").content(detailHeader, detail)
                )
        ));
        var page = html(pageTitle)
                .stylesheet("bulma.min.css")
                .stylesheet("openapi-ui.css")
                .script("htmx.min.js")
                .javaScriptCode(TREE_KEYBOARD_JS)
                .body(body);

        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("index.html"), page.render());
        Files.writeString(outputDir.resolve("openapi-ui.css"), CUSTOM_CSS);

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

    private void generateFragments(PathNode node, String pathPrefix) throws IOException {
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
            for (var opEntry : child.operations.entrySet()) {
                var method = opEntry.getKey();
                var operation = opEntry.getValue();
                var summary = operation.getSummary() != null ? operation.getSummary() : "";
                var headingBadge = span(method.name()).classes("method-badge", "detail-badge",
                        "method-" + method.name().toLowerCase());
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

    private boolean firstTreeItem = true;

    private Element renderNode(PathNode node, String pathPrefix, boolean isRoot) {
        Element list = ul();
        if (isRoot) {
            list.attr("role", "tree").attr("tabindex", "0");
        } else {
            list.attr("role", "group");
        }
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
            Element item = li().attr("role", "treeitem");
            if (firstTreeItem) {
                item.attr("aria-selected", "true");
                firstTreeItem = false;
            }
            if (!child.children.isEmpty()) {
                item.attr("aria-expanded", "true");
                item.content(span("\u25BC").classes("tree-toggle"));
            }
            item.content(span(segment).classes("tree-segment"));
            for (var opEntry : child.operations.entrySet()) {
                var method = opEntry.getKey();
                var operation = opEntry.getValue();
                var summary = operation.getSummary();
                var badge = span(method.name()).classes("method-badge", "method-" + method.name().toLowerCase());
                var labelText = summary != null ? " — " + summary : "";
                item.content(span().classes("tree-op-label").content(badge).content(labelText)
                        .attr("hx-get", fullPath + "/" + method.name() + ".html")
                        .attr("hx-target", "#detail")
                        .attr("hx-swap", "innerHTML"));
            }
            if (!child.children.isEmpty()) {
                item.content(renderNode(child, fullPath, false));
            }
            list.content(item);
        }
        return list;
    }

    private static final String CUSTOM_CSS = """
            body {
                background-color: hsl(220, 15%, 97%);
            }
            .section {
                padding-top: 1.5rem;
                min-height: 100vh;
            }
            /* --- Tree panel --- */
            [role="tree"] {
                list-style: none;
                margin: 0;
                padding: 0;
            }
            [role="group"] {
                list-style: none;
                margin: 0;
                padding: 0 0 0 1.25rem;
                border-left: 2px solid hsl(220, 15%, 87%);
                margin-left: 0.5rem;
            }
            [role="treeitem"] {
                padding: 4px 0;
                line-height: 1.7;
            }
            [role="treeitem"] > span {
                cursor: pointer;
                padding: 3px 8px;
                border-radius: 4px;
            }
            [role="treeitem"] > span:hover {
                background-color: hsl(220, 20%, 91%);
            }
            [role="treeitem"][aria-selected="true"] > span:first-child {
                background-color: hsl(217, 71%, 93%);
            }
            [role="tree"]:focus-visible [role="treeitem"][aria-selected="true"] > span:first-child {
                outline: 2px solid hsl(217, 71%, 53%);
                outline-offset: 1px;
            }
            .tree-segment {
                font-weight: 600;
                color: hsl(220, 15%, 25%);
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-size: 0.9rem;
            }
            .tree-toggle {
                display: inline-block;
                cursor: pointer;
                font-size: 0.65rem;
                width: 1rem;
                text-align: center;
                transition: transform 0.15s ease;
                user-select: none;
                vertical-align: middle;
                color: hsl(220, 10%, 55%);
            }
            [role="treeitem"][aria-expanded="false"] > .tree-toggle {
                transform: rotate(-90deg);
            }
            .tree-op-label {
                color: hsl(220, 10%, 45%);
                font-size: 0.85rem;
            }
            .columns.is-desktop > .column.is-one-third {
                background-color: hsl(220, 18%, 95%);
                border-right: 1px solid hsl(220, 15%, 88%);
                padding: 1.25rem 1.5rem;
            }
            @media screen and (min-width: 1024px) {
                .columns.is-desktop > .column.is-one-third {
                    min-height: calc(100vh - 4rem);
                }
            }
            /* --- Method badges --- */
            .method-badge {
                display: inline-block;
                padding: 2px 8px;
                border-radius: 4px;
                font-size: 0.7rem;
                font-weight: 700;
                color: white;
                text-transform: uppercase;
                letter-spacing: 0.5px;
                vertical-align: middle;
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
            }
            .method-badge.detail-badge {
                font-size: 0.85rem;
                padding: 4px 12px;
                border-radius: 5px;
            }
            .method-get { background-color: hsl(141, 53%, 45%); }
            .method-post { background-color: hsl(217, 71%, 50%); }
            .method-put { background-color: hsl(38, 90%, 45%); }
            .method-delete { background-color: hsl(348, 75%, 52%); }
            .method-patch { background-color: hsl(271, 60%, 55%); }
            /* --- Detail pane --- */
            .detail-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                flex-wrap: wrap;
                gap: 0.75rem;
                margin-bottom: 1.25rem;
                padding-bottom: 0.75rem;
                border-bottom: 2px solid hsl(220, 15%, 90%);
            }
            .detail-header .title {
                margin-bottom: 0;
                color: hsl(220, 20%, 20%);
            }
            .detail-column {
                padding-left: 2rem;
            }
            #detail {
                background-color: white;
                border-radius: 8px;
                padding: 1.5rem;
                box-shadow: 0 1px 3px hsla(220, 20%, 20%, 0.06);
                border: 1px solid hsl(220, 15%, 92%);
            }
            #detail .endpoint-path {
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-weight: 500;
                color: hsl(220, 15%, 30%);
            }
            #detail .op-summary {
                color: hsl(220, 10%, 45%);
                margin-bottom: 1.25rem;
            }
            #detail pre {
                border: 1px solid hsl(220, 15%, 90%);
                border-radius: 6px;
                padding: 1rem 1.25rem;
                background-color: hsl(220, 18%, 97%);
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

    private static final String TREE_KEYBOARD_JS = """
            document.addEventListener('DOMContentLoaded', function() {
                var tree = document.querySelector('[role="tree"]');
                if (!tree) return;

                function isGroupVisible(el) {
                    while (el && el !== tree) {
                        if (el.parentElement && el.parentElement.getAttribute('aria-expanded') === 'false') return false;
                        el = el.parentElement;
                    }
                    return true;
                }

                function getVisibleItems() {
                    return Array.from(tree.querySelectorAll('[role="treeitem"]')).filter(function(item) {
                        return isGroupVisible(item);
                    });
                }

                function toggleNode(item, expand) {
                    var group = item.querySelector('[role="group"]');
                    if (!group) return;
                    item.setAttribute('aria-expanded', expand ? 'true' : 'false');
                    group.style.display = expand ? '' : 'none';
                }

                tree.addEventListener('click', function(e) {
                    var toggle = e.target.closest('.tree-toggle');
                    if (!toggle) return;
                    var item = toggle.closest('[role="treeitem"]');
                    if (!item) return;
                    var expanded = item.getAttribute('aria-expanded') === 'true';
                    toggleNode(item, !expanded);
                });

                tree.addEventListener('keydown', function(e) {
                    var items = getVisibleItems();
                    var current = tree.querySelector('[aria-selected="true"]');
                    var idx = items.indexOf(current);

                    switch (e.key) {
                        case 'ArrowDown':
                            e.preventDefault();
                            if (idx < items.length - 1) selectItem(items[idx + 1]);
                            break;
                        case 'ArrowUp':
                            e.preventDefault();
                            if (idx > 0) selectItem(items[idx - 1]);
                            break;
                        case 'ArrowRight':
                            e.preventDefault();
                            if (current.getAttribute('aria-expanded') === 'false') {
                                toggleNode(current, true);
                            }
                            break;
                        case 'ArrowLeft':
                            e.preventDefault();
                            if (current.getAttribute('aria-expanded') === 'true') {
                                toggleNode(current, false);
                            } else {
                                var parentGroup = current.closest('[role="group"]');
                                if (parentGroup) {
                                    var parentItem = parentGroup.closest('[role="treeitem"]');
                                    if (parentItem) selectItem(parentItem);
                                }
                            }
                            break;
                        case 'Enter':
                            e.preventDefault();
                            var hxEl = current.querySelector('[hx-get]') || current;
                            if (hxEl.getAttribute('hx-get')) htmx.ajax('GET', hxEl.getAttribute('hx-get'), '#detail');
                            break;
                        case 'Escape':
                            e.preventDefault();
                            tree.focus();
                            break;
                    }
                });

                function selectItem(item) {
                    tree.querySelectorAll('[aria-selected="true"]').forEach(function(el) {
                        el.removeAttribute('aria-selected');
                    });
                    item.setAttribute('aria-selected', 'true');
                }

                var detail = document.getElementById('detail');
                document.addEventListener('keydown', function(e) {
                    if (e.key === 'Escape' && !e.target.closest('[role="tree"]')) {
                        e.preventDefault();
                        tree.focus();
                    }
                });

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
                        var url = baseUrl.startsWith('http') ? baseUrl + pathTemplate
                                : new URL((baseUrl + pathTemplate).replace(/\\/+/g, '/'), window.location.origin).href;
                        var queryParams = [];
                        inputs.forEach(function(inp) {
                            var name = inp.getAttribute('name');
                            var val = inp.value;
                            if (pathTemplate.includes('{' + name + '}')) {
                                url = url.replace('{' + name + '}', encodeURIComponent(val));
                            } else if (val) {
                                queryParams.push(name + '=' + encodeURIComponent(val));
                            }
                        });
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
