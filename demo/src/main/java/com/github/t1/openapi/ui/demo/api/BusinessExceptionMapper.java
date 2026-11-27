package com.github.t1.openapi.ui.demo.api;

import java.net.URI;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.quarkiverse.httpproblem.HttpProblem;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

/// Maps all [BusinessException]s to `400 Bad Request`.
/// This is intentional: the demo API treats all business rule violations — including
/// "not found" — as client errors, keeping error handling uniform and simple.
@Provider
class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {
    @Override public Response toResponse(BusinessException exception) {
        return HttpProblem.builder()
                .withType(typeUrn(exception))
                .withTitle(BAD_REQUEST.getReasonPhrase())
                .withStatus(BAD_REQUEST)
                .withDetail(exception.getMessage())
                .build()
                .toResponse();
    }

    static URI typeUrn(BusinessException exception) {
        var name = exception.getClass().getSimpleName()
                .replaceAll("Exception$", "")
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase();
        return URI.create("urn:problem-type:" + name);
    }
}
