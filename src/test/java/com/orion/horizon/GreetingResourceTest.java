package com.orion.horizon;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class GreetingResourceTest {
    @Test
    void testHelloEndpoint() {
        given()
          .when().get("/hello")
          .then()
             .statusCode(200)
             .body(is("Hello from Quarkus REST"));
    }

    @Test
    void healthEndpoint() {
        given()
          .when().get("/q/health")
          .then()
             .statusCode(200);
    }

    @Test
    void openapiEndpoint() {
        given()
          .when().get("/q/openapi")
          .then()
             .statusCode(200);
    }

}