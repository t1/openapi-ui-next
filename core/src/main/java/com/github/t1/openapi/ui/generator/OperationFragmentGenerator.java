package com.github.t1.openapi.ui.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.t1.bulmajava.elements.Box;
import com.github.t1.bulmajava.form.Form;
import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.t1.bulmajava.basic.Color.DANGER;
import static com.github.t1.bulmajava.basic.Color.PRIMARY;
import static com.github.t1.bulmajava.basic.Size.MEDIUM;
import static com.github.t1.bulmajava.basic.Size.NORMAL;
import static com.github.t1.bulmajava.basic.Size.SMALL;
import static com.github.t1.bulmajava.basic.Style.FULLWIDTH;
import static com.github.t1.bulmajava.columns.Column.column;
import static com.github.t1.bulmajava.columns.Columns.columns;
import static com.github.t1.bulmajava.components.Message.message;
import static com.github.t1.bulmajava.components.Message.messageBody;
import static com.github.t1.bulmajava.elements.Box.box;
import static com.github.t1.bulmajava.elements.Button.button;
import static com.github.t1.bulmajava.elements.Tag.tag;
import static com.github.t1.bulmajava.elements.Tag.tagsAddon;
import static com.github.t1.bulmajava.basic.Color.WARNING;
import static com.github.t1.bulmajava.elements.Title.subtitle;
import static com.github.t1.bulmajava.elements.Title.title;
import static com.github.t1.bulmajava.form.Field.field;
import static com.github.t1.bulmajava.form.Form.form;
import static com.github.t1.bulmajava.form.Input.input;
import static com.github.t1.bulmajava.form.InputType.TEXT;
import static com.github.t1.bulmajava.form.Select.select;
import static com.github.t1.bulmajava.form.Textarea.textarea;
import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.htmljava.HtmlBasics.p;
import static com.github.t1.htmljava.HtmlBasics.span;
import static com.github.t1.htmljava.HtmlBasics.strong;
import static com.github.t1.openapi.ui.components.SplitPane.splitPane;
import static com.github.t1.openapi.ui.generator.OpenApiUiGenerator.methodColor;
import static java.lang.Boolean.TRUE;

class OperationFragmentGenerator {
    private final HttpMethod method;
    private final io.swagger.v3.oas.models.Operation operation;
    private final ApiPath path;
    private final ApiPath displayPath;

    private OperationFragmentGenerator(Operation operation) {
        this.method = operation.method();
        this.operation = operation.spec();
        this.path = operation.path();
        this.displayPath = path.withResolvedParams(this.operation);
    }

    static Element operationFragment(Operation operation) {
        return new OperationFragmentGenerator(operation).fragment();
    }

    static Map<String, String> responseFragments(Operation operation) {
        return new OperationFragmentGenerator(operation).responseFragmentFiles();
    }

    private Element fragment() {
        var fragment = div().content(
                headerRow(),
                message().content(messageBody().content(description())));
        if (operation.getExternalDocs() != null) {
            fragment.content(div().classes("external-docs").content(
                    element("a").attr("href", operation.getExternalDocs().getUrl())
                            .attr("target", "_blank")
                            .content("External docs")));
        }
        fragment.content(operationForm());
        return fragment;
    }

    private Form operationForm() {
        var operationForm = form().attr("data-path", displayPath.display()).attr("data-fragment-path", path.toString()).attr("data-method", method.name());
        if (operation.getOperationId() != null) operationForm.attr("data-operation-id", operation.getOperationId());
        parameterFields(operationForm);
        operationForm.content(div().classes("custom-headers")
                .content(element("button").attr("type", "button").classes("custom-header-add")
                        .content("+ Add custom header")));
        requestBodySection(operationForm);
        if (operation.getResponses() != null) {
            var responseBox = responseBox(operation.getResponses());
            if (responseBox != null) operationForm.content(responseBox);
        }
        var responseArea = div().classes("response-area");
        responseArea.content(columns().classes("is-gapless").content(
                column().classes("is-narrow").content(
                        button("Send").is(PRIMARY).attr("type", "submit")),
                column().classes("has-text-right")));
        operationForm.content(responseArea);
        return operationForm;
    }

