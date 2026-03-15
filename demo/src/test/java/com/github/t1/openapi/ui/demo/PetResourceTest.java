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

    @Test void shouldReturn404ForUnknownPet() {
        given()
                .when().get("/pets/999")
                .then()
                .statusCode(404);
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

    @Test void shouldReturn404ForPutUnknownPet() {
        given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"X\",\"status\":\"available\",\"ownerId\":1}")
                .when().put("/pets/999")
                .then()
                .statusCode(404);
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

    @Test void shouldReturn404ForPatchUnknownPet() {
        given()
                .contentType(APPLICATION_JSON)
                .body("{\"name\":\"X\"}")
                .when().patch("/pets/999")
                .then()
                .statusCode(404);
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
