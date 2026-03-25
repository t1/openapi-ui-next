package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A pet registered in the store")
public record Pet(
        @Schema(description = "Unique identifier", examples = "1") long id,
        @NotBlank @Schema(description = "Display name of the pet", examples = "Max") String name,
        @NotNull @Schema(description = "Current adoption status", examples = "available") PetStatus status,
        @Schema(description = "ID of the owning customer", examples = "42") long ownerId) {}
