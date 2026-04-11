package com.github.t1.openapi.ui.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.bulmajava.elements.Box;
import com.github.t1.bulmajava.form.Form;
import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.PathItem.HttpMethod;
import org.eclipse.microprofile.openapi.models.links.Link;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.security.SecurityScheme;
import org.eclipse.microprofile.openapi.OASFactory;

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
    private final org.eclipse.microprofile.openapi.models.Operation operation;
    private final ApiPath path;
    private final ApiPath displayPath;
    private final Map<String, String[]> operationIdMap;
    private final Map<String, Schema> schemas;
    private final java.util.List<org.eclipse.microprofile.openapi.models.security.SecurityRequirement> globalSecurity;
    private final Components components;

    /// Helper to get type as string from MicroProfile's List<SchemaType>
    private static String typeAsString(Schema schema) {
        var types = schema.getType();
        if (types == null || types.isEmpty()) return null;
        return types.getFirst().toString().toLowerCase();
    }

    private OperationFragmentGenerator(Operation operation, Map<String, String[]> operationIdMap) {
        this.method = operation.method();
        this.operation = operation.spec();
        this.path = operation.path();
        this.displayPath = path.withResolvedParams(this.operation);
        this.operationIdMap = operationIdMap;
        this.components = operation.components();
        this.schemas = operation.components() != null && operation.components().getSchemas() != null 
            ? operation.components().getSchemas() 
            : Map.of();
        this.globalSecurity = operation.globalSecurity();
    }

    static Element operationFragment(Operation operation, Map<String, String[]> operationIdMap) {
        return new OperationFragmentGenerator(operation, operationIdMap).fragment();
    }

    static Map<String, String> responseFragments(Operation operation, Map<String, String[]> operationIdMap) {
        return new OperationFragmentGenerator(operation, operationIdMap).responseFragmentFiles();
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
        responseLinksData(operationForm);
        parameterFields(operationForm);
        authSection(operationForm);
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

    /// Embeds link metadata as a JSON `data-response-links` attribute on the form,
    /// so the JS can detect and wrap matching response body values as clickable links.
    private void responseLinksData(Form form) {
        if (operation.getResponses() == null) return;
        var linksJson = new StringBuilder("{");
        var firstStatus = true;
        for (var entry : operation.getResponses().getAPIResponses().entrySet()) {
            var response = entry.getValue();
            var links = allLinks(response);
            if (links.isEmpty()) continue;
            if (!firstStatus) linksJson.append(",");
            firstStatus = false;
            linksJson.append("\"").append(entry.getKey()).append("\":{");
            var firstLink = true;
            for (var linkEntry : links.entrySet()) {
                if (!firstLink) linksJson.append(",");
                firstLink = false;
                var link = linkEntry.getValue();
                linksJson.append("\"").append(linkEntry.getKey()).append("\":{");
                linksJson.append("\"operationId\":\"").append(link.getOperationId()).append("\"");
                if (link.getParameters() != null && !link.getParameters().isEmpty()) {
                    linksJson.append(",\"parameters\":{");
                    var firstParam = true;
                    for (var param : link.getParameters().entrySet()) {
                        if (!firstParam) linksJson.append(",");
                        firstParam = false;
                        linksJson.append("\"").append(param.getKey()).append("\":\"").append(param.getValue()).append("\"");
                    }
                    linksJson.append("}");
                }
                linksJson.append("}");
            }
            linksJson.append("}");
        }
        linksJson.append("}");
        if (!firstStatus) form.attr("data-response-links", linksJson.toString());
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
            var badges = tagsAddon().content(tag(param.getIn().toString())).classes("is-inline-flex", "ml-2");
            if (TRUE == param.getRequired()) badges.content(tag("required").is(DANGER));
            var inputField = field().label(span(param.getName()), badges);
            inputField.content(parameterInput(param));
            inputField.iconRight("thumbtack");
            parameterHelp(inputField, param);
            operationForm.content(inputField);
        }
    }

    private void authSection(Form operationForm) {
        var effectiveSecurity = operation.getSecurity() != null ? operation.getSecurity() : globalSecurity;
        if (effectiveSecurity == null || effectiveSecurity.isEmpty()) return;
        if (components == null || components.getSecuritySchemes() == null) return;
        
        var authDiv = div().classes("auth-section");
        
        // Get the first security requirement (OR alternative)
        var firstRequirement = effectiveSecurity.getFirst();
        for (var schemeName : firstRequirement.getSchemes().keySet()) {
            var scheme = components.getSecuritySchemes().get(schemeName);
            if (scheme != null && SecurityScheme.Type.APIKEY == scheme.getType()) {
                authDiv.content(apiKeyAuthField(scheme));
            }
        }
        
        operationForm.content(authDiv);
    }
    
    private com.github.t1.bulmajava.form.Field apiKeyAuthField(SecurityScheme scheme) {
        var badges = tagsAddon().content(tag("🔒 apiKey").is(WARNING)).classes("is-inline-flex", "ml-2");
        var inputField = field().label(span(scheme.getName()), badges);
        var inp = input(TEXT).attr("name", scheme.getName());
        inp.attr("data-param-in", SecurityScheme.In.HEADER == scheme.getIn() ? "auth-header" : "auth-query");
        inputField.content(inp);
        inputField.iconRight("thumbtack");
        return inputField;
    }

    private Renderable parameterInput(Parameter param) {
        var schema = param.getSchema();
        var enumValues = (schema != null) ? schema.getEnumeration() : null;
        var required = TRUE == param.getRequired();
        if (enumValues != null && !enumValues.isEmpty()) {
            return selectInput(param, enumValues, required);
        } else if ("boolean".equals(schema != null ? typeAsString(schema) : null)) {
            return checkboxInput(param);
        } else {
            return textInput(param, required);
        }
    }

    private Renderable selectInput(Parameter param, List<?> enumValues, boolean required) {
        var sel = select(param.getName()).is(FULLWIDTH).option("", "(any)");
        sel.attr("data-param-in", param.getIn().toString());
        if (required) sel.attr("required", "");
        for (var value : enumValues) {
            sel.option(value.toString(), value.toString());
        }
        return sel;
    }

    private Renderable checkboxInput(Parameter param) {
        var cb = element("input").attr("type", "checkbox")
                .attr("name", param.getName()).attr("data-param-in", param.getIn().toString());
        return element("label").classes("checkbox").content(cb);
    }

    private Renderable textInput(Parameter param, boolean required) {
        var inp = input(TEXT).attr("name", param.getName());
        inp.attr("data-param-in", param.getIn().toString());
        if (required) inp.attr("required", "");
        return inp;
    }

    private void parameterHelp(com.github.t1.bulmajava.form.Field inputField, Parameter param) {
        if (Parameter.In.COOKIE.equals(param.getIn())) {
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
        var jsonContent = content.getMediaTypes().get("application/json");
        if (jsonContent == null) jsonContent = content.getMediaTypes().get("*/*");
        if (jsonContent == null || jsonContent.getSchema() == null) return null;
        return jsonContent;
    }

    private String skeleton(MediaType jsonContent) {
        var skeleton = JsonSkeletonGenerator.generate(jsonContent.getSchema(), schemas);
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
        var schema = SchemaResolver.resolve(jsonContent.getSchema(), schemas);
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

    private Renderable requestBodyEditor(String skeleton, Schema schema) {
        var textareaEl = textarea()
                .attr("data-request-body", "true")
                .classes("is-family-code");
        if (TRUE == operation.getRequestBody().getRequired()) textareaEl.attr("required", "");
        textareaEl.content(skeleton);
        var hasProperties = schema.getProperties() != null && !schema.getProperties().isEmpty();
        if (hasProperties) {
            var treeContent = div();
            new SchemaRenderer(null).render(treeContent, schema);
            var tree = div().classes("schema-box-tree", "schema-box-content").content(treeContent);
            return splitPane().ratio(1, 1).first(textareaEl).second(tree);
        }
        return textareaEl;
    }

    private Map<String, String> responseFragmentFiles() {
        if (operation.getResponses() == null) return Map.of();
        var fragments = new LinkedHashMap<String, String>();
        for (var entry : operation.getResponses().getAPIResponses().entrySet()) {
            var code = entry.getKey();
            var response = entry.getValue();
            var panel = responsePanel(code, response);
            fragments.put(method.name() + "-response-" + code + ".html", panel.render());
        }
        fragments.put(method.name() + "-response-fallback.html", fallbackPanel().render());
        return fragments;
    }

    private Element responsePanel(String code, APIResponse response) {
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

    private Element documentedHeaders(APIResponse response) {
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

    private Renderable responseBox(APIResponses responses) {
        var statusCodes = responses.getAPIResponses().entrySet().stream()
                .filter(e -> e.getValue().getContent() != null
                        || e.getValue().getHeaders() != null
                        || e.getValue().getDescription() != null
                        || (e.getValue().getLinks() != null && !e.getValue().getLinks().isEmpty()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (statusCodes.isEmpty()) return null;

        var hasSchemaProperties = statusCodes.stream()
                .filter(code -> responses.getAPIResponses().get(code).getContent() != null)
                .anyMatch(code -> responses.getAPIResponses().get(code).getContent().getMediaTypes().values().stream()
                        .anyMatch(mt -> {
                            if (mt.getSchema() == null) return false;
                            var resolved = SchemaResolver.resolve(mt.getSchema(), schemas);
                            return resolved.getProperties() != null || (typeAsString(resolved) != null && "array".equals(typeAsString(resolved)) && resolved.getItems() != null);
                        }));
        var hasHeaders = statusCodes.stream()
                .anyMatch(code -> responses.getAPIResponses().get(code).getHeaders() != null && !responses.getAPIResponses().get(code).getHeaders().isEmpty());
        var hasLinks = statusCodes.stream()
                .anyMatch(code -> responses.getAPIResponses().get(code).getLinks() != null && !responses.getAPIResponses().get(code).getLinks().isEmpty());
        var hasExpandableContent = hasSchemaProperties || hasHeaders || hasLinks;

        var allContentTypes = responses.getAPIResponses().values().stream()
                .filter(r -> r.getContent() != null)
                .flatMap(r -> r.getContent().getMediaTypes().keySet().stream())
                .distinct()
                .toList();
        if (!hasExpandableContent && allContentTypes.size() <= 1) return null;

        var responseBox = responseBoxHeader(allContentTypes, hasExpandableContent);
        if (!hasExpandableContent) return responseBox;

        var content = div().classes("schema-box-content");
        content.content(statusCodeTabs(statusCodes));
        for (var code : statusCodes) {
            content.content(statusCodePanel(code, responses.getAPIResponses().get(code), statusCodes.getFirst().equals(code)));
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

    private Element statusCodePanel(String code, APIResponse response, boolean isActive) {
        var panel = div().classes("schema-status-panel", "content").attr("data-status", code);
        if (!isActive) panel.style("display:none");

        if (response.getDescription() != null) {
            panel.content(p(response.getDescription()).classes("schema-response-description"));
        }

        var responseHasHeaders = response.getHeaders() != null && !response.getHeaders().isEmpty();
        var firstMediaType = (response.getContent() != null && !response.getContent().getMediaTypes().isEmpty())
                ? response.getContent().getMediaTypes().values().iterator().next() : null;
        var responseHasBody = firstMediaType != null && firstMediaType.getSchema() != null;
        var links = allLinks(response);
        if (responseHasHeaders) {
            if (responseHasBody) panel.content(span("Headers"));
            panel.content(schemaHeaders(response, links));
        }
        if (responseHasBody) {
            if (responseHasHeaders) panel.content(span("Body"));
            new SchemaRenderer(links).render(panel, firstMediaType.getSchema());
        }
        return panel;
    }

    private static final String X_LINKS_EXTENSION = "x-links";

    /**
     * Merges standard response links with x-links from response extensions.
     * x-links use the same Link Object structure but support [*] array wildcards in body expressions.
     */
    private static Map<String, Link> allLinks(APIResponse response) {
        var result = new LinkedHashMap<String, Link>();
        if (response.getLinks() != null) result.putAll(response.getLinks());
        if (response.getExtensions() != null) {
            result.putAll(parseXLinks(response.getExtensions()));
        }
        return result;
    }

    /**
     * Parses x-links from response extensions map.
     * Returns an empty map if no x-links extension is present.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Link> parseXLinks(Map<String, Object> extensions) {
        var result = new LinkedHashMap<String, Link>();
        var xLinks = extensions.get(X_LINKS_EXTENSION);
        if (xLinks instanceof Map<?, ?> extensionMap) {
            for (var entry : ((Map<String, Object>) extensionMap).entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> linkData) {
                    var link = OASFactory.createLink();
                    var linkDataMap = (Map<String, Object>) linkData;
                    link.setOperationId((String) linkDataMap.get("operationId"));
                    link.setDescription((String) linkDataMap.get("description"));
                    if (linkDataMap.get("parameters") instanceof Map<?, ?> parametersData) {
                        var paramMap = new LinkedHashMap<String, String>();
                        for (var p : ((Map<String, Object>) parametersData).entrySet()) {
                            paramMap.put(p.getKey(), String.valueOf(p.getValue()));
                        }
                        link.setParameters(new LinkedHashMap<>(paramMap));
                    }
                    result.put(entry.getKey(), link);
                }
            }
        }
        return result;
    }

    /** Parses `$response.body#/owner/id` → `["body", "owner/id"]` or `$response.header.X-Foo` → `["header", "X-Foo"]`. */
    private static String[] parseResponseSource(String expression) {
        if (expression == null) return null;
        if (expression.startsWith("$response.body#/")) {
            var path = expression.substring("$response.body#/".length());
            path = path.replace("[*]", "").replaceAll("/+", "/"); // strip array wildcards for schema matching
            if (path.startsWith("/")) path = path.substring(1);
            return new String[]{"body", path};
        }
        if (expression.startsWith("$response.header.")) {
            return new String[]{"header", expression.substring("$response.header.".length())};
        }
        return null;
    }

    /** Returns links whose parameters reference the given property path in the given source type.
     * For body properties, `propertyPath` is the full JSON Pointer path (e.g. `owner/id`).
     * For headers, it's the header name (e.g. `X-Request-Id`). */
    private static Map<String, Link> linksForProperty(
            Map<String, Link> allLinks, String sourceType, String propertyPath) {
        if (allLinks == null) return Map.of();
        var result = new LinkedHashMap<String, Link>();
        for (var entry : allLinks.entrySet()) {
            var link = entry.getValue();
            if (link.getParameters() == null) continue;
            for (var param : link.getParameters().values()) {
                var source = parseResponseSource(String.valueOf(param));
                if (source != null && source[0].equals(sourceType) && source[1].equals(propertyPath)) {
                    result.put(entry.getKey(), link);
                    break;
                }
            }
        }
        return result;
    }

    private Element linkSubRow(String linkName, Link link, String contextSourceType) {
        var row = span().classes("schema-link-row").attr("tabindex", "0");
        var href = operationIdHref(link.getOperationId());
        var nameEl = href != null
                ? element("a").attr("href", href).content("→ " + linkName)
                : span("→ " + linkName);
        row.content(nameEl);
        if (link.getDescription() != null) {
            row.content(span(link.getDescription()).classes("schema-prop-desc"));
        }
        if (link.getParameters() != null) {
            for (var param : link.getParameters().entrySet()) {
                var display = formatParamShort(String.valueOf(param.getValue()), contextSourceType);
                row.content(span(param.getKey() + " ← " + display).classes("schema-link-param"));
            }
        }
        return row;
    }

    private static String formatParamShort(String expression, String contextSourceType) {
        var source = parseResponseSource(expression);
        if (source == null) return expression;
        var path = source[1];
        var lastSlash = path.lastIndexOf('/');
        var leaf = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        if (source[0].equals(contextSourceType)) return leaf;
        return source[0] + "." + leaf;
    }

    private String operationIdHref(String operationId) {
        if (operationId == null || operationIdMap == null) return null;
        var target = operationIdMap.get(operationId);
        if (target == null) return null;
        return "#" + target[0] + "/" + target[1];
    }

    private Element schemaHeaders(APIResponse response, Map<String, Link> links) {
        var headersSection = div().classes("schema-response-headers");
        var headerProps = div().classes("schema-props");
        for (var entry : response.getHeaders().entrySet()) {
            var headerObj = entry.getValue();
            var nameEl = span(entry.getKey()).classes("schema-prop-name");
            var details = span().classes("schema-prop-details");
            if (headerObj.getSchema() != null && typeAsString(headerObj.getSchema()) != null) {
                details.content(span(typeAsString(headerObj.getSchema())).classes("schema-prop-type"));
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
            var headerLinks = linksForProperty(links, "header", entry.getKey());
            for (var linkEntry : headerLinks.entrySet()) {
                headerProps.content(span().classes("schema-prop-name"));
                headerProps.content(linkSubRow(linkEntry.getKey(), linkEntry.getValue(), "header"));
            }
        }
        headersSection.content(headerProps);
        return headersSection;
    }


    private class SchemaRenderer {
        private final Set<Schema> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<String, Link> links;

        SchemaRenderer(Map<String, Link> links) {
            this.links = links != null ? links : Map.of();
        }

        @SuppressWarnings("rawtypes")
        void render(Element container, Schema schema) {
            render(container, schema, "");
        }

        @SuppressWarnings("rawtypes")
        private void render(Element container, Schema schema, String pathPrefix) {
            var resolved = SchemaResolver.resolve(schema, schemas);
            if (!visited.add(resolved)) return; // cycle detection
            if (resolved.getTitle() != null) {
                var titleBar = div().classes("schema-title");
                titleBar.content(span(resolved.getTitle()).classes("schema-title-name"));
                if (resolved.getDescription() != null) {
                    titleBar.content(span("— " + resolved.getDescription()).classes("schema-title-desc"));
                }
                container.content(titleBar);
            }
            var isArray = "array".equals(typeAsString(resolved)) && resolved.getItems() != null;
            if (isArray) container.content(tag("array").is(NORMAL).classes("schema-type-badge"));
            var effectiveSchema = isArray ? SchemaResolver.resolve(resolved.getItems(), schemas) : resolved;
            var required = effectiveSchema.getRequired() != null ? effectiveSchema.getRequired() : List.<String>of();
            Map<String, Schema> properties = effectiveSchema.getProperties();
            if (properties != null) {
                var table = div().classes("schema-props");
                for (var prop : properties.entrySet()) {
                    addPropertyRow(table, prop.getKey(), prop.getValue(), required, pathPrefix);
                }
                container.content(table);
            }
        }

        private void addPropertyRow(Element table, String name, Schema propSchema, List<String> required, String pathPrefix) {
            var resolved = SchemaResolver.resolve(propSchema, schemas);
            var type = typeAsString(resolved) != null ? typeAsString(resolved) : "object";
            if (resolved.getEnumeration() != null && !resolved.getEnumeration().isEmpty()) type = "enum";

            // determine if this property has nested sub-properties
            Schema nestedSchema = null;
            if ("object".equals(type) && resolved.getProperties() != null) {
                nestedSchema = resolved;
            } else if ("array".equals(type) && resolved.getItems() != null) {
                var items = SchemaResolver.resolve(resolved.getItems(), schemas);
                if (items.getProperties() != null) {
                    nestedSchema = items;
                }
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
            var example = JsonSkeletonGenerator.resolveExample(resolved);
            if (example != null) {
                details.content(span("e.g. " + example).classes("schema-prop-example"));
            } else if (resolved.getEnumeration() != null && !resolved.getEnumeration().isEmpty()) {
                var values = String.join(" | ", resolved.getEnumeration().stream().map(Object::toString).toList());
                details.content(span(values).classes("schema-prop-example"));
            }
            if (resolved.getDescription() != null) {
                details.content(span(resolved.getDescription()).classes("schema-prop-desc"));
            }
            table.content(details);

            var fullPath = pathPrefix.isEmpty() ? name : pathPrefix + "/" + name;
            var propertyLinks = linksForProperty(links, "body", fullPath);
            for (var linkEntry : propertyLinks.entrySet()) {
                table.content(span().classes("schema-prop-name"));
                table.content(linkSubRow(linkEntry.getKey(), linkEntry.getValue(), "body"));
            }

            if (hasNested) {
                var nestedContent = div().classes("schema-nested");
                render(nestedContent, nestedSchema, fullPath);
                table.content(nestedContent);
            }
        }
    }

    private static class JsonSkeletonGenerator {
        @SuppressWarnings("rawtypes")
        static String generate(Schema schema, Map<String, Schema> schemas) {
            var resolved = SchemaResolver.resolve(schema, schemas);
            Map<String, Schema> properties = resolved.getProperties();
            if (properties == null) return "{}";
            var sb = new StringBuilder("{\n");
            var first = true;
            for (var entry : properties.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append("  \"").append(entry.getKey()).append("\": ");
                sb.append(sampleValue(entry.getValue(), schemas));
            }
            sb.append("\n}");
            return sb.toString();
        }

        static Object resolveExample(Schema schema) {
            var example = schema.getExample();
            if (example == null && schema.getExamples() != null && !schema.getExamples().isEmpty())
                example = schema.getExamples().getFirst();
            return example;
        }

        static String sampleValue(Schema schema, Map<String, Schema> schemas) {
            var resolved = SchemaResolver.resolve(schema, schemas);
            var example = resolveExample(resolved);
            if (example != null) {
                return formatSampleValue(typeAsString(resolved), example);
            }
            if (resolved.getDefaultValue() != null) {
                return formatSampleValue(typeAsString(resolved), resolved.getDefaultValue());
            }
            if (resolved.getEnumeration() != null && !resolved.getEnumeration().isEmpty()) {
                return formatSampleValue(typeAsString(resolved), resolved.getEnumeration().getFirst());
            }
            if (resolved.getFormat() != null) {
                var formatted = formatBasedSample(resolved.getFormat());
                if (formatted != null) return "\"" + formatted + "\"";
            }
            var type = typeAsString(resolved);
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
            try {
                return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(example);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("could not serialize example to JSON", e);
            }
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
