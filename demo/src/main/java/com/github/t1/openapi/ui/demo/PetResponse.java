package com.github.t1.openapi.ui.demo;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@XmlRootElement
@Schema(description = "A pet with its owner details")
public class PetResponse {
    @Schema(description = "Unique identifier", examples = "1") public long id;
    @Schema(description = "Display name of the pet", examples = "Max") public String name;
    @Schema(description = "Current adoption status", examples = "available") public PetStatus status;
    @Schema(description = "The pet's owner") public OwnerSummary owner;
    @XmlElementWrapper(name = "visits") @XmlElement(name = "visit")
    @Schema(description = "Veterinary visits for this pet (only included when showVisits=true)") public List<VisitSummary> visits;

    public PetResponse() {}

    public PetResponse(long id, String name, PetStatus status, OwnerSummary owner, List<VisitSummary> visits) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.owner = owner;
        this.visits = visits;
    }
}
