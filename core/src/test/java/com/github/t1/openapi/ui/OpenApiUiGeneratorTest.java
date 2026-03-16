package com.github.t1.openapi.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.BDDAssertions.then;

class OpenApiUiGeneratorTest {
    @TempDir Path outputDir;

    void generate(String path) throws URISyntaxException, IOException {
        var specPath = Path.of(requireNonNull(getClass().getResource(path)).toURI());
        new OpenApiUiGenerator(specPath, outputDir).generate();
    }

    @Test void shouldGroupPathsBySegments() throws Exception {
        generate("/nested-paths.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("pets");
        then(indexHtml).contains("{petId}");
        int petsListItem = indexHtml.indexOf("pets");
        int nestedUl = indexHtml.indexOf("<ul", petsListItem);
        int petIdItem = indexHtml.indexOf("{petId}", nestedUl);
        then(petIdItem).as("{petId} should be in a nested list under pets").isGreaterThan(nestedUl);
    }

    @Test void shouldGenerateFragmentFiles() throws Exception {
        generate("/nested-paths.yaml");

        then(outputDir.resolve("pets/GET.html")).exists();
        then(outputDir.resolve("pets/{petId}/GET.html")).exists();
        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("List pets");
        then(fragment).contains("GET");
    }

    @Test void shouldIncludeHtmxAttributes() throws Exception {
        generate("/one-get.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("htmx.min.js");
        then(indexHtml).contains("hx-get=\"pets/index.html\"");
        then(indexHtml).contains("hx-target=");
    }

    @Test void shouldGenerateParameterInputs() throws Exception {
        generate("/params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("name=\"petId\"");
        then(fragment).contains("name=\"fields\"");
        then(fragment).contains("Comma-separated list of fields");
    }

    @Test void shouldRenderResponseSchema() throws Exception {
        generate("/params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("id");
        then(fragment).contains("name");
        then(fragment).contains("string");
    }

@Test void shouldUseParamClassForPathParameters() throws Exception {
        generate("/nested-paths.yaml");

        var html = Files.readString(outputDir.resolve("index.html"));

        then(html).contains("class=\"tree-param\"");
    }

    @Test void shouldGenerateRequestBodyTextarea() throws Exception {
        generate("/request-body.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
        then(fragment).contains("data-request-body");
        then(fragment).contains("&quot;name&quot;");
        then(fragment).contains("&quot;age&quot;: 0");
        then(fragment).contains("&quot;active&quot;: false");
    }

    @Test void shouldIncludeRequestBodyStyles() throws Exception {
        generate("/request-body.yaml");

        var css = Files.readString(outputDir.resolve("openapi-ui.css"));
        then(css).contains("data-request-body");
    }

    @Test void shouldRenderMethodTagAddons() throws Exception {
        generate("/multi-method.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        // Method addons in tree, not operation labels
        then(indexHtml).contains("class=\"tag is-");
        then(indexHtml).doesNotContain("class=\"tree-op-summary\"");
        then(indexHtml).doesNotContain("class=\"tree-op-label\"");
    }

    @Test void shouldGeneratePathFragmentWithTabs() throws Exception {
        generate("/multi-method.yaml");

        var pathFragment = Files.readString(outputDir.resolve("pets/index.html"));
        then(pathFragment).contains("class=\"tabs\"");
        then(pathFragment).contains("hx-get=\"pets/GET.html\"");
        then(pathFragment).contains("hx-get=\"pets/POST.html\"");
        then(pathFragment).contains("id=\"method-content\"");
        then(pathFragment).contains("List pets");
    }

    @Test void shouldStillGenerateMethodFragments() throws Exception {
        generate("/multi-method.yaml");

        then(outputDir.resolve("pets/GET.html")).exists();
        then(outputDir.resolve("pets/POST.html")).exists();
        then(outputDir.resolve("pets/{petId}/GET.html")).exists();
        then(outputDir.resolve("pets/{petId}/DELETE.html")).exists();
        then(outputDir.resolve("pets/index.html")).exists();
        then(outputDir.resolve("pets/{petId}/index.html")).exists();
    }

    @Test void shouldRenderDescription() throws Exception {
        generate("/multi-method.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("Returns all pets from the system.");
        then(fragment).contains("op-description");
    }

    @Test void shouldRenderDeprecatedBadge() throws Exception {
        generate("/multi-method.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("DEPRECATED");
        then(fragment).contains("deprecated-badge");
    }

    @Test void shouldRenderTags() throws Exception {
        generate("/multi-method.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("op-tag");
        then(fragment).contains("pets");
    }

    @Test void shouldRenderExternalDocs() throws Exception {
        generate("/multi-method.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("external-docs");
        then(fragment).contains("https://example.com/docs/pets");
    }

    @Test void methodAddonsAreGrouped() throws Exception {
        generate("/nested-paths.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("tags has-addons");
    }

    @Test void shouldGenerateIndexWithOnePath() throws Exception {
        generate("/one-get.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("pets");
        then(indexHtml).contains("GET");
    }
}
