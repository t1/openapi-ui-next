package com.github.t1.openapi.ui;

import io.swagger.v3.parser.OpenAPIV3Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.t1.bulmajava.layout.Container.container;
import static com.github.t1.bulmajava.layout.Section.section;
import static com.github.t1.htmljava.Html.html;
import static com.github.t1.htmljava.HtmlBasics.li;
import static com.github.t1.htmljava.HtmlBasics.ul;

public class OpenApiUiGenerator {
    private final Path specFile;
    private final Path outputDir;

    public OpenApiUiGenerator(Path specFile, Path outputDir) {
        this.specFile = specFile;
        this.outputDir = outputDir;
    }

    public void generate() throws IOException {
        var openApi = new OpenAPIV3Parser().read(specFile.toString());
        var list = ul();
        for (var pathEntry : openApi.getPaths().entrySet()) {
            var path = pathEntry.getKey();
            var pathItem = pathEntry.getValue();
            for (var opEntry : pathItem.readOperationsMap().entrySet()) {
                var method = opEntry.getKey();
                var operation = opEntry.getValue();
                var summary = operation.getSummary() != null ? operation.getSummary() : "";
                list.content(li(method + " " + path + " — " + summary));
            }
        }

        var title = openApi.getInfo().getTitle();
        var page = html(title).body(section().content(container().content(list)));

        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("index.html"), page.render());
    }
}
