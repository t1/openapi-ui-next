package com.github.t1.openapi.ui.generator;

import com.github.t1.htmljava.Element;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem.HttpMethod;

import java.util.Map;

import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.openapi.ui.generator.OperationFragmentGenerator.operationFragment;

class PathFragmentGenerator {
    static Element pathFragment(ApiPath path, Map<HttpMethod, Operation> operations, Map<String, String[]> operationIdMap) {
        var tabList = element("ul");
        var first = true;
        Element firstMethodContent = null;
        for (var opEntry : operations.entrySet()) {
            var method = opEntry.getKey();
            var li = element("li");
            if (first) li.classes("is-active");
            li.content(element("a").content(method.name())
                    .attr("tabindex", "0")
                    .attr("data-method", method.name())
                    .attr("hx-get", path + "/" + method.name() + ".html")
                    .attr("hx-target", "#method-content")
                    .attr("hx-swap", "innerHTML"));
            tabList.content(li);
            if (first) {
                firstMethodContent = operationFragment(
                        new com.github.t1.openapi.ui.generator.Operation(method, opEntry.getValue(), path), operationIdMap);
                first = false;
            }
        }
        return div().content(
                div().classes("tabs").content(tabList),
                div().id("method-content").content(firstMethodContent));
    }
}
