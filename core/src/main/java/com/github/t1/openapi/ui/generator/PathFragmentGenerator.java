package com.github.t1.openapi.ui.generator;

import com.github.t1.htmljava.Element;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import java.util.Map;

import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;

class PathFragmentGenerator {
    static Element buildContent(ApiPath fullPath, Map<HttpMethod, io.swagger.v3.oas.models.Operation> operations) {
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
                    .attr("hx-get", fullPath + "/" + method.name() + ".html")
                    .attr("hx-target", "#method-content")
                    .attr("hx-swap", "innerHTML"));
            tabList.content(li);
            if (first) {
                firstMethodContent = MethodFragmentGenerator.buildContent(
                        new OperationContext(method, opEntry.getValue(), fullPath));
                first = false;
            }
        }
        return div().content(
                div().classes("tabs").content(tabList),
                div().id("method-content").content(firstMethodContent));
    }
}
