package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.ArrayList;
import java.util.List;

@Path("/owners")
public class OwnerResource {
    static final List<Owner> OWNERS = new ArrayList<>(List.of(
            new Owner(1, "Alice", "alice@example.com"),
            new Owner(2, "Bob", "bob@example.com")
    ));

    @GET @Operation(summary = "List all owners")
    public List<Owner> list() {
        return OWNERS;
    }

    @GET @Path("/{id}") @Operation(summary = "Get an owner by ID")
    public Owner get(@PathParam("id") long id) {
        return OWNERS.stream()
                .filter(o -> o.id() == id).findFirst()
                .orElseThrow(NotFoundException::new);
    }

    @GET @Path("/{ownerId}/pets") @Operation(summary = "List pets for an owner")
    public List<Pet> listPets(@PathParam("ownerId") long ownerId) {
        return PetResource.PETS.stream()
                .filter(p -> p.ownerId() == ownerId).toList();
    }
}
