package com.github.t1.openapi.ui.generator;

import com.github.t1.htmljava.Element;
import io.swagger.v3.oas.models.media.Schema;

import java.util.Map;

import static com.github.t1.bulmajava.basic.Color.PRIMARY;
import static com.github.t1.bulmajava.basic.Size.MEDIUM;
import static com.github.t1.bulmajava.components.Message.message;
import static com.github.t1.bulmajava.components.Message.messageBody;
import static com.github.t1.bulmajava.elements.Button.button;
import static com.github.t1.bulmajava.elements.Tag.tag;
import static com.github.t1.bulmajava.form.Field.field;
import static com.github.t1.bulmajava.form.Input.input;
import static com.github.t1.bulmajava.form.InputType.TEXT;
import static com.github.t1.bulmajava.form.Select.select;
import static com.github.t1.bulmajava.layout.Level.level;
import static com.github.t1.htmljava.HtmlBasics.code;
import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.htmljava.HtmlBasics.span;
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
                var requestBodyField = field("Request Body (application/json)");
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
                    requestBodyField.content(
                            div().classes("select", "is-small")
                                    .style("float: right; margin-top: -2rem")
                                    .content(exampleSelect));
                }
                requestBodyField.content(
                        element("textarea")
                                .attr("data-request-body", "true")
                                .classes("textarea", "is-family-code")
                                .attr("rows", "6")
                                .content(skeleton));
                fragment.content(requestBodyField);
            }
        }
        if (operation.getResponses() != null) {
            var response200 = operation.getResponses().get("200");
            if (response200 != null && response200.getContent() != null) {
                var contentTypes = response200.getContent().keySet();
                if (contentTypes.size() > 1) {
                    var sel = select("accept").attr("data-accept", "true");
                    for (var ct : contentTypes) sel.option(ct, ct);
                    fragment.content(field("Response Type").content(sel));
                }
                var jsonMedia = response200.getContent().get("application/json");
                if (jsonMedia != null && jsonMedia.getSchema() != null) {
                    @SuppressWarnings("unchecked")
                    var properties = (Map<String, Schema<?>>) jsonMedia.getSchema().getProperties();
                    if (properties != null) {
                        var sb = new StringBuilder();
                        sb.append("{\n");
                        var first = true;
                        for (var propEntry : properties.entrySet()) {
                            if (!first) sb.append(",\n");
                            first = false;
                            sb.append("  \"").append(propEntry.getKey()).append("\": ")
                                    .append(propEntry.getValue().getType());
                        }
                        sb.append("\n}");
                        fragment.content(element("pre").content(code(sb.toString())));
                    }
                }
            }
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
        if (schema.getExample() != null) {
            return formatSampleValue(schema.getType(), schema.getExample());
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
