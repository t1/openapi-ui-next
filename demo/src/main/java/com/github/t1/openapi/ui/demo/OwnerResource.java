package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.links.Link;
import org.eclipse.microprofile.openapi.annotations.links.LinkParameter;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY;

@Path("/owners")
@Tag(name = "owners")
public class OwnerResource {
    static final List<Owner> OWNERS = new ArrayList<>(List.of(
            new Owner(1, "Alice", "alice@example.com"),
            new Owner(2, "Bob", "bob@example.com")
    ));

    @GET @Operation(summary = "List all owners")
    public List<Owner> list() {
        return OWNERS;
    }

    static Owner findById(long id) {
        return OWNERS.stream()
                .filter(o -> o.id() == id).findFirst()
                .orElseThrow(() -> new OwnerNotFoundException(id));
    }

    @GET @Path("/{id}")
    @Operation(operationId = "getOwner", summary = "Get an owner by ID")
    @APIResponse(responseCode = "200", description = "An owner",
            links = @Link(name = "pets", operationId = "listOwnerPets",
                    description = "List pets owned by this person",
                    parameters = @LinkParameter(name = "ownerId", expression = "$response.body#/id")))
    public OwnerResponse get(@PathParam("id") long id) {
        var owner = findById(id);
        var pets = PetResource.PETS.stream()
                .filter(p -> p.ownerId == owner.id())
                .map(p -> new PetSummary(p.id, p.name, p.status))
                .toList();
        return new OwnerResponse(owner.id(), owner.name(), owner.email(), pets);
    }

    @GET @Path("/{ownerId}/pets")
    @Operation(operationId = "listOwnerPets", summary = "List pets for an owner")
    @APIResponse(responseCode = "200", description = "List of pets",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(type = ARRAY, implementation = Pet.class)),
            extensions = @Extension(name = "x-links", parseValue = true,
                    value = "{\"pet\":{\"operationId\":\"getPet\","
                            + "\"description\":\"Get details of a pet\","
                            + "\"parameters\":{\"id\":\"$response.body#/[*]/id\"}},"
                            + "\"visits\":{\"operationId\":\"listPetVisits\","
                            + "\"description\":\"List visits for a pet\","
                            + "\"parameters\":{\"petId\":\"$response.body#/[*]/id\"}}}"))
    public List<Pet> listPets(@PathParam("ownerId") long ownerId) {
        return PetResource.PETS.stream()
                .filter(p -> p.ownerId == ownerId).toList();
    }
}
