package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.json.JsonObject;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;

@Path("/pets")
@Tag(name = "pets")
public class PetResource {
    static final List<Pet> PETS = new ArrayList<>(List.of(
            new Pet(1, "Max", "available", 1),
            new Pet(2, "Bella", "adopted", 1),
            new Pet(3, "Charlie", "available", 2)
    ));

    private static long nextId = 4;

    @GET @Operation(summary = "List all pets", description = "Returns all pets from the system. Supports filtering by status.")
    public List<Pet> list(@QueryParam("status") String status) {
        if (status == null) return PETS;
        return PETS.stream().filter(p -> p.status().equals(status)).toList();
    }

    @GET @Path("/{id}") @Operation(summary = "Get a pet by ID", description = "Returns a single pet by its unique identifier.")
    public Pet get(@PathParam("id") long id) {
        return PETS.stream()
                .filter(p -> p.id() == id).findFirst()
                .orElseThrow(NotFoundException::new);
    }

    @POST @Operation(summary = "Add a new pet")
    public Response create(@RequestBody Pet pet) {
        var created = new Pet(nextId++, pet.name(), pet.status(), pet.ownerId());
        PETS.add(created);
        return Response.status(201).entity(created).build();
    }

    @PUT @Path("/{id}") @Operation(summary = "Update a pet", deprecated = true)
    @Deprecated
    public Pet update(@PathParam("id") long id, @RequestBody Pet pet) {
        for (int i = 0; i < PETS.size(); i++) {
            if (PETS.get(i).id() == id) {
                var updated = new Pet(id, pet.name(), pet.status(), pet.ownerId());
                PETS.set(i, updated);
                return updated;
            }
        }
        throw new NotFoundException();
    }

    @PATCH @Path("/{id}") @Operation(summary = "Partially update a pet")
    public Pet patch(@PathParam("id") long id, @RequestBody JsonObject patch) {
        for (int i = 0; i < PETS.size(); i++) {
            var existing = PETS.get(i);
            if (existing.id() == id) {
                var updated = new Pet(id,
                        patch.containsKey("name") ? patch.getString("name") : existing.name(),
                        patch.containsKey("status") ? patch.getString("status") : existing.status(),
                        patch.containsKey("ownerId") ? patch.getJsonNumber("ownerId").longValue() : existing.ownerId());
                PETS.set(i, updated);
                return updated;
            }
        }
        throw new NotFoundException();
    }

    @DELETE @Path("/{id}") @Operation(summary = "Delete a pet")
    public Response delete(@PathParam("id") long id) {
        var removed = PETS.removeIf(p -> p.id() == id);
        if (!removed) throw new NotFoundException();
        return Response.noContent().build();
    }
}
