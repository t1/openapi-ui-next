package com.github.t1.openapi.ui.generator;

import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.PathItem.HttpMethod;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.eclipse.microprofile.openapi.models.PathItem.HttpMethod.DELETE;
import static org.eclipse.microprofile.openapi.models.PathItem.HttpMethod.GET;
import static org.eclipse.microprofile.openapi.models.PathItem.HttpMethod.HEAD;
import static org.eclipse.microprofile.openapi.models.PathItem.HttpMethod.OPTIONS;
import static org.eclipse.microprofile.openapi.models.PathItem.HttpMethod.PATCH;
import static org.eclipse.microprofile.openapi.models.PathItem.HttpMethod.POST;
import static org.eclipse.microprofile.openapi.models.PathItem.HttpMethod.PUT;
import static org.eclipse.microprofile.openapi.models.PathItem.HttpMethod.TRACE;

class PathNode {
    private static final List<HttpMethod> METHOD_ORDER = List.of(
            GET, HEAD, OPTIONS, TRACE, POST, PUT, PATCH, DELETE);

    private final Map<String, PathNode> children = new LinkedHashMap<>();
    private final Map<HttpMethod, Operation> operations = new LinkedHashMap<>();
    private PathItem pathItem;

    Map<String, PathNode> children() { return Collections.unmodifiableMap(children); }

    Map<HttpMethod, Operation> operations() {
        var sorted = new LinkedHashMap<HttpMethod, Operation>();
        operations.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> METHOD_ORDER.indexOf(e.getKey())))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    PathItem pathItem() { return pathItem; }

    void add(List<String> segments, int index, PathItem pathItem) {
        if (index >= segments.size()) {
            this.pathItem = pathItem;
            operations.putAll(pathItem.getOperations());
            return;
        }
        var segment = segments.get(index);
        var key = isPathParam(segment) ? existingParamKeyOrElse(segment) : segment;
        children.computeIfAbsent(key, k -> new PathNode())
                .add(segments, index + 1, pathItem);
    }

    private String existingParamKeyOrElse(String segment) {
        return children.keySet().stream()
                .filter(PathNode::isPathParam)
                .findFirst()
                .orElse(segment);
    }

    static boolean isPathParam(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }
}
