package com.github.t1.openapi.ui.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class MainTest {
    @TempDir Path tempDir;

    private Path writeMinimalSpec() throws Exception {
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
        return specFile;
    }

    @Test void shouldGenerateFromArgs() throws Exception {
        var specFile = writeMinimalSpec();
        var outputDir = tempDir.resolve("output");

        Main.main(new String[]{specFile.toString(), outputDir.toString()});

        then(outputDir.resolve("index.html")).exists();
    }

    @Test void shouldAcceptVerboseFlag() throws Exception {
        var specFile = writeMinimalSpec();
        var outputDir = tempDir.resolve("output");

        Main.main(new String[]{"--verbose", specFile.toString(), outputDir.toString()});

        then(outputDir.resolve("index.html")).exists();
    }

    @Test void shouldPrintUsageWithNoArgs() {
        thenThrownBy(() -> Main.main(new String[]{}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usage");
    }
}
