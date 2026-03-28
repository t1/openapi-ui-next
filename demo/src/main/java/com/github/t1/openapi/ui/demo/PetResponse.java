package com.github.t1.openapi.ui.demo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "A pet with its owner details")
public record PetResponse(
        @Schema(description = "Unique identifier", examples = "1") long id,
        @Schema(description = "Display name of the pet", examples = "Max") String name,
        @Schema(description = "Current adoption status", examples = "available") PetStatus status,
        @Schema(description = "The pet's owner") OwnerSummary owner,
        @Schema(description = "Veterinary visits for this pet (only included when showVisits=true)") List<VisitSummary> visits) {}
