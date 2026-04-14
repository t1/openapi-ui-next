package com.github.t1.openapi.ui.demo;

import io.vertx.core.http.HttpServerResponse;
import jakarta.json.JsonObject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.links.Link;
import org.eclipse.microprofile.openapi.annotations.links.LinkParameter;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirements;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.github.t1.openapi.ui.demo.PetStatus.adopted;
import static com.github.t1.openapi.ui.demo.PetStatus.available;
import static io.vertx.core.http.Cookie.cookie;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static jakarta.ws.rs.core.Response.Status.CREATED;

@Path("/pets")
@Tag(name = "pets")
public class PetResource {
    static final List<Pet> PETS = new ArrayList<>(List.of(
            new Pet(1, "Max", available, 1),
            new Pet(2, "Bella", adopted, 1),
            new Pet(3, "Charlie", available, 2)
    ));

    private static long nextId = 4;

    @GET @Produces(APPLICATION_JSON)
    @Operation(summary = "List all pets", description = "Returns all pets from the system. "
                                                        + "Supports filtering by status via the optional query parameter. "
                                                        + "Results are sorted by ID in ascending order. "
                                                        + "The response includes each pet's name, species, status, and owner information. "
                                                        + "Pagination is not yet supported; all matching records are returned in a single response. "
                                                        + "For large datasets, consider using the status filter to reduce the result set.")
    @APIResponse(responseCode = "200", description = "A list of pets",
            headers = @Header(name = "Set-Cookie", description = "Session tracking cookie",
                    schema = @Schema(type = SchemaType.STRING)))
    public List<Pet> list(
            @QueryParam("status") PetStatus status,
            @HeaderParam("X-Request-ID") @Parameter(description = "Unique request correlation identifier") String requestId,
            @CookieParam("session_id") @Parameter(description = "Session identifier for tracking") String sessionId,
            @Context HttpServerResponse response) {
        response.addCookie(cookie("session_id", "demo123").setPath("/").setHttpOnly(true));
        if (status == null) return PETS;
        return PETS.stream().filter(p -> p.status == status).toList();
    }

