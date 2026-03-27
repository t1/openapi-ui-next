package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@XmlRootElement
@Schema(description = "A pet registered in the store")
public class Pet {
    @Schema(description = "Unique identifier", examples = "1") public long id;
    @NotBlank @Schema(description = "Display name of the pet", examples = "Max") public String name;
    @NotNull @Schema(description = "Current adoption status", examples = "available") public PetStatus status;
    @Schema(description = "ID of the owning customer", examples = "42") public long ownerId;

    public Pet() {}

    public Pet(long id, String name, PetStatus status, long ownerId) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.ownerId = ownerId;
    }
}
