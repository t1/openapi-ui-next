package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A pet owner")
public record Owner(
        @Schema(description = "Unique identifier") long id,
        @NotBlank @Schema(description = "Full name of the owner") String name,
        @NotBlank @Schema(description = "Contact email address") String email) {}
