package com.github.t1.openapi.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiUiGeneratorTest {
    @TempDir Path outputDir;

    @Test
    void shouldGroupPathsBySegments() throws Exception {
        var specPath = Path.of(getClass().getResource("/nested-paths.yaml").toURI());
        new OpenApiUiGenerator(specPath, outputDir).generate();
        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        assertTrue(indexHtml.contains("pets"));
        assertTrue(indexHtml.contains("{petId}"));
        int petsListItem = indexHtml.indexOf("pets");
        int nestedUl = indexHtml.indexOf("<ul", petsListItem);
        int petIdItem = indexHtml.indexOf("{petId}", nestedUl);
        assertTrue(petIdItem > nestedUl, "{petId} should be in a nested list under pets");
    }

    @Test
    void shouldGenerateFragmentFiles() throws Exception {
        var specPath = Path.of(getClass().getResource("/nested-paths.yaml").toURI());

        new OpenApiUiGenerator(specPath, outputDir).generate();

        assertTrue(Files.exists(outputDir.resolve("pets/GET.html")));
        assertTrue(Files.exists(outputDir.resolve("pets/{petId}/GET.html")));

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        assertTrue(fragment.contains("List pets"));
        assertTrue(fragment.contains("GET"));
    }

    @Test
    void shouldGenerateIndexWithOnePath() throws Exception {
        var specPath = Path.of(getClass().getResource("/one-get.yaml").toURI());

        new OpenApiUiGenerator(specPath, outputDir).generate();

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        assertTrue(indexHtml.contains("pets"));
        assertTrue(indexHtml.contains("GET"));
    }
}
