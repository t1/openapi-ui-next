package com.github.t1.openapi.ui.demo.api;

import java.net.URI;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import io.quarkiverse.httpproblem.HttpProblem;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

@Provider
class DelayResponseFilter implements ContainerRequestFilter {
    @Override public void filter(ContainerRequestContext request) {
        var delay = request.getHeaderString("X-Delay-Response");
        if (delay == null) return;
        try {
            var delayMillis = (long) (Double.parseDouble(delay) * 1000);
            Thread.sleep(delayMillis);
        } catch (NumberFormatException e) {
            request.abortWith(HttpProblem.builder()
                    .withType(URI.create("urn:problem-type:invalid-delay-value"))
                    .withTitle(BAD_REQUEST.getReasonPhrase())
                    .withStatus(BAD_REQUEST)
                    .withDetail("Invalid X-Delay-Response value: " + delay)
                    .build()
                    .toResponse());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