    private Element headerRow() {
        var headingBadge = tag(method.name()).is(methodColor(method), MEDIUM);
        var headerRow = div().classes("is-flex", "is-align-items-center", "mb-5").style("gap:0.75rem").content(
                headingBadge,
                title(4, displayPath.display()).classes("mb-0", "endpoint-path"));
        var hasTags = operation.getTags() != null && !operation.getTags().isEmpty();
        var isDeprecated = TRUE == operation.getDeprecated();
        if (hasTags || isDeprecated) {
            var tagsRow = div().classes("tags").style("margin-left:auto");
            if (hasTags) {
                for (var tagName : operation.getTags()) {
                    tagsRow.content(tag(tagName).classes("op-tag"));
                }
            }
            if (isDeprecated) {
                tagsRow.content(tag("DEPRECATED").is(WARNING).classes("deprecated-badge"));
            }
            headerRow.content(tagsRow);
        }
        return headerRow;
    }

    private Element description() {
        var summary = operation.getSummary() != null ? operation.getSummary() : "";
        var descriptionSpan = span().classes("op-description").content(strong(summary));
        if (operation.getDescription() != null) {
            descriptionSpan.content(" — " + operation.getDescription());
        }
        return div().classes("op-description-wrapper").content(
                descriptionSpan,
                element("button").classes("desc-toggle")
                        .attr("tabindex", "0")
                        .attr("aria-label", "Expand description")
                        .content(span("▶")));
    }

    private void parameterFields(Form operationForm) {
        if (operation.getParameters() == null) return;
        for (var param : operation.getParameters()) {
            var badges = tagsAddon().content(tag(param.getIn())).classes("is-inline-flex", "ml-2");
            if (TRUE == param.getRequired()) badges.content(tag("required").is(DANGER));
            var inputField = field().label(span(param.getName()), badges);
            inputField.content(parameterInput(param));
            inputField.iconRight("thumbtack");
            parameterHelp(inputField, param);
            operationForm.content(inputField);
        }
    }

    private Renderable parameterInput(io.swagger.v3.oas.models.parameters.Parameter param) {
        var schema = param.getSchema();
        var enumValues = (schema != null) ? schema.getEnum() : null;
        var required = TRUE == param.getRequired();
        if (enumValues != null && !enumValues.isEmpty()) {
            return selectInput(param, enumValues, required);
        } else if ("boolean".equals(schema != null ? schema.getType() : null)) {
            return checkboxInput(param);
        } else {
            return textInput(param, required);
        }
    }

    private Renderable selectInput(io.swagger.v3.oas.models.parameters.Parameter param, List<?> enumValues, boolean required) {
        var sel = select(param.getName()).is(FULLWIDTH).option("", "(any)");
        sel.attr("data-param-in", param.getIn());
        if (required) sel.attr("required", "");
        for (var value : enumValues) {
            sel.option(value.toString(), value.toString());
        }
        return sel;
    }

    private Renderable checkboxInput(io.swagger.v3.oas.models.parameters.Parameter param) {
        var cb = element("input").attr("type", "checkbox")
                .attr("name", param.getName()).attr("data-param-in", param.getIn());
        return element("label").classes("checkbox").content(cb);
    }

    private Renderable textInput(io.swagger.v3.oas.models.parameters.Parameter param, boolean required) {
        var inp = input(TEXT).attr("name", param.getName());
        inp.attr("data-param-in", param.getIn());
        if (required) inp.attr("required", "");
        return inp;
    }

    private void parameterHelp(com.github.t1.bulmajava.form.Field inputField, io.swagger.v3.oas.models.parameters.Parameter param) {
        if ("cookie".equals(param.getIn())) {
            var help = p().classes("help");
            if (param.getDescription() != null) help.content(span(param.getDescription() + " — "));
            help.content(element("em").classes("cookie-notice")
                    .content("the browser manages this automatically in try mode"));
            inputField.content(help);
        } else if (param.getDescription() != null) {
            inputField.help(param.getDescription());
        }
    }

