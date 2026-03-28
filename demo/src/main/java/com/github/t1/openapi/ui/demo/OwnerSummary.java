package com.github.t1.openapi.ui.demo;

import jakarta.xml.bind.annotation.XmlRootElement;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@XmlRootElement
@Schema(description = "Summary of a pet owner")
public class OwnerSummary {
    @Schema(description = "Unique identifier") public long id;
    @Schema(description = "Full name of the owner") public String name;

    public OwnerSummary() {}

    public OwnerSummary(long id, String name) {
        this.id = id;
        this.name = name;
    }
}
