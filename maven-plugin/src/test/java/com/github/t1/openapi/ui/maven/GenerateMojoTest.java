package com.github.t1.openapi.ui.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.BDDAssertions.then;

class GenerateMojoTest {
    @TempDir Path tempDir;

    @Test void shouldGenerateOutput() throws Exception {
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

        var mojo = new GenerateMojo();
        mojo.specFile = specFile.toFile();
        mojo.outputDirectory = outputDir.toFile();
        mojo.execute();

        then(outputDir.resolve("index.html")).exists();
        then(outputDir.resolve("test/GET.html")).exists();
    }
}
