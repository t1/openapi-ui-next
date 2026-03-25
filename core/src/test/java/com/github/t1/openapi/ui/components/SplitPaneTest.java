package com.github.t1.openapi.ui.components;

import org.junit.jupiter.api.Test;

import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.openapi.ui.components.SplitPane.splitPane;
import static org.assertj.core.api.BDDAssertions.then;

class SplitPaneTest {
    @Test void shouldRenderSplitLayout() {
        var pane = splitPane()
                .first(div().content("left"))
                .second(div().content("right"));

        var html = pane.render();

        then(html)
                .contains("class=\"split-layout\"")
                .contains("class=\"split-first\"")
                .contains("class=\"split-handle\"")
                .contains("class=\"split-second\"")
                .contains("left")
                .contains("right")
                .doesNotContain("data-persist");
    }

    @Test void shouldProvideJsWithDragBehavior() {
        var js = SplitPane.js();

        then(js)
                .contains("split-handle")
                .contains("pointerdown")
                .contains("pointermove");
    }

    @Test void shouldSupportMultipleSplitPanes() {
        var js = SplitPane.js();

        then(js).contains("querySelectorAll");
        then(js).doesNotContain("document.querySelector(");
    }

    @Test void shouldReinitializeAfterHtmxSwap() {
        var js = SplitPane.js();

        then(js).contains("htmx:afterSettle");
        then(js).contains("initSplitPanes");
    }

    @Test void shouldAddPersistDataAttribute() {
        var pane = splitPane()
                .first(div().content("left"))
                .second(div().content("right"))
                .persistAs("my-key");

        var html = pane.render();

        then(html).contains("data-persist=\"my-key\"");
    }

    @Test void shouldApplyCustomRatio() {
        var pane = splitPane()
                .ratio(1, 1)
                .first(div().content("left"))
                .second(div().content("right"));

        var html = pane.render();

        then(html).contains("grid-template-columns: minmax(150px, 1fr) 0px minmax(150px, 1fr)");
    }

    @Test void shouldProvideSplitLayoutCss() {
        var css = SplitPane.css();

        then(css)
                .contains(".split-layout")
                .contains(".split-handle")
                .contains(".split-handle::after")
                .contains("col-resize");
    }
}
