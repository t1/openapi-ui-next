package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request body for recording a veterinary visit")
public record VisitInput(
        @NotBlank @Schema(description = "Date of the visit") String date,
        @NotBlank @Schema(description = "Reason for the visit") String reason) {}
