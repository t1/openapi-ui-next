package com.github.t1.openapi.ui.demo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Summary of a veterinary visit")
public record VisitSummary(
        @Schema(description = "Unique identifier") long id,
        @Schema(description = "Date of the visit") String date,
        @Schema(description = "Reason for the visit") String reason) {}
