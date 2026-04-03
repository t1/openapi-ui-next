package com.github.t1.openapi.ui.generator;

import com.github.t1.htmljava.Renderable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.github.t1.bulmajava.elements.Tag.tag;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.htmljava.HtmlBasics.span;
import static com.github.t1.openapi.ui.components.Tree.tree;
import static com.github.t1.openapi.ui.generator.OpenApiUiGenerator.methodColor;

class TagTreeGenerator {
    record TaggedOperation(HttpMethod method, ApiPath path, io.swagger.v3.oas.models.Operation operation, List<String> allTags) {}

    static Renderable buildTagTree(OpenAPI openApi, OpenApiUiGenerator.PathNode root) {
        var tagOps = new LinkedHashMap<String, List<TaggedOperation>>();
        collectTaggedOperations(root, ApiPath.ROOT, tagOps);

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
                            span(op.path.display()).classes("tree-segment").style("margin-left:0.5rem")
                    );
                    if (op.allTags.size() > 1) {
                        var otherTags = op.allTags.stream()
                                .filter(t -> !t.equals(tagName))
                                .toList();
                        label.content(span("also in: " + String.join(", ", otherTags)).classes("also-in"));
                    }
                    node.item(label, item -> item
                            .attr("data-tag", tagName)
                            .attr("hx-get", op.path + "/" + op.method.name() + ".html")
                            .attr("hx-target", "#detail")
                            .attr("hx-swap", "innerHTML"));
                }
            });
        }
        return tagTree;
    }

    private static void collectTaggedOperations(OpenApiUiGenerator.PathNode node, ApiPath pathPrefix, Map<String, List<TaggedOperation>> tagOps) {
        for (var entry : node.children.entrySet()) {
            var segment = entry.getKey();
            var child = entry.getValue();
            var fullPath = pathPrefix.resolve(segment);
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
}
