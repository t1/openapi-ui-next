package com.github.t1.openapi.ui.demo;

import java.io.IOException;
import java.net.URI;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

@Provider
class DelayResponseFilter implements ContainerRequestFilter {
    @Override public void filter(ContainerRequestContext request) throws IOException {
        var delay = request.getHeaderString("X-Delay-Response");
        if (delay == null) return;
        try {
            var delayMillis = (long) (Double.parseDouble(delay) * 1000);
            Thread.sleep(delayMillis);
        } catch (NumberFormatException e) {
            request.abortWith(Response.status(BAD_REQUEST)
                    .type("application/problem+json")
                    .entity(new ProblemDetails(
                            URI.create("urn:problem-type:invalid-delay-value"),
                            "Bad Request", BAD_REQUEST.getStatusCode(),
                            "Invalid X-Delay-Response value: " + delay,
                            null))
                    .build());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
