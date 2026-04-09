package com.github.t1.openapi.ui.generator;

import io.smallrye.openapi.runtime.io.OpenApiParser;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parses an OpenAPI spec file and generates the UI as files in an output directory.
 * Used by the CLI and Maven plugin; the Quarkus extension uses [OpenApiUiGenerator] directly.
 */
public class OpenApiUiFileGenerator {
    private static final Logger log = LoggerFactory.getLogger(OpenApiUiFileGenerator.class);

    private final Path specFile;
    private final Path outputDir;

    public OpenApiUiFileGenerator(Path specFile, Path outputDir) {
        this.specFile = specFile;
        this.outputDir = outputDir;
    }

    public void generate() throws IOException {
        var openApi = parseSpec();
        Files.createDirectories(outputDir);
        new OpenApiUiGenerator(openApi, this::writeFile).generate();
        log.info("Output written to {}", outputDir);
    }

    private OpenAPI parseSpec() {
        log.info("Parsing {}", specFile);
        try {
            return OpenApiParser.parse(specFile.toUri().toURL());
        } catch (IOException e) {
            throw new RuntimeException("could not parse spec: " + specFile, e);
        }
    }

    private void writeFile(String name, byte[] content) {
        try {
            var file = outputDir.resolve(name);
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        } catch (IOException e) {
            throw new RuntimeException("could not write file: " + name, e);
        }
    }
}