    @GET @Path("/{id}") @Produces({APPLICATION_JSON, APPLICATION_XML})
    @Operation(operationId = "getPet", summary = "Get a pet by ID", description = "Returns a single pet by its unique identifier.")
    @APIResponse(responseCode = "200", description = "A pet",
            headers = @Header(name = "X-Request-ID", description = "Echoed request identifier",
                    schema = @Schema(type = SchemaType.STRING)),
            content = {@Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = PetResponse.class)),
                    @Content(mediaType = APPLICATION_XML, schema = @Schema(implementation = PetResponse.class))},
            links = {
                    @Link(name = "owner", operationId = "getOwner",
                            description = "Get the owner of this pet",
                            parameters = @LinkParameter(name = "id", expression = "$response.body#/owner/id")),
                    @Link(name = "visits", operationId = "listPetVisits",
                            description = "List visits for this pet",
                            parameters = @LinkParameter(name = "petId", expression = "$response.body#/id"))},
            extensions = @Extension(name = "x-links", parseValue = true,
                    value = "{\"visitDetail\":{\"operationId\":\"getVisit\","
                            + "\"description\":\"Get details of this visit\","
                            + "\"parameters\":{\"visitId\":\"$response.body#/visits[*]/id\"}}}"))
    @APIResponse(responseCode = "400", description = "Business error",
            content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "default", description = "Unexpected error",
            content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = ProblemDetails.class)))
    public PetResponse get(@PathParam("id") long id, @QueryParam("showVisits") boolean showVisits) {
        var pet = findById(id);
        var owner = OwnerResource.OWNERS.stream()
                .filter(o -> o.id() == pet.ownerId).findFirst()
                .map(o -> new OwnerSummary(o.id(), o.name()))
                .orElse(null);
        var visits = showVisits
                ? VisitResource.VISITS.stream()
                .filter(v -> v.petId() == pet.id)
                .map(v -> new VisitSummary(v.id(), v.date(), v.reason()))
                .toList()
                : null;
        return new PetResponse(pet.id, pet.name, pet.status, owner, visits);
    }

    @POST @Produces(APPLICATION_JSON)
    @Operation(summary = "Add a new pet")
    public Response create(
            @HeaderParam("X-Api-Key") @Parameter(required = true, description = "API key for authentication") String apiKey,
            @RequestBody @Valid Pet pet) {
        validateOwner(pet.ownerId);
        var created = new Pet(nextId++, pet.name, pet.status, pet.ownerId);
        PETS.add(created);
        return Response.status(CREATED).entity(created).build();
    }

    @PUT @Path("/{id}") @Produces(APPLICATION_JSON) @Operation(summary = "Update a pet", deprecated = true)
    @Deprecated
    public Pet update(@PathParam("id") long id, @RequestBody @Valid Pet pet) {
        validateOwner(pet.ownerId);
        var updated = new Pet(id, pet.name, pet.status, pet.ownerId);
        PETS.set(indexOfPet(id), updated);
        return updated;
    }

    @PATCH @Path("/{id}") @Consumes(APPLICATION_JSON) @Produces(APPLICATION_JSON) @Operation(summary = "Partially update a pet")
    public Pet patch(@PathParam("id") long id, @RequestBody(content = @Content(
            mediaType = APPLICATION_JSON,
            examples = {
                    @ExampleObject(name = "Rename", value = "{\"name\": \"Rex\"}"),
                    @ExampleObject(name = "Multiple fields", value = "{\"name\": \"Rex\", \"status\": \"adopted\"}")
            })) JsonObject patch) {
        var i = indexOfPet(id);
        var existing = PETS.get(i);
        if (patch.containsKey("ownerId")) validateOwner(patch.getJsonNumber("ownerId").longValue());
        var updated = new Pet(id,
                patch.containsKey("name") ? patch.getString("name") : existing.name,
                patch.containsKey("status") ? PetStatus.valueOf(patch.getString("status")) : existing.status,
                patch.containsKey("ownerId") ? patch.getJsonNumber("ownerId").longValue() : existing.ownerId);
        PETS.set(i, updated);
        return updated;
    }

    static Pet findById(long id) {
        return PETS.stream()
                .filter(p -> p.id == id).findFirst()
                .orElseThrow(() -> new PetNotFoundException(id));
    }

    static int indexOfPet(long id) {
        for (int i = 0; i < PETS.size(); i++)
            if (PETS.get(i).id == id) return i;
        throw new PetNotFoundException(id);
    }

    private static void validateOwner(long ownerId) {
        OwnerResource.findById(ownerId);
    }

    @DELETE @Path("/{id}")
    @SecurityRequirements({
            @SecurityRequirement(name = "BearerAuth"),
            @SecurityRequirement(name = "ApiKeyAuth")})
    @Operation(summary = "Delete a pet")
    @Tag(name = "admin")
    @APIResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = ProblemDetails.class)))
    public Response delete(@PathParam("id") long id,
                           @HeaderParam("Authorization") String authorization) {
        if (authorization == null || !authorization.equals("Bearer demo-token"))
            return Response.status(Response.Status.UNAUTHORIZED)
                    .type(ProblemDetails.MEDIA_TYPE)
                    .entity(new ProblemDetails(URI.create("urn:problem-type:unauthorized"), "Unauthorized", 401, "Use `demo-token` as the bearer token", null))
                    .build();
        var removed = PETS.removeIf(p -> p.id == id);
        if (!removed) throw new PetNotFoundException(id);
        return Response.noContent().build();
    }

    @POST @Path("/search") @Consumes("application/x-www-form-urlencoded") @Produces(APPLICATION_JSON)
    @Operation(summary = "Search pets by form", description = "Search for pets using form-encoded parameters")
    public List<Pet> searchForm(
            @FormParam("name") String name,
            @FormParam("status") PetStatus status,
            @FormParam("includeAdopted") Boolean includeAdopted) {
        return PETS.stream()
                .filter(p -> name == null || p.name.toLowerCase().contains(name.toLowerCase()))
                .filter(p -> status == null || p.status == status)
                .filter(p -> includeAdopted == null || !includeAdopted || p.status == adopted)
                .toList();
    }
}
