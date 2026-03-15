package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.ArrayList;
import java.util.List;

@Path("/pets/{petId}/visits")
public class VisitResource {
    static final List<Visit> VISITS = new ArrayList<>(List.of(
            new Visit(1, 1, "2024-01-15", "Annual checkup"),
            new Visit(2, 1, "2024-06-20", "Vaccination"),
            new Visit(3, 2, "2024-03-10", "Dental cleaning")
    ));

    @GET @Operation(summary = "List visits for a pet")
    public List<Visit> list(@PathParam("petId") long petId) {
        return VISITS.stream()
                .filter(v -> v.petId() == petId).toList();
    }

    @GET @Path("/{visitId}") @Operation(summary = "Get a visit by ID")
    public Visit get(@PathParam("petId") long petId, @PathParam("visitId") long visitId) {
        return VISITS.stream()
                .filter(v -> v.petId() == petId && v.id() == visitId).findFirst()
                .orElseThrow(NotFoundException::new);
    }
}
