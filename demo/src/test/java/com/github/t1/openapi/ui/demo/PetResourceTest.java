package com.github.t1.openapi.ui.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PetResourceTest {
    @Test void shouldListPets() {
        given()
                .when().get("/pets")
                .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(3))
                .body("name", hasItems("Max", "Bella", "Charlie"));
    }

    @Test
    void shouldGetPetById() {
        given()
                .when().get("/pets/1")
                .then()
                .statusCode(200)
                .body("name", is("Max"))
                .body("status", is("available"));
    }

    @Test void shouldFilterPetsByStatus() {
        given()
                .queryParam("status", "adopted")
                .when().get("/pets")
                .then()
                .statusCode(200)
                .body("$.size()", is(1))
                .body("[0].name", is("Bella"));
    }

    @Test void shouldReturnProblemDetailsForUnknownPet() {
        given()
                .when().get("/pets/999")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", is("urn:problem-type:pet-not-found"))
                .body("title", is("Bad Request"))
                .body("status", is(400))
                .body("detail", is("Pet with ID 999 not found"));
    }

    @Test void shouldCreatePet() {
        given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"Luna\",\"status\":\"available\",\"ownerId\":2}")
                .when().post("/pets")
                .then()
                .statusCode(201)
                .body("name", is("Luna"))
                .body("id", notNullValue());
    }

    @Test void shouldUpdatePet() {
        var id = given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"Temp\",\"status\":\"available\",\"ownerId\":1}")
                .when().post("/pets")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"Updated\",\"status\":\"available\",\"ownerId\":2}")
                .when().put("/pets/" + id)
                .then()
                .statusCode(200)
                .body("name", is("Updated"))
                .body("status", is("available"));
    }

    @Test void shouldReturnProblemDetailsForPutUnknownPet() {
        given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"X\",\"status\":\"available\",\"ownerId\":1}")
                .when().put("/pets/999")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", is("urn:problem-type:pet-not-found"));
    }

    @Test void shouldPatchPet() {
        var id = given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"Temp\",\"status\":\"available\",\"ownerId\":1}")
                .when().post("/pets")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"Patched\"}")
                .when().patch("/pets/" + id)
                .then()
                .statusCode(200)
                .body("name", is("Patched"))
                .body("status", is("available"));
    }

    @Test void shouldReturnProblemDetailsForPatchUnknownPet() {
        given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"X\"}")
                .when().patch("/pets/999")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", is("urn:problem-type:pet-not-found"));
    }

    @Test void shouldReturnProblemDetailsForDeleteUnknownPet() {
        given()
                .when().delete("/pets/999")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", is("urn:problem-type:pet-not-found"));
    }

    @Test void shouldReturnConstraintViolationForBlankPetName() {
        given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"\",\"status\":\"available\",\"ownerId\":1}")
                .when().post("/pets")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", is("urn:problem-type:constraint-violation"))
                .body("title", is("Bad Request"))
                .body("status", is(400))
                .body("violations.size()", is(1))
                .body("violations[0].field", is("name"))
                .body("violations[0].message", is("must not be blank"));
    }

    @Test void shouldReturnProblemDetailsForInvalidOwnerId() {
        given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"Rex\",\"status\":\"available\",\"ownerId\":999}")
                .when().post("/pets")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", is("urn:problem-type:invalid-owner-id"))
                .body("detail", is("Owner with ID 999 does not exist"));
    }

    @Test void shouldDeletePet() {
        // create a pet to delete, to avoid affecting other tests
        var id = given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"Temp\",\"status\":\"available\",\"ownerId\":1}")
                .when().post("/pets")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/pets/" + id)
                .then()
                .statusCode(204);
    }
}
