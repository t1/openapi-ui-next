package com.github.t1.openapi.ui.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.BDDAssertions.then;

class MainTest {
    @TempDir Path tempDir;

    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args, new PrintStream(stderr));
    }

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

        var exitCode = run(specFile.toString(), outputDir.toString());

        then(exitCode).isZero();
        then(outputDir.resolve("index.html")).exists();
    }

    @Test void shouldAcceptVerboseFlag() throws Exception {
        var specFile = writeMinimalSpec();
        var outputDir = tempDir.resolve("output");

        var exitCode = run("--verbose", specFile.toString(), outputDir.toString());

        then(exitCode).isZero();
        then(outputDir.resolve("index.html")).exists();
    }

    @Test void shouldPrintUsageWithNoArgs() {
        var exitCode = run();

        then(exitCode).isEqualTo(1);
        then(stderr.toString()).isEqualTo("Usage: openapi-ui [--verbose] <spec-file> <output-dir>\n");
    }

    @Test void shouldPrintCleanErrorForMissingSpecFile() {
        var exitCode = run("nonexistent.yaml", tempDir.resolve("output").toString());

        then(exitCode).isEqualTo(2);
        then(stderr.toString()).startsWith("RuntimeException: could not parse spec: nonexistent.yaml");
        then(stderr.toString()).doesNotContain("at com.github.t1");
    }

    @Test void shouldPrintStackTraceInVerboseMode() {
        var exitCode = run("--verbose", "nonexistent.yaml", tempDir.resolve("output").toString());

        then(exitCode).isEqualTo(2);
        then(stderr.toString()).isNotEmpty();
        then(stderr.toString()).contains("at com.github.t1");
    }
}
