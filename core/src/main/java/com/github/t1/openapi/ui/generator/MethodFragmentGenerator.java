package com.github.t1.openapi.ui.generator;

import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Map;

import static com.github.t1.bulmajava.basic.Color.PRIMARY;
import static com.github.t1.bulmajava.basic.Size.MEDIUM;
import static com.github.t1.bulmajava.components.Message.message;
import static com.github.t1.bulmajava.components.Message.messageBody;
import static com.github.t1.bulmajava.elements.Box.box;
import static com.github.t1.bulmajava.elements.Button.button;
import static com.github.t1.bulmajava.elements.Title.subtitle;
import static com.github.t1.bulmajava.elements.Tag.tag;
import static com.github.t1.bulmajava.form.Field.field;
import static com.github.t1.bulmajava.form.Input.input;
import static com.github.t1.bulmajava.form.InputType.TEXT;
import static com.github.t1.bulmajava.form.Select.select;
import static com.github.t1.bulmajava.layout.Level.level;
import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.htmljava.HtmlBasics.span;
import static com.github.t1.openapi.ui.components.SplitPane.splitPane;
import static com.github.t1.openapi.ui.generator.OpenApiUiGenerator.methodColor;

class MethodFragmentGenerator {
    static Element buildContent(OperationContext ctx) {
        var method = ctx.method();
        var operation = ctx.operation();
        var fullPath = ctx.fullPath();
        var summary = operation.getSummary() != null ? operation.getSummary() : "";
        var headingBadge = tag(method.name()).is(methodColor(method), MEDIUM);
        var headerRow = div().classes("is-flex", "is-align-items-center", "mb-5").style("gap:0.75rem").content(
                headingBadge,
                element("h2").classes("title", "is-4", "mb-0", "endpoint-path")
                        .content("/" + fullPath));
        var hasTags = operation.getTags() != null && !operation.getTags().isEmpty();
        var isDeprecated = Boolean.TRUE.equals(operation.getDeprecated());
        if (hasTags || isDeprecated) {
            var tagsRow = div().classes("tags").style("margin-left:auto");
            if (hasTags) {
                for (var t : operation.getTags()) {
                    tagsRow.content(span(t).classes("tag", "op-tag"));
                }
            }
            if (isDeprecated) {
                tagsRow.content(span("DEPRECATED").classes("tag", "is-warning", "deprecated-badge"));
            }
            headerRow.content(tagsRow);
        }
        var descriptionSpan = span().classes("op-description").content(
                element("strong").content(summary));
        if (operation.getDescription() != null) {
            descriptionSpan.content(" — " + operation.getDescription());
        }
        var descriptionWrapper = div().classes("op-description-wrapper").content(
                descriptionSpan,
                element("button").classes("desc-toggle")
                        .attr("tabindex", "0")
                        .attr("aria-label", "Expand description")
                        .content(span("▸")));
        var fragment = div().content(
                headerRow,
                message().content(messageBody().content(descriptionWrapper))
        );
        if (operation.getExternalDocs() != null) {
            fragment.content(div().classes("external-docs").content(
                    element("a").attr("href", operation.getExternalDocs().getUrl())
                            .attr("target", "_blank")
                            .content("External docs")));
        }
        if (operation.getParameters() != null) {
            for (var param : operation.getParameters()) {
                var schema = param.getSchema();
                var enumValues = (schema != null) ? schema.getEnum() : null;
                var inputField = field(param.getName());
                if (enumValues != null && !enumValues.isEmpty()) {
                    var sel = select(param.getName()).option("", "(any)");
                    for (var value : enumValues) {
                        sel.option(value.toString(), value.toString());
                    }
                    inputField.content(sel);
                } else {
                    inputField.content(input(TEXT).attr("name", param.getName()));
                }
                if (param.getDescription() != null) {
                    inputField.help(param.getDescription());
                }
                fragment.content(inputField);
            }
        }
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            var content = operation.getRequestBody().getContent();
            var jsonContent = content.get("application/json");
            if (jsonContent == null) jsonContent = content.get("*/*");
            if (jsonContent != null && jsonContent.getSchema() != null) {
                var skeleton = generateJsonSkeleton(jsonContent.getSchema());
                if ("{}".equals(skeleton)) skeleton = mediaTypeExample(jsonContent);

                var bodyBox = box();
                bodyBox.classes("schema-box", "is-collapsed").attr("data-box", "body");
                var bodyTitle = div().classes("schema-box-title")
                        .content(subtitle(6, "Request Body"));
                var bodyHeader = div().classes("schema-box-header")
                        .content(bodyTitle, element("button").classes("schema-toggle").content("Schema ▸"));

                if (jsonContent.getExamples() != null && jsonContent.getExamples().size() > 1) {
                    var exampleSelect = element("select")
                            .attr("data-example-select", "true");
                    for (var entry : jsonContent.getExamples().entrySet()) {
                        var value = entry.getValue().getValue();
                        var formatted = (value instanceof com.fasterxml.jackson.databind.JsonNode node)
                                ? node.toPrettyString() : value.toString();
                        var label = entry.getValue().getSummary() != null
                                ? entry.getValue().getSummary() : entry.getKey();
                        exampleSelect.content(element("option")
                                .attr("value", formatted)
                                .content(label));
                    }
                    bodyTitle.content(div().classes("select", "is-small").content(exampleSelect));
                }

                bodyBox.content(bodyHeader);

                var textarea = element("textarea")
                        .attr("data-request-body", "true")
                        .classes("textarea", "is-family-code")
                        .attr("rows", "6")
                        .content(skeleton);

                var schema = jsonContent.getSchema();
                if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
                    var treeContent = div();
                    addSchemaContent(treeContent, schema);
                    var tree = div().classes("schema-box-tree", "schema-box-content").content(treeContent);
                    bodyBox.content(splitPane().ratio(1, 1).first(textarea).second(tree));
                } else {
                    bodyBox.content(textarea);
                }
                fragment.content(bodyBox);
            }
        }
        if (operation.getResponses() != null) {
            var responseBox = buildResponseBox(operation.getResponses());
            if (responseBox != null) fragment.content(responseBox);
        }
        var sendRow = level().content(
                div().classes("level-left").content(
                        button("Send").is(PRIMARY)
                                .attr("data-path", "/" + fullPath)
                                .attr("data-method", method.name())),
                div().classes("level-right"));
        fragment.content(sendRow);
        return fragment;
    }

    private static Renderable buildResponseBox(io.swagger.v3.oas.models.responses.ApiResponses responses) {
        // collect status codes that have content
        var statusCodes = responses.entrySet().stream()
                .filter(e -> e.getValue().getContent() != null)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (statusCodes.isEmpty()) return null;
        var hasSchemaProperties = statusCodes.stream()
                .anyMatch(code -> responses.get(code).getContent().values().stream()
                        .anyMatch(mt -> mt.getSchema() != null && mt.getSchema().getProperties() != null));

        var responseBox = box().classes("schema-box", "is-collapsed").attr("data-box", "response");

        // header: title on the left, Accept select + Schema toggle on the right
        var title = div().classes("schema-box-title")
                .content(subtitle(6, "Response Body"));
        var header = div().classes("schema-box-header").content(title);
        // collect all content types across all status codes
        var allContentTypes = responses.values().stream()
                .filter(r -> r.getContent() != null)
                .flatMap(r -> r.getContent().keySet().stream())
                .distinct()
                .toList();
        var controls = div().classes("schema-box-controls");
        if (allContentTypes.size() > 1) {
            controls.content(span("Accept").classes("schema-accept-label"));
            var sel = select("accept").attr("data-accept", "true");
            for (var ct : allContentTypes) sel.option(ct, ct);
            controls.content(sel);
        }
        if (hasSchemaProperties) {
            controls.content(element("button").classes("schema-toggle").content("Schema ▸"));
        }
        header.content(controls);
        responseBox.content(header);

        if (!hasSchemaProperties) return responseBox;

        // content: status code tabs + property panels
        var content = div().classes("schema-box-content");

        // status code tabs
        var tabs = div().classes("schema-status-tabs");
        var isFirst = true;
        for (var code : statusCodes) {
            var tab = span(code).classes("schema-status-tab").attr("tabindex", "0");
            if (isFirst) {
                tab.classes("is-active");
                isFirst = false;
            }
            tabs.content(tab);
        }
        content.content(tabs);

        // property panels per status code
        var isFirstPanel = true;
        for (var code : statusCodes) {
            var response = responses.get(code);
            var panel = div().classes("schema-status-panel").attr("data-status", code);
            if (!isFirstPanel) panel.style("display:none");
            isFirstPanel = false;

            // use first available content type's schema
            var mediaType = response.getContent().values().iterator().next();
            if (mediaType.getSchema() != null) {
                var schema = mediaType.getSchema();
                addSchemaContent(panel, schema);
            }
            content.content(panel);
        }

        responseBox.content(content);
        return responseBox;
    }

    @SuppressWarnings("rawtypes")
    private static void addSchemaContent(Element container, Schema<?> schema) {
        if (schema.getTitle() != null) {
            var titleBar = div().classes("schema-title");
            titleBar.content(span(schema.getTitle()).classes("schema-title-name"));
            if (schema.getDescription() != null) {
                titleBar.content(span("— " + schema.getDescription()).classes("schema-title-desc"));
            }
            container.content(titleBar);
        }
        var required = schema.getRequired() != null ? schema.getRequired() : List.<String>of();
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            var table = div().classes("schema-props");
            for (var prop : properties.entrySet()) {
                addPropertyRow(table, prop.getKey(), prop.getValue(), required);
            }
            container.content(table);
        }
    }

    private static void addPropertyRow(Element table, String name, Schema<?> propSchema, List<String> required) {
        table.content(span(name).classes("schema-prop-name").attr("data-prop", name));
        var details = span().classes("schema-prop-details");
        var type = propSchema.getType() != null ? propSchema.getType() : "object";
        if (propSchema.getEnum() != null && !propSchema.getEnum().isEmpty()) type = "enum";
        details.content(span(type).classes("schema-prop-type"));
        if (required.contains(name)) {
            details.content(span("required").classes("schema-prop-required"));
        }
        var example = propSchema.getExample();
        if (example == null && propSchema.getExamples() != null && !propSchema.getExamples().isEmpty()) {
            example = propSchema.getExamples().getFirst();
        }
        if (example != null) {
            details.content(span("e.g. " + example).classes("schema-prop-example"));
        } else if (propSchema.getEnum() != null && !propSchema.getEnum().isEmpty()) {
            var values = String.join(" | ", propSchema.getEnum().stream().map(Object::toString).toList());
            details.content(span(values).classes("schema-prop-example"));
        }
        if (propSchema.getDescription() != null) {
            details.content(span(propSchema.getDescription()).classes("schema-prop-desc"));
        }
        table.content(details);
    }

    @SuppressWarnings("rawtypes")
    static String generateJsonSkeleton(Schema<?> schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) return "{}";
        var sb = new StringBuilder("{\n");
        var first = true;
        for (var entry : properties.entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("  \"").append(entry.getKey()).append("\": ");
            sb.append(sampleValue(entry.getValue()));
        }
        sb.append("\n}");
        return sb.toString();
    }

    static String sampleValue(Schema<?> schema) {
        var example = schema.getExample();
        if (example == null && schema.getExamples() != null && !schema.getExamples().isEmpty()) {
            example = schema.getExamples().getFirst();
        }
        if (example != null) {
            return formatSampleValue(schema.getType(), example);
        }
        if (schema.getDefault() != null) {
            return formatSampleValue(schema.getType(), schema.getDefault());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return formatSampleValue(schema.getType(), schema.getEnum().getFirst());
        }
        if (schema.getFormat() != null) {
            var formatted = formatBasedSample(schema.getFormat());
            if (formatted != null) return "\"" + formatted + "\"";
        }
        var type = schema.getType();
        if (type == null) return "null";
        return switch (type) {
            case "string" -> "\"\"";
            case "integer", "number" -> "0";
            case "boolean" -> "false";
            case "array" -> "[]";
            case "object" -> "{}";
            default -> "null";
        };
    }

    static String mediaTypeExample(io.swagger.v3.oas.models.media.MediaType mediaType) {
        var example = mediaType.getExample();
        if (example == null && mediaType.getExamples() != null)
            example = mediaType.getExamples().values().iterator().next().getValue();
        if (example == null) return "{}";
        if (example instanceof com.fasterxml.jackson.databind.JsonNode node)
            return node.toPrettyString();
        return example.toString();
    }

    private static String formatSampleValue(String type, Object value) {
        var quote = type == null || "string".equals(type);
        return quote ? "\"" + value + "\"" : value.toString();
    }

    private static String formatBasedSample(String format) {
        return switch (format) {
            case "date" -> "2024-01-15";
            case "date-time" -> "2024-01-15T12:00:00Z";
            case "email" -> "user@example.com";
            case "uri", "url" -> "https://example.com";
            case "uuid" -> "3fa85f64-5717-4562-b3fc-2c963f66afa6";
            default -> null;
        };
    }
}
