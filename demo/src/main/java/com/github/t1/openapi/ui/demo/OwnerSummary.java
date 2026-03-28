package com.github.t1.openapi.ui.demo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Summary of a pet owner")
public record OwnerSummary(
        @Schema(description = "Unique identifier") long id,
        @Schema(description = "Full name of the owner") String name) {}
