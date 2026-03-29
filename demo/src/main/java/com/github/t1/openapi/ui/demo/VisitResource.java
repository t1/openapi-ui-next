package com.github.t1.openapi.ui.demo;

import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;

@Path("/pets/{petId}/visits")
@Tag(name = "visits")
public class VisitResource {
    static final List<Visit> VISITS = new ArrayList<>(List.of(
            new Visit(1, 1, "2024-01-15", "Annual checkup"),
            new Visit(2, 1, "2024-06-20", "Vaccination"),
            new Visit(3, 2, "2024-03-10", "Dental cleaning")
    ));

    @GET @Tag(name = "visits") @Tag(name = "pets") @Operation(summary = "List visits for a pet")
    public List<Visit> list(@PathParam("petId") long petId) {
        return VISITS.stream()
                .filter(v -> v.petId() == petId).toList();
    }

    @POST @Operation(summary = "Record a visit")
    public Visit create(@PathParam("petId") long petId, @RequestBody @Valid Visit visit) {
        var petExists = PetResource.PETS.stream().anyMatch(p -> p.id == petId);
        if (!petExists) throw new PetNotFoundException(petId);
        var created = new Visit(VISITS.size() + 1, petId, visit.date(), visit.reason());
        VISITS.add(created);
        return created;
    }

    @GET @Path("/{visitId}") @Tag(name = "visits") @Tag(name = "pets") @Operation(summary = "Get a visit by ID")
    public Visit get(@PathParam("petId") long petId, @PathParam("visitId") long visitId) {
        return VISITS.stream()
                .filter(v -> v.petId() == petId && v.id() == visitId).findFirst()
                .orElseThrow(() -> new VisitNotFoundException(visitId));
    }

    @DELETE @Path("/{visitId}") @Operation(summary = "Cancel a visit")
    public void delete(@PathParam("petId") long petId, @PathParam("visitId") long visitId) {
        VISITS.removeIf(v -> v.petId() == petId && v.id() == visitId);
    }
}
