package com.github.t1.openapi.ui.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class DelayResponseFilterTest {
    @Test void shouldDelayResponseWhenXDelayResponseHeaderIsSet() {
        var start = System.currentTimeMillis();

        given()
                .header("X-Delay-Response", "1")
                .when().get("/pets")
                .then()
                .statusCode(200);

        then(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(1000);
    }

    @Test void shouldNotDelayWithoutHeader() {
        var start = System.currentTimeMillis();

        given()
                .when().get("/pets")
                .then()
                .statusCode(200);

        then(System.currentTimeMillis() - start).isLessThan(1000);
    }

    @Test void shouldReturnProblemDetailsForInvalidDelayValue() {
        given()
                .header("X-Delay-Response", "abc")
                .when().get("/pets")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("title", is("Bad Request"))
                .body("status", is(400))
                .body("detail", containsString("abc"));
    }
}
