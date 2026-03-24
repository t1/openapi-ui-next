package com.github.t1.openapi.ui.demo;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.json.JsonObject;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;

import static com.github.t1.openapi.ui.demo.PetStatus.adopted;
import static com.github.t1.openapi.ui.demo.PetStatus.available;

@Path("/pets")
@Tag(name = "pets")
public class PetResource {
    static final List<Pet> PETS = new ArrayList<>(List.of(
            new Pet(1, "Max", available, 1),
            new Pet(2, "Bella", adopted, 1),
            new Pet(3, "Charlie", available, 2)
    ));

    private static long nextId = 4;

    @GET @Produces(APPLICATION_JSON) @Operation(summary = "List all pets", description = "Returns all pets from the system. "
            + "Supports filtering by status via the optional query parameter. "
            + "Results are sorted by ID in ascending order. "
            + "The response includes each pet's name, species, status, and owner information. "
            + "Pagination is not yet supported; all matching records are returned in a single response. "
            + "For large datasets, consider using the status filter to reduce the result set.")
    public List<Pet> list(@QueryParam("status") PetStatus status) {
        if (status == null) return PETS;
        return PETS.stream().filter(p -> p.status() == status).toList();
    }

    @GET @Path("/{id}") @Produces({APPLICATION_JSON, APPLICATION_XML})
    @Operation(summary = "Get a pet by ID", description = "Returns a single pet by its unique identifier.")
    @APIResponse(responseCode = "200", description = "A pet",
            content = {@Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = Pet.class)),
                    @Content(mediaType = APPLICATION_XML, schema = @Schema(implementation = Pet.class))})
    @APIResponse(responseCode = "404", description = "Pet not found")
    public Pet get(@PathParam("id") long id) {
        return PETS.stream()
                .filter(p -> p.id() == id).findFirst()
                .orElseThrow(() -> new PetNotFoundException(id));
    }

    @POST @Operation(summary = "Add a new pet")
    public Response create(@RequestBody @Valid Pet pet) {
        validateOwner(pet.ownerId());
        var created = new Pet(nextId++, pet.name(), pet.status(), pet.ownerId());
        PETS.add(created);
        return Response.status(201).entity(created).build();
    }

    @PUT @Path("/{id}") @Operation(summary = "Update a pet", deprecated = true)
    @Deprecated
    public Pet update(@PathParam("id") long id, @RequestBody @Valid Pet pet) {
        validateOwner(pet.ownerId());
        for (int i = 0; i < PETS.size(); i++) {
            if (PETS.get(i).id() == id) {
                var updated = new Pet(id, pet.name(), pet.status(), pet.ownerId());
                PETS.set(i, updated);
                return updated;
            }
        }
        throw new PetNotFoundException(id);
    }

    @PATCH @Path("/{id}") @Consumes(APPLICATION_JSON) @Operation(summary = "Partially update a pet")
    public Pet patch(@PathParam("id") long id, @RequestBody(content = @Content(
            mediaType = APPLICATION_JSON,
            examples = {
                    @ExampleObject(name = "Rename", value = "{\"name\": \"Rex\"}"),
                    @ExampleObject(name = "Multiple fields", value = "{\"name\": \"Rex\", \"status\": \"adopted\"}")
            })) JsonObject patch) {
        for (int i = 0; i < PETS.size(); i++) {
            var existing = PETS.get(i);
            if (existing.id() == id) {
                if (patch.containsKey("ownerId")) validateOwner(patch.getJsonNumber("ownerId").longValue());
                var updated = new Pet(id,
                        patch.containsKey("name") ? patch.getString("name") : existing.name(),
                        patch.containsKey("status") ? PetStatus.valueOf(patch.getString("status")) : existing.status(),
                        patch.containsKey("ownerId") ? patch.getJsonNumber("ownerId").longValue() : existing.ownerId());
                PETS.set(i, updated);
                return updated;
            }
        }
        throw new PetNotFoundException(id);
    }

    private static void validateOwner(long ownerId) {
        var ownerExists = OwnerResource.OWNERS.stream().anyMatch(o -> o.id() == ownerId);
        if (!ownerExists) throw new InvalidOwnerIdException(ownerId);
    }

    @DELETE @Path("/{id}") @Operation(summary = "Delete a pet")
    @Tag(name = "admin")
    public Response delete(@PathParam("id") long id) {
        var removed = PETS.removeIf(p -> p.id() == id);
        if (!removed) throw new PetNotFoundException(id);
        return Response.noContent().build();
    }
}
