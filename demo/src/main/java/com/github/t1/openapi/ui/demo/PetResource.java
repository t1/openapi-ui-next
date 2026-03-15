package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.ArrayList;
import java.util.List;

@Path("/pets")
public class PetResource {
    static final List<Pet> PETS = new ArrayList<>(List.of(
            new Pet(1, "Max", "available", 1),
            new Pet(2, "Bella", "adopted", 1),
            new Pet(3, "Charlie", "available", 2)
    ));

    private static long nextId = 4;

    @GET @Operation(summary = "List all pets")
    public List<Pet> list(@QueryParam("status") String status) {
        if (status == null) return PETS;
        return PETS.stream().filter(p -> p.status().equals(status)).toList();
    }

    @GET @Path("/{id}") @Operation(summary = "Get a pet by ID")
    public Pet get(@PathParam("id") long id) {
        return PETS.stream()
                .filter(p -> p.id() == id).findFirst()
                .orElseThrow(NotFoundException::new);
    }

    @POST @Operation(summary = "Add a new pet")
    public Response create(Pet pet) {
        var created = new Pet(nextId++, pet.name(), pet.status(), pet.ownerId());
        PETS.add(created);
        return Response.status(201).entity(created).build();
    }

    @DELETE @Path("/{id}") @Operation(summary = "Delete a pet")
    public Response delete(@PathParam("id") long id) {
        var removed = PETS.removeIf(p -> p.id() == id);
        if (!removed) throw new NotFoundException();
        return Response.noContent().build();
    }
}
