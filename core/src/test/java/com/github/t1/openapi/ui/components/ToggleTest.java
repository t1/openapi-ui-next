package com.github.t1.openapi.ui.components;

import org.junit.jupiter.api.Test;

import static com.github.t1.openapi.ui.components.Toggle.toggle;
import static org.assertj.core.api.BDDAssertions.then;

class ToggleTest {

    @Test void shouldRenderToggleWithNameAttribute() {
        var rendered = toggle("mode").render();

        then(rendered).isEqualTo("""
                <div class="toggle" data-toggle="mode" tabindex="0"></div>
                """);
    }

    @Test void shouldRenderOptionWithValue() {
        var rendered = toggle("mode").option("try").render();

        then(rendered).isEqualTo("""
                <div class="toggle" data-toggle="mode" tabindex="0">
                    <span data-toggle-value="try">try</span>
                </div>
                """);
    }

    @Test void shouldRenderOptionWithLabel() {
        var rendered = toggle("mode").option("try", "Try it").render();

        then(rendered).isEqualTo("""
                <div class="toggle" data-toggle="mode" tabindex="0">
                    <span data-toggle-value="try">Try it</span>
                </div>
                """);
    }

    @Test void shouldRenderOptionWithCustomizer() {
        var rendered = toggle("mode").option("try", o -> o.attr("title", "Send requests")).render();

        then(rendered).isEqualTo("""
                <div class="toggle" data-toggle="mode" tabindex="0">
                    <span data-toggle-value="try" title="Send requests">try</span>
                </div>
                """);
    }

    @Test void shouldRenderActiveOption() {
        var rendered = toggle("mode").activeOption("try").render();

        then(rendered).isEqualTo("""
                <div class="toggle" data-toggle="mode" tabindex="0">
                    <span class="is-active" data-toggle-value="try">try</span>
                </div>
                """);
    }

    @Test void shouldRenderActiveOptionWithLabel() {
        var rendered = toggle("mode").activeOption("try", "Try it").render();

        then(rendered).isEqualTo("""
                <div class="toggle" data-toggle="mode" tabindex="0">
                    <span class="is-active" data-toggle-value="try">Try it</span>
                </div>
                """);
    }

    @Test void shouldRenderActiveOptionWithCustomizer() {
        var rendered = toggle("mode").activeOption("try", o -> o.attr("title", "Send")).render();

        then(rendered).isEqualTo("""
                <div class="toggle" data-toggle="mode" tabindex="0">
                    <span class="is-active" data-toggle-value="try" title="Send">try</span>
                </div>
                """);
    }

    @Test void shouldPersistAs() {
        var rendered = toggle("mode").persistAs("my-key").render();

        then(rendered).isEqualTo("""
                <div class="toggle" data-toggle="mode" tabindex="0" data-persist="my-key"></div>
                """);
    }

    @Test void shouldActivateOption() {
        var rendered = toggle("mode").option("try").option("curl").activate("curl").render();

        then(rendered).isEqualTo("""
                <div class="toggle" data-toggle="mode" tabindex="0">
                    <span data-toggle-value="try">try</span>
                    <span class="is-active" data-toggle-value="curl">curl</span>
                </div>
                """);
    }

    @Test void shouldDeactivatePreviouslyActiveOption() {
        var rendered = toggle("mode").activeOption("try").option("curl").activate("curl").render();

        then(rendered).isEqualTo("""
                <div class="toggle" data-toggle="mode" tabindex="0">
                    <span data-toggle-value="try">try</span>
                    <span class="is-active" data-toggle-value="curl">curl</span>
                </div>
                """);
    }
}
