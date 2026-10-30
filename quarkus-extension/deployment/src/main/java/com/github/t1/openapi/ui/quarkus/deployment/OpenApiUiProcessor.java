package com.github.t1.openapi.ui.quarkus.deployment;

import com.github.t1.openapi.ui.generator.OpenApiUiGenerator;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.smallrye.openapi.deployment.spi.OpenApiDocumentBuildItem;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
            BuildProducer<GeneratedStaticResourceBuildItem> generatedStaticResources)
            throws IOException {

        if (openApiDocuments.isEmpty()) {
            log.info("No OpenAPI document found — skipping OpenAPI UI generation");
            return;
        }

        log.info("Generating OpenAPI UI from OpenAPI document");

        // We can't write into the app's classes dir, as Quarkus build steps scan it concurrently;
        // the generated-static-resource mechanism serves the UI from memory instead.
        var openApi = openApiDocuments.getFirst().getSmallRyeOpenAPI().model();
        new OpenApiUiGenerator(openApi, (name, content) ->
                generatedStaticResources.produce(new GeneratedStaticResourceBuildItem(ENDPOINT_PREFIX + name, content))
        ).generate();
    }
}
