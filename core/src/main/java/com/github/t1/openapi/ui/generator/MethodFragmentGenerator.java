package com.github.t1.openapi.ui.generator;

import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.t1.bulmajava.basic.Color.PRIMARY;
import static com.github.t1.bulmajava.basic.Size.MEDIUM;
import static com.github.t1.bulmajava.basic.Size.SMALL;
import static com.github.t1.bulmajava.components.Message.message;
import static com.github.t1.bulmajava.components.Message.messageBody;
import static com.github.t1.bulmajava.elements.Box.box;
import static com.github.t1.bulmajava.elements.Button.button;
import static com.github.t1.bulmajava.elements.Title.subtitle;
import static com.github.t1.bulmajava.basic.Color.DANGER;
import static com.github.t1.bulmajava.elements.Tag.tag;
import static com.github.t1.bulmajava.elements.Tag.tagsAddon;
import static com.github.t1.bulmajava.form.Field.field;
import static com.github.t1.bulmajava.form.Form.form;
import static com.github.t1.bulmajava.form.Input.input;
import static com.github.t1.bulmajava.form.Checkbox.checkbox;
import static com.github.t1.bulmajava.form.InputType.TEXT;
import static com.github.t1.bulmajava.form.Select.select;
import static com.github.t1.bulmajava.columns.Column.column;
import static com.github.t1.bulmajava.columns.Columns.columns;
import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.htmljava.HtmlBasics.p;
import static com.github.t1.htmljava.HtmlBasics.span;
import static com.github.t1.openapi.ui.components.SplitPane.splitPane;
import static com.github.t1.openapi.ui.generator.OpenApiUiGenerator.methodColor;

class MethodFragmentGenerator {
    static Element buildContent(OperationContext ctx) {
        var method = ctx.method();
        var operation = ctx.operation();
        var fullPath = ctx.fullPath();
        var displayPath = resolveDisplayPath(fullPath, operation);
        var summary = operation.getSummary() != null ? operation.getSummary() : "";
        var headingBadge = tag(method.name()).is(methodColor(method), MEDIUM);
        var headerRow = div().classes("is-flex", "is-align-items-center", "mb-5").style("gap:0.75rem").content(
                headingBadge,
                element("h2").classes("title", "is-4", "mb-0", "endpoint-path")
                        .content("/" + displayPath));
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
        var sendForm = form().attr("data-path", "/" + displayPath).attr("data-method", method.name());
        if (operation.getParameters() != null) {
            for (var param : operation.getParameters()) {
                var schema = param.getSchema();
                var enumValues = (schema != null) ? schema.getEnum() : null;
                var required = Boolean.TRUE.equals(param.getRequired());
                var badges = tagsAddon().content(tag(param.getIn())).classes("is-inline-flex", "ml-2");
                if (required) badges.content(tag("required").is(DANGER));
                var inputField = field().label(span(param.getName()), badges,
                        span().classes("checkbox", "is-size-7", "param-persist")
                                .content(element("input").attr("type", "checkbox").classes("param-persist-check"),
                                        span(" persist")));
                if (enumValues != null && !enumValues.isEmpty()) {
                    var sel = select(param.getName()).option("", "(any)");
                    sel.attr("data-param-in", param.getIn());
                    if (required) sel.attr("required", "");
                    for (var value : enumValues) {
                        sel.option(value.toString(), value.toString());
                    }
                    inputField.content(sel);
                } else if ("boolean".equals(schema != null ? schema.getType() : null)) {
                    inputField.content(checkbox().name(param.getName()).attr("data-param-in", param.getIn()));
                } else {
                    var inp = input(TEXT).attr("name", param.getName());
                    inp.attr("data-param-in", param.getIn());
                    if (required) inp.attr("required", "");
                    inputField.content(inp);
                }
                if (param.getDescription() != null) {
                    inputField.help(param.getDescription());
                }
                sendForm.content(inputField);
            }
        }
        var customHeaders = div().classes("custom-headers")
                .content(element("button").attr("type", "button").classes("custom-header-add")
                        .content("+ Add custom header"));
        sendForm.content(customHeaders);
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
                var bodyControls = div().classes("schema-box-controls");

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
                    bodyControls.content(span("Example").classes("schema-accept-label"));
                    bodyControls.content(div().classes("select", "is-small").content(exampleSelect));
                }
                bodyControls.content(element("button").attr("type", "button").classes("schema-toggle").content("Schema ▸"));
                var bodyHeader = div().classes("schema-box-header")
                        .content(bodyTitle, bodyControls);

