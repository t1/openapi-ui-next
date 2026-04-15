package com.github.t1.openapi.ui.components;

import org.junit.jupiter.api.Test;

import static com.github.t1.htmljava.HtmlBasics.span;
import static com.github.t1.openapi.ui.components.Tree.tree;
import static org.assertj.core.api.BDDAssertions.then;

class TreeTest {
    @Test void shouldRenderEmptyTree() {
        var html = tree().render();

        then(html.strip()).isEqualTo("<ul role=\"tree\" tabindex=\"0\" autofocus=\"\"></ul>");
    }

    @Test void shouldRenderLeafItem() {
        var html = tree().item("hello").render();

        then(html)
                .contains("<li role=\"treeitem\"")
                .contains("<span class=\"tree-label\">")
                .contains("<span>hello</span>");
    }

    @Test void shouldRenderLeafItemWithRenderableContent() {
        var html = tree().item(span("rich").classes("styled")).render();

        then(html)
                .contains("<li role=\"treeitem\"")
                .contains("<span class=\"styled\">rich</span>");
    }

    @Test void shouldRenderNodeWithChildren() {
        var html = tree().node("parent", node -> node.item("child")).render();

        then(html)
                .contains("aria-expanded=\"false\"")
                .contains("tree-toggle")
                .contains("<span>parent</span>")
                .contains("role=\"group\"")
                .contains("<span>child</span>");
        // tree-label wraps toggle+label but not the subtree
        int treeLabelPos = html.indexOf("tree-label");
        int groupPos = html.indexOf("role=\"group\"");
        then(treeLabelPos).as("node should have tree-label wrapper").isGreaterThan(-1);
        // the closing </span> of tree-label must come before the group
        int treeLabelEnd = html.indexOf("</span>", html.indexOf("tree-label"));
        then(treeLabelEnd).as("tree-label should close before subtree").isLessThan(groupPos);
    }

    @Test void shouldRenderNodeWithSiblingContent() {
        var html = tree().node("seg", node -> {
            node.content(span("badge").classes("op"));
            node.item("child");
        }).render();

        int badgePos = html.indexOf("badge");
        int groupPos = html.indexOf("role=\"group\"");
        then(badgePos).as("sibling content should appear before subtree").isLessThan(groupPos);
    }

    @Test void shouldSelectFirstItem() {
        var html = tree()
                .item("first")
                .item("second")
                .render();

        then(html).contains("aria-selected=\"true\"");
        int selectedPos = html.indexOf("aria-selected");
        int firstPos = html.indexOf("first");
        then(selectedPos).as("aria-selected should be on the first item").isLessThan(firstPos + 20);
    }

    @Test void shouldNotSelectSecondItem() {
        var html = tree()
                .item("first")
                .item("second")
                .render();

        int lastIndex = html.lastIndexOf("aria-selected");
        int firstIndex = html.indexOf("aria-selected");
        then(lastIndex).as("only one item should be selected").isEqualTo(firstIndex);
    }

    @Test void shouldRenderNodeChildItemWithExtraContent() {
        var html = tree().node("parent", node ->
            node.item(span("seg").classes("tree-segment"), item ->
                item.content(span("badge").classes("op")))
        ).render();

        then(html)
                .contains("<span class=\"tree-segment\">seg</span>")
                .contains("<span class=\"op\">badge</span>");
        // badge must be inside the group (subtree), not on the parent <li>
        int badgePos = html.indexOf("class=\"op\"");
        int groupPos = html.indexOf("role=\"group\"");
        then(badgePos).as("badge should be inside the subtree group").isGreaterThan(groupPos);
    }

    @Test void shouldRenderItemWithExtraContent() {
        var html = tree().item(span("seg").classes("tree-segment"), item ->
            item.content(span("badge").classes("op"))
        ).render();

        then(html)
                .contains("<li role=\"treeitem\"")
                .contains("<span class=\"tree-segment\">seg</span>")
                .contains("<span class=\"op\">badge</span>");
        int segPos = html.indexOf("tree-segment");
        int badgePos = html.indexOf("class=\"op\"");
        then(segPos).as("segment and badge should be in same <li>").isLessThan(badgePos);
    }

    @Test void shouldProvideCss() {
        then(Tree.css())
                .contains("[role=\"tree\"]")
                .contains(".tree-toggle")
                .contains(".tree-segment")
                .contains(".tree-param")
                .contains("transition")
                .doesNotContain(".tree-op-label");
    }

    @Test void shouldStyleSelectionOnTreeLabelNotLi() {
        var css = Tree.css();

        then(css).contains(".tree-label");
        then(css).doesNotContain("[role=\"treeitem\"][aria-selected=\"true\"] {");
    }

    @Test void shouldNotUseItalicForTreeParam() {
        then(Tree.css())
                .contains(".tree-param")
                .doesNotContain("italic")
                .doesNotContain("#7c5cbf");
    }

    @Test void shouldProvideJs() {
        then(Tree.js())
                .contains("role=\"tree\"")
                .contains("ArrowDown")
                .contains("ArrowUp");
    }
}
