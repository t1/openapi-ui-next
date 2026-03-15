package com.github.t1.openapi.ui.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class VisitResourceTest {
    @Test void shouldListVisitsForPet() {
        given()
                .when().get("/pets/1/visits")
                .then()
                .statusCode(200)
                .body("$.size()", is(2))
                .body("[0].reason", is("Annual checkup"))
                .body("[1].reason", is("Vaccination"));
    }

    @Test void shouldGetVisitByIdForPet() {
        given()
                .when().get("/pets/1/visits/1")
                .then()
                .statusCode(200)
                .body("reason", is("Annual checkup"))
                .body("date", is("2024-01-15"));
    }
    @Test void shouldReturn404ForUnknownVisit() {
        given()
                .when().get("/pets/1/visits/999")
                .then()
                .statusCode(404);
    }
}