    private void requestBodySection(Form operationForm) {
        var jsonContent = resolveJsonContent();
        if (jsonContent == null) return;
        var skeleton = skeleton(jsonContent);
        if ("{}".equals(skeleton)) return;

        var bodyBox = box();
        bodyBox.classes("schema-box", "flat-box", "is-collapsed").attr("data-box", "body");
        bodyBox.content(requestBodyHeader(jsonContent));
        bodyBox.content(requestBodyEditor(skeleton, jsonContent.getSchema()));
        operationForm.content(bodyBox);
    }

    private MediaType resolveJsonContent() {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) return null;
        var content = operation.getRequestBody().getContent();
        var jsonContent = content.get("application/json");
        if (jsonContent == null) jsonContent = content.get("*/*");
        if (jsonContent == null || jsonContent.getSchema() == null) return null;
        return jsonContent;
    }

    private String skeleton(MediaType jsonContent) {
        var skeleton = JsonSkeletonGenerator.generate(jsonContent.getSchema());
        if ("{}".equals(skeleton)) skeleton = JsonSkeletonGenerator.mediaTypeExample(jsonContent);
        return skeleton;
    }

    private Element requestBodyHeader(MediaType jsonContent) {
        var bodyTitle = div().classes("schema-box-title")
                .content(subtitle(6, "Request Body"));
        var bodyControls = div().classes("schema-box-controls");
        if (jsonContent.getExamples() != null && jsonContent.getExamples().size() > 1) {
            bodyControls.content(span("Example").classes("schema-accept-label"));
            bodyControls.content(div().classes("select", "is-small").content(exampleSelect(jsonContent)));
        }
        var schema = jsonContent.getSchema();
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            bodyControls.content(element("button").attr("type", "button").classes("schema-toggle").content("Schema ▶"));
        }
        return div().classes("schema-box-header").content(bodyTitle, bodyControls);
    }

    private Element exampleSelect(MediaType jsonContent) {
        var select = element("select").attr("data-example-select", "true");
        for (var entry : jsonContent.getExamples().entrySet()) {
            var value = entry.getValue().getValue();
            var formatted = (value instanceof JsonNode node)
                    ? node.toPrettyString() : value.toString();
            var label = entry.getValue().getSummary() != null
                    ? entry.getValue().getSummary() : entry.getKey();
            select.content(element("option").attr("value", formatted).content(label));
        }
        return select;
    }

    private Renderable requestBodyEditor(String skeleton, Schema<?> schema) {
        var textareaEl = textarea()
                .attr("data-request-body", "true")
                .classes("is-family-code");
        if (TRUE == operation.getRequestBody().getRequired()) textareaEl.attr("required", "");
        textareaEl.content(skeleton);
        var hasProperties = schema.getProperties() != null && !schema.getProperties().isEmpty();
        if (hasProperties) {
            var treeContent = div();
            new SchemaRenderer().render(treeContent, schema);
            var tree = div().classes("schema-box-tree", "schema-box-content").content(treeContent);
            return splitPane().ratio(1, 1).first(textareaEl).second(tree);
        }
        return textareaEl;
    }

    private Map<String, String> responseFragmentFiles() {
        if (operation.getResponses() == null) return Map.of();
        var fragments = new LinkedHashMap<String, String>();
        for (var entry : operation.getResponses().entrySet()) {
            var code = entry.getKey();
            var response = entry.getValue();
            var panel = responsePanel(code, response);
            fragments.put(method.name() + "-response-" + code + ".html", panel.render());
        }
        fragments.put(method.name() + "-response-fallback.html", fallbackPanel().render());
        return fragments;
    }

    private Element responsePanel(String code, ApiResponse response) {
        var status = StatusCode.of(code);
        var responseInfo = div().classes("response-info").content(
                div().classes("response-info-top").content(
                        span(status.label()).classes("response-status", status.cssClass())),
                response.getDescription() != null
                        ? div().classes("response-status-description").content(response.getDescription())
                        : span());
        return panelSkeleton(responseInfo, documentedHeaders(response));
    }

    private Element fallbackPanel() {
        var responseInfo = div().classes("response-info").content(
                div().classes("response-info-top").content(
                        span().classes("response-status")));
        return panelSkeleton(responseInfo, null);
    }

    private Element panelSkeleton(Element responseInfo, Element documentedHeaders) {
        var panel = div();
        panel.content(columns().classes("is-gapless").content(
                column().classes("is-narrow").content(button("Send").is(PRIMARY).attr("type", "submit")),
                column().classes("has-text-right").content(responseInfo)));
        var headersSection = box().classes("response-headers", "flat-box");
        headersSection.content(subtitle(6, "Headers ▶").classes("response-headers-toggle").attr("tabindex", "0"));
        if (documentedHeaders != null) headersSection.content(documentedHeaders);
        headersSection.content(div().classes("response-header-rows", "response-headers-undocumented"));
        headersSection.content(p("no headers").classes("response-empty").style("display:none"));
        panel.content(headersSection);
        panel.content(element("pre").classes("response", "box", "flat-box"));
        panel.content(p("no body").classes("response-empty", "box", "flat-box").style("display:none"));
        return panel;
    }

    private Element documentedHeaders(ApiResponse response) {
        if (response.getHeaders() == null || response.getHeaders().isEmpty()) return null;
        var docGrid = div().classes("response-header-rows", "response-documented-headers");
        for (var headerEntry : response.getHeaders().entrySet()) {
            var headerObj = headerEntry.getValue();
            var nameEl = span(headerEntry.getKey()).classes("response-header-name");
            if (TRUE == headerObj.getDeprecated()) nameEl.classes("is-deprecated");
            docGrid.content(nameEl);
            var valueEl = span().classes("response-header-value").attr("data-header", headerEntry.getKey().toLowerCase());
            if (TRUE == headerObj.getRequired()) valueEl.attr("data-required", "");
            docGrid.content(valueEl);
            if (headerObj.getDescription() != null) {
                docGrid.content(span());
                docGrid.content(span(headerObj.getDescription()).classes("response-header-description")
                        .attr("data-header", headerEntry.getKey().toLowerCase()));
            }
        }
        return docGrid;
    }

    private record StatusCode(String code, String text, String cssClass) {
        String label() {return code + " " + text;}

        static StatusCode of(String code) {
            return switch (code) {
                case "200" -> new StatusCode(code, "OK", "is-success");
                case "201" -> new StatusCode(code, "Created", "is-success");
                case "204" -> new StatusCode(code, "No Content", "is-success");
                case "301" -> new StatusCode(code, "Moved Permanently", "is-info");
                case "304" -> new StatusCode(code, "Not Modified", "is-info");
                case "400" -> new StatusCode(code, "Bad Request", "is-error");
                case "401" -> new StatusCode(code, "Unauthorized", "is-error");
                case "403" -> new StatusCode(code, "Forbidden", "is-error");
                case "404" -> new StatusCode(code, "Not Found", "is-error");
                case "405" -> new StatusCode(code, "Method Not Allowed", "is-error");
                case "409" -> new StatusCode(code, "Conflict", "is-error");
                case "500" -> new StatusCode(code, "Internal Server Error", "is-error");
                case "502" -> new StatusCode(code, "Bad Gateway", "is-error");
                case "503" -> new StatusCode(code, "Service Unavailable", "is-error");
                case "default" -> new StatusCode(code, "Default", "is-info");
                default -> {
                    var cssClass = code.startsWith("2") ? "is-success"
                            : code.startsWith("4") || code.startsWith("5") ? "is-error"
                            : "is-info";
                    yield new StatusCode(code, code, cssClass);
                }
            };
        }
    }

    private Renderable responseBox(ApiResponses responses) {
        var statusCodes = responses.entrySet().stream()
                .filter(e -> e.getValue().getContent() != null
                        || e.getValue().getHeaders() != null
                        || e.getValue().getDescription() != null
                        || (e.getValue().getLinks() != null && !e.getValue().getLinks().isEmpty()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (statusCodes.isEmpty()) return null;

        var hasSchemaProperties = statusCodes.stream()
                .filter(code -> responses.get(code).getContent() != null)
                .anyMatch(code -> responses.get(code).getContent().values().stream()
                        .anyMatch(mt -> mt.getSchema() != null && (mt.getSchema().getProperties() != null
                                || ("array".equals(mt.getSchema().getType()) && mt.getSchema().getItems() != null))));
        var hasHeaders = statusCodes.stream()
                .anyMatch(code -> responses.get(code).getHeaders() != null && !responses.get(code).getHeaders().isEmpty());
        var hasLinks = statusCodes.stream()
                .anyMatch(code -> responses.get(code).getLinks() != null && !responses.get(code).getLinks().isEmpty());
        var hasExpandableContent = hasSchemaProperties || hasHeaders || hasLinks;

        var allContentTypes = responses.values().stream()
                .filter(r -> r.getContent() != null)
                .flatMap(r -> r.getContent().keySet().stream())
                .distinct()
                .toList();
        if (!hasExpandableContent && allContentTypes.size() <= 1) return null;

        var responseBox = responseBoxHeader(allContentTypes, hasExpandableContent);
        if (!hasExpandableContent) return responseBox;

        var content = div().classes("schema-box-content");
        content.content(statusCodeTabs(statusCodes));
        for (var code : statusCodes) {
            content.content(statusCodePanel(code, responses.get(code), statusCodes.getFirst().equals(code)));
        }
        responseBox.content(content);
        return responseBox;
    }

    private Box responseBoxHeader(List<String> allContentTypes, boolean hasExpandableContent) {
        var responseBox = box().classes("schema-box", "flat-box", "is-collapsed").attr("data-box", "response");
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
            controls.content(element("button").attr("type", "button").classes("schema-toggle").content("Schema ▶"));
        }
        header.content(controls);
        responseBox.content(header);
        return responseBox;
    }

    private Element statusCodeTabs(List<String> statusCodes) {
        var tabs = div().classes("schema-status-tabs");
        for (var code : statusCodes) {
            var tab = span(code).classes("schema-status-tab").attr("tabindex", "0").attr("data-status", code);
            if (statusCodes.getFirst().equals(code)) tab.classes("is-active");
            tabs.content(tab);
        }
        return tabs;
    }

    private Element statusCodePanel(String code, ApiResponse response, boolean isActive) {
        var panel = div().classes("schema-status-panel", "content").attr("data-status", code);
        if (!isActive) panel.style("display:none");

        if (response.getDescription() != null) {
            panel.content(p(response.getDescription()).classes("schema-response-description"));
        }

        var responseHasHeaders = response.getHeaders() != null && !response.getHeaders().isEmpty();
        var responseHasBody = response.getContent() != null
                && response.getContent().values().iterator().next().getSchema() != null;
        if (responseHasHeaders) {
            if (responseHasBody) panel.content(span("Headers"));
            panel.content(schemaHeaders(response));
        }
        if (responseHasBody) {
            if (responseHasHeaders) panel.content(span("Body"));
            var mediaType = response.getContent().values().iterator().next();
            new SchemaRenderer().render(panel, mediaType.getSchema());
        }
        if (response.getLinks() != null && !response.getLinks().isEmpty()) {
            panel.content(responseLinks(response));
        }
        return panel;
    }

    private Element responseLinks(ApiResponse response) {
        var linksSection = div().classes("schema-response-links");
        linksSection.content(span("Links").classes("schema-links-label"));
        var linksGrid = div().classes("schema-links");
        for (var entry : response.getLinks().entrySet()) {
            var linkName = entry.getKey();
            var link = entry.getValue();
            var nameEl = span(linkName).classes("schema-link-name");
            linksGrid.content(nameEl);
            var details = span().classes("schema-link-details");
            if (link.getOperationId() != null) {
                details.content(span(link.getOperationId()).classes("schema-link-operation"));
            }
            if (link.getDescription() != null) {
                details.content(span(link.getDescription()).classes("schema-link-desc"));
            }
            if (link.getParameters() != null && !link.getParameters().isEmpty()) {
                for (var param : link.getParameters().entrySet()) {
                    details.content(span(param.getKey() + " ← " + param.getValue()).classes("schema-link-param"));
                }
            }
            linksGrid.content(details);
        }
        linksSection.content(linksGrid);
        return linksSection;
    }

    private Element schemaHeaders(ApiResponse response) {
        var headersSection = div().classes("schema-response-headers");
        var headerProps = div().classes("schema-props");
        for (var entry : response.getHeaders().entrySet()) {
            var headerObj = entry.getValue();
            var nameEl = span(entry.getKey()).classes("schema-prop-name");
            var details = span().classes("schema-prop-details");
            if (headerObj.getSchema() != null && headerObj.getSchema().getType() != null) {
                details.content(span(headerObj.getSchema().getType()).classes("schema-prop-type"));
            }
            if (TRUE == headerObj.getRequired()) {
                details.content(tag("required").is(DANGER));
            }
            if (TRUE == headerObj.getDeprecated()) {
                details.content(tag("deprecated").classes("is-warning"));
            }
            if (headerObj.getDescription() != null) {
                details.content(span(headerObj.getDescription()).classes("schema-prop-desc"));
            }
            headerProps.content(nameEl, details);
        }
        headersSection.content(headerProps);
        return headersSection;
    }


    private static class SchemaRenderer {
        private final Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        @SuppressWarnings("rawtypes")
        void render(Element container, Schema<?> schema) {
            if (!visited.add(schema)) return; // cycle detection
            if (schema.getTitle() != null) {
                var titleBar = div().classes("schema-title");
                titleBar.content(span(schema.getTitle()).classes("schema-title-name"));
                if (schema.getDescription() != null) {
                    titleBar.content(span("— " + schema.getDescription()).classes("schema-title-desc"));
                }
                container.content(titleBar);
            }
            var isArray = "array".equals(schema.getType()) && schema.getItems() != null;
            if (isArray) container.content(tag("array").is(NORMAL).classes("schema-type-badge"));
            var effectiveSchema = isArray ? schema.getItems() : schema;
            var required = effectiveSchema.getRequired() != null ? effectiveSchema.getRequired() : List.<String>of();
            Map<String, Schema> properties = effectiveSchema.getProperties();
            if (properties != null) {
                var table = div().classes("schema-props");
                for (var prop : properties.entrySet()) {
                    addPropertyRow(table, prop.getKey(), prop.getValue(), required);
                }
                container.content(table);
            }
        }

        private void addPropertyRow(Element table, String name, Schema<?> propSchema, List<String> required) {
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
                nameSpan.content(element("button").attr("type", "button").classes("schema-nested-toggle").attr("aria-expanded", "false").content("▶"));
            }
            table.content(nameSpan);

            var details = span().classes("schema-prop-details");
            details.content(span(type).classes("schema-prop-type"));
            if (required.contains(name)) {
                details.content(tag("required").is(DANGER));
            }
            var example = JsonSkeletonGenerator.resolveExample(propSchema);
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
                render(nestedContent, nestedSchema);
                table.content(nestedContent);
            }
        }
    }

    private static class JsonSkeletonGenerator {
        @SuppressWarnings("rawtypes")
        static String generate(Schema<?> schema) {
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

        static Object resolveExample(Schema<?> schema) {
            var example = schema.getExample();
            if (example == null && schema.getExamples() != null && !schema.getExamples().isEmpty())
                example = schema.getExamples().getFirst();
            return example;
        }

        static String sampleValue(Schema<?> schema) {
            var example = resolveExample(schema);
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

        static String mediaTypeExample(MediaType mediaType) {
            var example = mediaType.getExample();
            if (example == null && mediaType.getExamples() != null)
                example = mediaType.getExamples().values().iterator().next().getValue();
            if (example == null) return "{}";
            if (example instanceof JsonNode node)
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
}
