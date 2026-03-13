package com.github.t1.openapi.ui.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir Path tempDir;

    @Test
    void shouldGenerateFromArgs() throws Exception {
        var specFile = tempDir.resolve("spec.yaml");
        Files.writeString(specFile, """
                openapi: 3.0.3
                info:
                  title: Test
                  version: 1.0.0
                paths:
                  /test:
                    get:
                      summary: Test endpoint
                      responses:
                        '200':
                          description: OK
                """);
        var outputDir = tempDir.resolve("output");

        Main.main(new String[]{specFile.toString(), outputDir.toString()});

        assertTrue(Files.exists(outputDir.resolve("index.html")));
    }

    @Test
    void shouldPrintUsageWithNoArgs() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                Main.main(new String[]{}));
        assertTrue(ex.getMessage().contains("Usage"));
    }
}
