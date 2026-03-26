package com.github.t1.openapi.ui.demo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.net.URI;

@Schema(description = "Error response following RFC 9457 Problem Details")
record ProblemDetails(
        @Schema(description = "Problem type URI", examples = "urn:problem-type:pet-not-found")
        URI type,

        @Schema(description = "Short human-readable and fixed summary", examples = "Pet Not Found")
        String title,

        @Schema(description = "HTTP status code", examples = "404")
        Integer status,

        @Schema(description = "Detailed human-readable explanation with dynamic details", examples = "Pet with ID 99 not found")
        String detail,

        @Schema(description = "URI identifying the specific occurrence", examples = "/pets/99")
        URI instance) {}
