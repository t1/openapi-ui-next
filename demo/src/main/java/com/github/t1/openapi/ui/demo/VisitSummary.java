package com.github.t1.openapi.ui.demo;

import jakarta.xml.bind.annotation.XmlRootElement;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@XmlRootElement
@Schema(description = "Summary of a veterinary visit")
public class VisitSummary {
    @Schema(description = "Unique identifier") public long id;
    @Schema(description = "Date of the visit") public String date;
    @Schema(description = "Reason for the visit") public String reason;

    public VisitSummary() {}

    public VisitSummary(long id, String date, String reason) {
        this.id = id;
        this.date = date;
        this.reason = reason;
    }
}
