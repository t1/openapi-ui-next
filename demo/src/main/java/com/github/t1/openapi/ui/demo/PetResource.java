package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.ArrayList;
import java.util.List;

@Path("/pets")
public class PetResource {
    private static final List<Pet> PETS = new ArrayList<>(List.of(
            new Pet(1, "Max", "available"),
            new Pet(2, "Bella", "adopted")
    ));

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
}
