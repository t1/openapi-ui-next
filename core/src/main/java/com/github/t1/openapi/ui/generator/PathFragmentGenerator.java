package com.github.t1.openapi.ui.generator;

import com.github.t1.htmljava.Element;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem.HttpMethod;

import java.util.Map;

import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.openapi.ui.generator.OperationFragmentGenerator.operationFragment;

class PathFragmentGenerator {
    static Element pathFragment(ApiPath path, Map<HttpMethod, Operation> operations, Map<String, String[]> operationIdMap, org.eclipse.microprofile.openapi.models.PathItem pathItem, java.util.List<org.eclipse.microprofile.openapi.models.servers.Server> globalServers, org.eclipse.microprofile.openapi.models.Components components, java.util.List<org.eclipse.microprofile.openapi.models.security.SecurityRequirement> globalSecurity) {
        var tabList = element("ul");
        var first = true;
        Element firstMethodContent = null;
        for (var opEntry : operations.entrySet()) {
            var method = opEntry.getKey();
            var li = element("li");
            if (first) li.classes("is-active");
            li.content(element("a").content(method.name())
                    .attr("data-tab-value", method.name())
                    .attr("data-method", method.name())
                    .attr("hx-get", path + "/" + method.name() + ".html")
                    .attr("hx-target", "#method-content")
                    .attr("hx-swap", "innerHTML"));
            tabList.content(li);
            if (first) {
                firstMethodContent = operationFragment(
                        new com.github.t1.openapi.ui.generator.Operation(method, opEntry.getValue(), path, pathItem, globalServers, components, globalSecurity), operationIdMap);
                first = false;
            }
        }
        return div().content(
                div().classes("tabs").attr("data-tab-bar", "method-tabs").attr("tabindex", "0").content(tabList),
                div().id("method-content").content(firstMethodContent));
    }
}
