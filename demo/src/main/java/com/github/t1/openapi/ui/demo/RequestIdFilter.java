package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

class RequestIdFilter {
    @ServerResponseFilter void echoRequestId(ContainerRequestContext request, ContainerResponseContext response) {
        var requestId = request.getHeaderString("X-Request-ID");
        if (requestId != null) response.getHeaders().putSingle("X-Request-ID", requestId);
    }
}
