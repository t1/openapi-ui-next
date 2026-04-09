package com.github.t1.openapi.ui.generator;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;

class SchemaResolverTest {
    @Test void shouldResolveRefSchema() {
        var petSchema = OASFactory.createSchema();
        petSchema.setType(List.of(Schema.SchemaType.OBJECT));
        petSchema.addProperty("name", OASFactory.createSchema().type(List.of(Schema.SchemaType.STRING)));
        
        var schemas = Map.of("Pet", petSchema);
        
        var refSchema = OASFactory.createSchema();
        refSchema.setRef("#/components/schemas/Pet");
        
        var resolved = SchemaResolver.resolve(refSchema, schemas);
        
        then(resolved).isSameAs(petSchema);
    }
    
    @Test void shouldReturnSchemaWhenNoRef() {
        var schema = OASFactory.createSchema();
        schema.setType(List.of(Schema.SchemaType.STRING));
        
        var resolved = SchemaResolver.resolve(schema, Map.of());
        
        then(resolved).isSameAs(schema);
    }
}
