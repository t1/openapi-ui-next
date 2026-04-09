package com.github.t1.openapi.ui.generator;

import org.eclipse.microprofile.openapi.models.media.Schema;

import java.util.Map;

class SchemaResolver {
    static Schema resolve(Schema schema, Map<String, Schema> schemas) {
        if (schema.getRef() == null) return schema;
        
        var ref = schema.getRef();
        var componentName = ref.substring(ref.lastIndexOf('/') + 1);
        
        return schemas.get(componentName);
    }
}
