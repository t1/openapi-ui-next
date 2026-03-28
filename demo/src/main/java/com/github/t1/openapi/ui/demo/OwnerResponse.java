package com.github.t1.openapi.ui.demo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "An owner with their pets")
public record OwnerResponse(
        @Schema(description = "Unique identifier") long id,
        @Schema(description = "Full name of the owner") String name,
        @Schema(description = "Contact email address") String email,
        @Schema(description = "Pets owned by this person") List<PetSummary> pets) {}
