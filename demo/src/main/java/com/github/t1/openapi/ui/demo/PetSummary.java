package com.github.t1.openapi.ui.demo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Summary of a pet")
public record PetSummary(
        @Schema(description = "Unique identifier", examples = "1") long id,
        @Schema(description = "Display name of the pet", examples = "Max") String name,
        @Schema(description = "Current adoption status", examples = "available") PetStatus status) {}
