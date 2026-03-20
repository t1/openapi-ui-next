package com.github.t1.openapi.ui.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
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
    @Test void shouldReturnConstraintViolationForBlankVisitReason() {
        given()
                .contentType(APPLICATION_JSON)
                .body("{\"date\":\"2024-01-01\",\"reason\":\"\"}")
                .when().post("/pets/1/visits")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", is("urn:problem-type:constraint-violation"))
                .body("violations.size()", is(1))
                .body("violations[0].field", is("reason"));
    }

    @Test void shouldReturnProblemDetailsForVisitOnNonexistentPet() {
        given()
                .contentType(APPLICATION_JSON)
                .body("{\"date\":\"2024-01-01\",\"reason\":\"Checkup\"}")
                .when().post("/pets/999/visits")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", is("urn:problem-type:pet-not-found"))
                .body("detail", is("Pet with ID 999 not found"));
    }

    @Test void shouldReturnProblemDetailsForUnknownVisit() {
        given()
                .when().get("/pets/1/visits/999")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", is("urn:problem-type:visit-not-found"))
                .body("detail", is("Visit with ID 999 not found"));
    }
}
