package com.github.t1.openapi.ui.quarkus.deployment;

import com.github.t1.openapi.ui.generator.OpenApiUiGenerator;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.smallrye.openapi.deployment.spi.OpenApiDocumentBuildItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
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

    private static final String RESOURCE_PREFIX = "META-INF/resources/openapi-ui/";

    @BuildStep
    void generateOpenApiUi(
            List<OpenApiDocumentBuildItem> openApiDocuments,
            BuildProducer<GeneratedResourceBuildItem> generatedResources,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResources)
            throws IOException {

        if (openApiDocuments.isEmpty()) {
            log.info("No OpenAPI document found — skipping OpenAPI UI generation");
            return;
        }

        // Use the first (default) document
        var defaultDoc = openApiDocuments.getFirst();

        log.info("Generating OpenAPI UI from OpenAPI document");

        var openApi = defaultDoc.getSmallRyeOpenAPI().model();
        var generator = new OpenApiUiGenerator(openApi, Path.of("unused"));
        var files = generator.generateToMemory();

        log.info("Generated {} OpenAPI UI files", files.size());

        // Produce GeneratedResourceBuildItem for each file
        for (var entry : files.entrySet()) {
            var resourcePath = RESOURCE_PREFIX + entry.getKey();
            log.debug("Producing resource: {}", resourcePath);
            generatedResources.produce(new GeneratedResourceBuildItem(resourcePath, entry.getValue()));
            nativeImageResources.produce(new NativeImageResourceBuildItem(resourcePath));
        }
    }
}
