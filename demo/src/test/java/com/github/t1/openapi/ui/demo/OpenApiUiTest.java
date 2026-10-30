package com.github.t1.openapi.ui.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;


@QuarkusTest
class OpenApiUiTest {
    @Test void shouldServeIndexHtml() {
        given()
                .when().get("/openapi-ui/index.html")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .body(containsString("<title>Pet Store</title>"))
                .body(containsString("htmx-config"));
    }

    @Test void shouldServeMethodFragment() {
        given()
                .when().get("/openapi-ui/pets/GET.html")
                .then()
                .statusCode(200)
                .body(containsString("List all pets"));
    }

    @Test void shouldServeHtmxVendorScript() {
        given()
                .when().get("/openapi-ui/vendor/htmx.min.js")
                .then()
                .statusCode(200)
                .body(containsString("htmx"));
    }
}
