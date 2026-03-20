package com.github.t1.openapi.ui.demo;

import java.util.Comparator;

import jakarta.json.Json;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override public Response toResponse(ConstraintViolationException exception) {
        var violations = Json.createArrayBuilder();
        exception.getConstraintViolations().stream()
                .sorted(Comparator.comparing(v -> fieldName(v.getPropertyPath())))
                .forEach(v -> violations.add(Json.createObjectBuilder()
                        .add("field", fieldName(v.getPropertyPath()))
                        .add("message", v.getMessage())));
        return Response.status(400)
                .type("application/problem+json")
                .entity(Json.createObjectBuilder()
                        .add("type", "urn:problem-type:constraint-violation")
                        .add("title", "Bad Request")
                        .add("status", 400)
                        .add("violations", violations)
                        .build()
                        .toString())
                .build();
    }

    private static String fieldName(Path path) {
        var name = "";
        for (var node : path) name = node.getName();
        return name;
    }
}
