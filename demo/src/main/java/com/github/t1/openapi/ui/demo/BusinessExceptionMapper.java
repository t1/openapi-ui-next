package com.github.t1.openapi.ui.demo;

import java.net.URI;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {
    @Override public Response toResponse(BusinessException exception) {
        return Response.status(400)
                .type("application/problem+json")
                .entity(new ProblemDetails(typeUrn(exception), "Bad Request", 400, exception.getMessage(), null))
                .build();
    }

    static URI typeUrn(BusinessException exception) {
        var name = exception.getClass().getSimpleName()
                .replaceAll("Exception$", "")
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase();
        return URI.create("urn:problem-type:" + name);
    }
}
