package com.github.t1.openapi.ui.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class OwnerResourceTest {
    @Test void shouldListOwners() {
        given()
                .when().get("/owners")
                .then()
                .statusCode(200)
                .body("$.size()", is(2))
                .body("[0].name", is("Alice"))
                .body("[1].name", is("Bob"));
    }
    @Test void shouldGetOwnerById() {
        given()
                .when().get("/owners/1")
                .then()
                .statusCode(200)
                .body("name", is("Alice"))
                .body("email", is("alice@example.com"));
    }
    @Test void shouldReturn404ForUnknownOwner() {
        given()
                .when().get("/owners/999")
                .then()
                .statusCode(404);
    }
    @Test void shouldListPetsForOwner() {
        given()
                .when().get("/owners/1/pets")
                .then()
                .statusCode(200)
                .body("$.size()", is(2))
                .body("[0].name", is("Max"))
                .body("[1].name", is("Bella"));
    }
}
