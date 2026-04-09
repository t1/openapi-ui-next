package com.github.t1.openapi.ui.quarkus.deployment;

import com.github.t1.openapi.ui.generator.OpenApiUiGenerator;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.smallrye.openapi.deployment.spi.OpenApiDocumentBuildItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * Quarkus build step that consumes the SmallRye OpenAPI model and generates
 * OpenAPI UI static resources (HTML, CSS, JS) at build time.
 *
 * In dev-mode, this runs on every hot-reload when JAX-RS code changes,
 * triggering browser auto-refresh via Quarkus's built-in live-reload.
 */
public class OpenApiUiProcessor {
    private static final Logger log = LoggerFactory.getLogger(OpenApiUiProcessor.class);

    private static final String ENDPOINT_PREFIX = "/openapi-ui/";

    @BuildStep CardPageBuildItem devUiCard() {
        var card = new CardPageBuildItem();
        card.addPage(Page.externalPageBuilder("OpenAPI UI")
                .url(ENDPOINT_PREFIX + "index.html")
                .doNotEmbed()
                .icon("font-awesome-solid:file-code"));
        return card;
    }

    @BuildStep
    void generateOpenApiUi(
            List<OpenApiDocumentBuildItem> openApiDocuments,
            OutputTargetBuildItem outputTarget,
            BuildProducer<GeneratedResourceBuildItem> generatedResources)
            throws IOException {

        if (openApiDocuments.isEmpty()) {
            log.info("No OpenAPI document found — skipping OpenAPI UI generation");
            return;
        }

        var defaultDoc = openApiDocuments.getFirst();

        log.info("Generating OpenAPI UI from OpenAPI document");

        // Write to classes/META-INF/resources/ so Quarkus serves them as static resources,
        // and produce GeneratedResourceBuildItem so Quarkus re-runs this step on hot-reload.
        var outputDir = outputTarget.getOutputDirectory().resolve("classes/META-INF/resources/openapi-ui");
        deleteRecursively(outputDir);

        var openApi = defaultDoc.getSmallRyeOpenAPI().model();
        new OpenApiUiGenerator(openApi, (name, content) -> {
            writeFile(outputDir, name, content);
            generatedResources.produce(new GeneratedResourceBuildItem(
                    "META-INF/resources/openapi-ui/" + name, content));
        }).generate();
    }

    private static void writeFile(Path outputDir, String name, byte[] content) {
        try {
            var file = outputDir.resolve(name);
            Files.createDirectories(file.getParent());
            Files.write(file, content);
            log.debug("Wrote static resource: {}", file);
        } catch (IOException e) {
            throw new RuntimeException("could not write file: " + name, e);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
