package com.github.t1.openapi.ui.generator;

import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class PathNode {
    private final Map<String, PathNode> children = new LinkedHashMap<>();
    private final Map<PathItem.HttpMethod, Operation> operations = new LinkedHashMap<>();

    Map<String, PathNode> children() { return Collections.unmodifiableMap(children); }

    Map<PathItem.HttpMethod, Operation> operations() { return Collections.unmodifiableMap(operations); }

    void add(List<String> segments, int index, PathItem pathItem) {
        if (index >= segments.size()) {
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
