package com.github.t1.openapi.ui;

import com.github.t1.htmljava.Element;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.t1.bulmajava.basic.Color.PRIMARY;
import static com.github.t1.bulmajava.columns.Column.column;
import static com.github.t1.bulmajava.columns.Columns.columns;
import static com.github.t1.bulmajava.elements.Button.button;
import static com.github.t1.bulmajava.elements.Title.title;
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
        var body = section().content(container().content(
                modeToggle,
                columns().classes("is-desktop").content(
                        column().classes("is-one-third").content(list),
                        column().content(detail)
                )
        ));
        var page = html(pageTitle)
                .stylesheet("https://cdn.jsdelivr.net/npm/bulma@1.0.0/css/bulma.min.css")
                .script("htmx.min.js")
                .javaScriptCode(TREE_KEYBOARD_JS)
                .body(body);

        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("index.html"), page.render());

        generateFragments(root, "");

        try (var htmx = getClass().getResourceAsStream("/htmx.min.js")) {
            Files.copy(htmx, outputDir.resolve("htmx.min.js"));
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
                var fragment = div().content(
                        title(method.name() + " /" + fullPath),
                        p(summary)
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
            item.content(span(segment));
            for (var opEntry : child.operations.entrySet()) {
                var method = opEntry.getKey();
                var operation = opEntry.getValue();
                var summary = operation.getSummary() != null ? operation.getSummary() : "";
                item.content(span(" " + method + " — " + summary)
                        .attr("hx-get", fullPath + "/" + method.name() + ".html")
                        .attr("hx-target", "#detail")
                        .attr("hx-swap", "innerHTML"));
            }
            if (!child.children.isEmpty()) {
                Element childList = renderNode(child, fullPath, false);
                childList.style("display:none");
                item.content(childList);
            }
            list.content(item);
        }
        return list;
    }

    private static final String TREE_KEYBOARD_JS = """
            document.addEventListener('DOMContentLoaded', function() {
                var tree = document.querySelector('[role="tree"]');
                if (!tree) return;

                function getVisibleItems() {
                    return Array.from(tree.querySelectorAll('[role="treeitem"]')).filter(function(item) {
                        var el = item;
                        while (el && el !== tree) {
                            if (el.style && el.style.display === 'none') return false;
                            el = el.parentElement;
                        }
                        return true;
                    });
                }

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
                            var group = current.querySelector('[role="group"]');
                            if (group) group.style.display = '';
                            break;
                        case 'ArrowLeft':
                            e.preventDefault();
                            var grp = current.querySelector('[role="group"]');
                            if (grp && grp.style.display !== 'none') {
                                grp.style.display = 'none';
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
                if (detail) {
                    detail.addEventListener('keydown', function(e) {
                        if (e.key === 'Escape') {
                            e.preventDefault();
                            document.querySelector('[role="tree"]').focus();
                        }
                    });
                }

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
                        });
                    });
                }

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
                        var url = baseUrl + pathTemplate;
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
                        } else if (mode === 'httpie') {
                            navigator.clipboard.writeText('http ' + method + ' ' + url);
                        } else if (mode === 'try') {
                            fetch(url).then(function(resp) {
                                var ct = resp.headers.get('Content-Type') || '';
                                return resp.text().then(function(text) {
                                    var pre = document.createElement('pre');
                                    if (ct.includes('json')) {
                                        try { text = JSON.stringify(JSON.parse(text), null, 2); } catch(e) {}
                                    }
                                    pre.textContent = text;
                                    var existing = detail.querySelector('pre.response');
                                    if (existing) existing.remove();
                                    pre.className = 'response';
                                    detail.appendChild(pre);
                                });
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
