package com.github.t1.openapi.ui.components;

import org.junit.jupiter.api.Test;

import static com.github.t1.openapi.ui.components.TabBar.tabBar;
import static org.assertj.core.api.BDDAssertions.then;

class TabBarTest {
    @Test void shouldRenderWithTabindexOnContainer() {
        var bar = tabBar("test-tabs")
                .activeTab("200", "200")
                .tab("404", "404");

        var html = bar.render();

        then(html).contains("tabindex=\"0\"");
        then(html).contains("data-tab-bar=\"test-tabs\"");
    }

    @Test void shouldMarkFirstTabActive() {
        var bar = tabBar("test").activeTab("a", "A").tab("b", "B");

        var html = bar.render();

        then(html).contains("is-active");
    }

    @Test void shouldHaveTabindexOnlyOnContainer() {
        var bar = tabBar("test").activeTab("a", "A").tab("b", "B");

        var html = bar.render();

        // tabindex appears exactly once (on the container)
        var count = html.split("tabindex").length - 1;
        then(count).isEqualTo(1);
    }
}
