package com.github.t1.openapi.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiUiGeneratorTest {
    @TempDir Path outputDir;

    @Test
    void shouldGenerateIndexWithOnePath() throws Exception {
        var specPath = Path.of(getClass().getResource("/one-get.yaml").toURI());

        new OpenApiUiGenerator(specPath, outputDir).generate();

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        assertTrue(indexHtml.contains("/pets"));
        assertTrue(indexHtml.contains("List pets"));
    }
}
