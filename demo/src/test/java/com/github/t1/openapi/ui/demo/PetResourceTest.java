package com.github.t1.openapi.ui.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PetResourceTest {
    @Test
    void shouldListPets() {
        given()
                .when().get("/pets")
                .then()
                .statusCode(200)
                .body("$.size()", is(2))
                .body("[0].name", is("Max"))
                .body("[1].name", is("Bella"));
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
                .queryParam("status", "available")
                .when().get("/pets")
                .then()
                .statusCode(200)
                .body("$.size()", is(1))
                .body("[0].name", is("Max"));
    }

    @Test
    void shouldReturn404ForUnknownPet() {
        given()
                .when().get("/pets/999")
                .then()
                .statusCode(404);
    }
}
