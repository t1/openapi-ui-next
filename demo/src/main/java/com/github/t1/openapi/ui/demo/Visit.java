package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A veterinary visit record")
public record Visit(
        @Schema(description = "Unique identifier") long id,
        @Schema(description = "ID of the pet being visited") long petId,
        @NotBlank @Schema(description = "Date of the visit") String date,
        @NotBlank @Schema(description = "Reason for the visit") String reason) {}
