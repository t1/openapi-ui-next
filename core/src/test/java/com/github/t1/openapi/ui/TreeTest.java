package com.github.t1.openapi.ui;

import org.junit.jupiter.api.Test;

import static com.github.t1.htmljava.HtmlBasics.span;
import static com.github.t1.openapi.ui.Tree.tree;
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
                .contains("aria-expanded=\"true\"")
                .contains("tree-toggle")
                .contains("<span>parent</span>")
                .contains("role=\"group\"")
                .contains("<span>child</span>");
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

    @Test void shouldProvideCss() {
        then(Tree.css())
                .contains("[role=\"tree\"]")
                .contains(".tree-toggle")
                .contains(".tree-segment")
                .doesNotContain(".tree-op-label");
    }

    @Test void shouldProvideJs() {
        then(Tree.js())
                .contains("role=\"tree\"")
                .contains("ArrowDown")
                .contains("ArrowUp");
    }
}
