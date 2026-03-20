package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record Pet(
        @Schema(examples = "1") long id,
        @NotBlank @Schema(examples = "Max") String name,
        @NotNull @Schema(examples = "available") PetStatus status,
        @Schema(examples = "42") long ownerId) {}