                bodyBox.content(bodyHeader);

                var textarea = element("textarea")
                        .attr("data-request-body", "true")
                        .classes("textarea", "is-family-code")
                        .attr("rows", "6");
                if (Boolean.TRUE.equals(operation.getRequestBody().getRequired())) textarea.attr("required", "");
                textarea
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
                sendForm.content(bodyBox);
            }
        }
        if (operation.getResponses() != null) {
            var responseBox = buildResponseBox(operation.getResponses());
            if (responseBox != null) sendForm.content(responseBox);
        }
        var responseArea = div().classes("response-area");
        var sendRow = columns().classes("is-gapless").content(
                column().classes("is-narrow").content(
                        button("Send").is(PRIMARY).attr("type", "submit")),
                column().classes("has-text-right"));
        responseArea.content(sendRow);
        sendForm.content(responseArea);
        fragment.content(sendForm);
        return fragment;
    }

    static Map<String, String> buildResponseFragments(OperationContext ctx) {
        var operation = ctx.operation();
        if (operation.getResponses() == null) return Map.of();
        var fragments = new LinkedHashMap<String, String>();
        var method = ctx.method().name();
        for (var entry : operation.getResponses().entrySet()) {
            var code = entry.getKey();
            var response = entry.getValue();
            var panel = buildResponsePanel(code, response);
            fragments.put(method + "-response-" + code + ".html", panel.render());
        }
        fragments.put(method + "-response-fallback.html", buildFallbackPanel().render());
        return fragments;
    }

    private static Element buildResponsePanel(String code, io.swagger.v3.oas.models.responses.ApiResponse response) {
        var panel = div();
        var statusNum = Integer.parseInt(code);
        var statusClass = statusNum >= 200 && statusNum < 300 ? "is-success" : "is-error";
        var statusText = httpStatusText(statusNum);
        panel.content(columns().classes("is-gapless").content(
                column().classes("is-narrow").content(button("Send").is(PRIMARY).attr("type", "submit")),
                column().classes("has-text-right").content(
                        div().classes("response-info").content(
                                div().classes("response-info-top").content(
                                        span(code + " " + statusText).classes("response-status", statusClass),
                                        element("button").attr("type", "button").classes("response-headers-toggle").content("headers ▸")),
                                response.getDescription() != null
                                        ? div().classes("response-status-description").content(response.getDescription())
                                        : span()))));
        // documented headers
        var headersSection = box().classes("response-headers");
        if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
            var docGrid = div().classes("response-header-rows", "response-documented-headers");
            for (var headerEntry : response.getHeaders().entrySet()) {
                var headerObj = headerEntry.getValue();
                var nameEl = span(headerEntry.getKey()).classes("response-header-name");
                if (Boolean.TRUE.equals(headerObj.getDeprecated())) nameEl.classes("is-deprecated");
                docGrid.content(nameEl);
                docGrid.content(span().classes("response-header-value").attr("data-header", headerEntry.getKey().toLowerCase()));
                if (headerObj.getDescription() != null) {
                    docGrid.content(span());
                    docGrid.content(span(headerObj.getDescription()).classes("response-header-description")
                            .attr("data-header", headerEntry.getKey().toLowerCase()));
                }
            }
            headersSection.content(docGrid);
        }
        headersSection.content(div().classes("response-header-rows", "response-headers-undocumented"));
        panel.content(headersSection);
        panel.content(element("pre").classes("response", "box"));
        return panel;
    }

    private static Element buildFallbackPanel() {
        var panel = div();
        panel.content(columns().classes("is-gapless").content(
                column().classes("is-narrow").content(button("Send").is(PRIMARY).attr("type", "submit")),
                column().classes("has-text-right").content(
                        div().classes("response-info").content(
                                div().classes("response-info-top").content(
                                        span().classes("response-status"),
                                        element("button").attr("type", "button").classes("response-headers-toggle").content("headers ▸"))))));
        panel.content(box().classes("response-headers").content(
                div().classes("response-header-rows")));
        panel.content(element("pre").classes("response", "box"));
        return panel;
    }

    private static String httpStatusText(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> String.valueOf(code);
        };
    }

    private static Renderable buildResponseBox(ApiResponses responses) {
        // collect status codes that have content, headers, or description
        var statusCodes = responses.entrySet().stream()
                .filter(e -> e.getValue().getContent() != null
                        || e.getValue().getHeaders() != null
                        || e.getValue().getDescription() != null)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (statusCodes.isEmpty()) return null;

        var hasSchemaProperties = statusCodes.stream()
                .filter(code -> responses.get(code).getContent() != null)
                .anyMatch(code -> responses.get(code).getContent().values().stream()
                        .anyMatch(mt -> mt.getSchema() != null && mt.getSchema().getProperties() != null));
        var hasHeaders = statusCodes.stream()
                .anyMatch(code -> responses.get(code).getHeaders() != null && !responses.get(code).getHeaders().isEmpty());
        var hasExpandableContent = hasSchemaProperties || hasHeaders;

        var allContentTypes = responses.values().stream()
                .filter(r -> r.getContent() != null)
                .flatMap(r -> r.getContent().keySet().stream())
                .distinct()
                .toList();
        if (!hasExpandableContent && allContentTypes.size() <= 1) return null;

        var responseBox = box().classes("schema-box", "is-collapsed").attr("data-box", "response");

        // header: title on the left, Accept select + Schema toggle on the right
        var title = div().classes("schema-box-title")
                .content(subtitle(6, "Response"));
        var header = div().classes("schema-box-header").content(title);
        var controls = div().classes("schema-box-controls");
        if (allContentTypes.size() > 1) {
            controls.content(span("Accept").classes("schema-accept-label"));
            var sel = select("accept").is(SMALL).attr("data-accept", "true");
            for (var ct : allContentTypes) sel.option(ct, ct);
            controls.content(sel);
        }
        if (hasExpandableContent) {
            controls.content(element("button").attr("type", "button").classes("schema-toggle").content("Schema ▸"));
        }
        header.content(controls);
        responseBox.content(header);

        if (!hasExpandableContent) return responseBox;

        // content: status code tabs + panels
        var content = div().classes("schema-box-content");

        // status code tabs
        var tabs = div().classes("schema-status-tabs");
        var isFirst = true;
        for (var code : statusCodes) {
            var tab = span(code).classes("schema-status-tab").attr("tabindex", "0").attr("data-status", code);
            if (isFirst) {
                tab.classes("is-active");
                isFirst = false;
            }
            tabs.content(tab);
        }
        content.content(tabs);

        // panels per status code
        var isFirstPanel = true;
        for (var code : statusCodes) {
            var response = responses.get(code);
            var panel = div().classes("schema-status-panel").attr("data-status", code);
            if (!isFirstPanel) panel.style("display:none");
            isFirstPanel = false;

            // response description
            if (response.getDescription() != null) {
                panel.content(p(response.getDescription()).classes("schema-response-description"));
            }

            // documented headers
            var responseHasHeaders = response.getHeaders() != null && !response.getHeaders().isEmpty();
            var responseHasBody = response.getContent() != null
                    && response.getContent().values().iterator().next().getSchema() != null;
            if (responseHasHeaders) {
                if (responseHasBody) panel.content(subtitle(6, "Headers").classes("schema-section-title"));
                var headersSection = div().classes("schema-response-headers");
                var headerProps = div().classes("schema-props");
                for (var entry : response.getHeaders().entrySet()) {
                    var headerObj = entry.getValue();
                    var nameEl = span(entry.getKey()).classes("schema-prop-name");
                    var details = span().classes("schema-prop-details");
                    if (headerObj.getSchema() != null && headerObj.getSchema().getType() != null) {
                        details.content(span(headerObj.getSchema().getType()).classes("schema-prop-type"));
                    }
                    if (Boolean.TRUE.equals(headerObj.getRequired())) {
                        details.content(tag("required").is(DANGER));
                    }
                    if (Boolean.TRUE.equals(headerObj.getDeprecated())) {
                        details.content(tag("deprecated").classes("is-warning"));
                    }
                    if (headerObj.getDescription() != null) {
                        details.content(span(headerObj.getDescription()).classes("schema-prop-desc"));
                    }
                    headerProps.content(nameEl, details);
                }
                headersSection.content(headerProps);
                panel.content(headersSection);
            }

            // body schema properties
            if (responseHasBody) {
                if (responseHasHeaders) panel.content(subtitle(6, "Body").classes("schema-section-title"));
                var mediaType = response.getContent().values().iterator().next();
                addSchemaContent(panel, mediaType.getSchema());
            }
            content.content(panel);
        }

        responseBox.content(content);
        return responseBox;
    }


    @SuppressWarnings("rawtypes")
    private static void addSchemaContent(Element container, Schema<?> schema) {
        addSchemaContent(container, schema, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @SuppressWarnings("rawtypes")
    private static void addSchemaContent(Element container, Schema<?> schema, Set<Schema<?>> visited) {
        if (!visited.add(schema)) return; // cycle detection
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
                addPropertyRow(table, prop.getKey(), prop.getValue(), required, visited);
            }
            container.content(table);
        }
    }

    @SuppressWarnings("rawtypes")
    private static void addPropertyRow(Element table, String name, Schema<?> propSchema, List<String> required, Set<Schema<?>> visited) {
        var type = propSchema.getType() != null ? propSchema.getType() : "object";
        if (propSchema.getEnum() != null && !propSchema.getEnum().isEmpty()) type = "enum";

        // determine if this property has nested sub-properties
        Schema<?> nestedSchema = null;
        if ("object".equals(type) && propSchema.getProperties() != null) {
            nestedSchema = propSchema;
        } else if ("array".equals(type) && propSchema.getItems() != null && propSchema.getItems().getProperties() != null) {
            nestedSchema = propSchema.getItems();
        }
        var hasNested = nestedSchema != null && !visited.contains(nestedSchema);

        var nameSpan = span(name).classes("schema-prop-name").attr("data-prop", name);
        if (hasNested) {
            nameSpan.content(element("button").attr("type", "button").classes("schema-nested-toggle").attr("aria-expanded", "false").content("\u25B6"));
        }
        table.content(nameSpan);

        var details = span().classes("schema-prop-details");
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

        if (hasNested) {
            var nestedContent = div().classes("schema-nested");
            addSchemaContent(nestedContent, nestedSchema, visited);
            table.content(nestedContent);
        }
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

    /** Replace unified path param segments with actual parameter names from the operation. */
    private static String resolveDisplayPath(String fullPath, io.swagger.v3.oas.models.Operation operation) {
        if (operation.getParameters() == null) return fullPath;
        var pathParams = operation.getParameters().stream()
                .filter(p -> "path".equals(p.getIn()))
                .toList();
        var segments = fullPath.split("/");
        var paramIndex = 0;
        for (var i = 0; i < segments.length; i++) {
            if (segments[i].startsWith("{") && segments[i].endsWith("}") && paramIndex < pathParams.size()) {
                segments[i] = "{" + pathParams.get(paramIndex).getName() + "}";
                paramIndex++;
            }
        }
        return String.join("/", segments);
    }
}
