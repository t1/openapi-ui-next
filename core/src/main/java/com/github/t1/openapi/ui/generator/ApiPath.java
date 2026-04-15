package com.github.t1.openapi.ui.generator;

import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.jspecify.annotations.NonNull;

record ApiPath(String value) {
    static final ApiPath ROOT = new ApiPath("");

    ApiPath resolve(String segment) {
        return new ApiPath(value.isEmpty() ? segment : value + "/" + segment);
    }

    /// The absolute API path for display, e.g. `/pets/{petId}`.
    String display() { return "/" + value; }

    /// Replace unified path param segments with actual parameter names from the operation.
    ApiPath withResolvedParams(Operation operation) {
        if (operation.getParameters() == null) return this;
        var pathParams = operation.getParameters().stream()
                .filter(p -> Parameter.In.PATH.equals(p.getIn()))
                .toList();
        var segments = value.split("/");
        var paramIndex = 0;
        for (var i = 0; i < segments.length; i++) {
            if (segments[i].startsWith("{") && segments[i].endsWith("}") && paramIndex < pathParams.size()) {
                segments[i] = "{" + pathParams.get(paramIndex).getName() + "}";
                paramIndex++;
            }
        }
        return new ApiPath(String.join("/", segments));
    }

    @Override public @NonNull String toString() { return value; }
}
