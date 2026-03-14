package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import java.util.ArrayList;
import java.util.List;

@Path("/pets")
public class PetResource {
    private static final List<Pet> PETS = new ArrayList<>(List.of(
            new Pet(1, "Max", "available"),
            new Pet(2, "Bella", "adopted")
    ));

    @GET
    public List<Pet> list() {
        return PETS;
    }

    @GET
    @Path("/{id}")
    public Pet get(@PathParam("id") long id) {
        return PETS.stream()
                .filter(p -> p.id() == id).findFirst()
                .orElseThrow(NotFoundException::new);
    }
}
