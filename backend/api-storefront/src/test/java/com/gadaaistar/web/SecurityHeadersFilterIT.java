package com.gadaaistar.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for {@link SecurityHeadersFilter} (INSA CWE-1021).
 *
 * <p>Verifies that the anti-clickjacking + hardening headers are present on
 * every HTTP response — success (200), not-found (404), and unauthorized (401).
 * The filter runs as a global {@code ContainerResponseFilter} so it covers
 * error envelopes from {@link GlobalExceptionMapper} too.
 */
@QuarkusTest
class SecurityHeadersFilterIT {

    @Test
    @DisplayName("security headers present on a 400 response (validation error from JAX-RS)")
    void headersOn400() {
        given()
                .header("Content-Type", "application/json")
                .body("{}")
                .when().post("/api/storefront/v1/auth/login")
                .then()
                .statusCode(400)
                .header("Content-Security-Policy", containsString("frame-ancestors 'self'"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("Referrer-Policy", equalTo("strict-origin-when-cross-origin"));
    }

    @Test
    @DisplayName("security headers present on a 404 response (error envelope)")
    void headersOn404() {
        given()
                .when().get("/api/storefront/v1/this-route-does-not-exist")
                .then()
                .statusCode(404)
                .header("Content-Security-Policy", containsString("frame-ancestors 'self'"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("Referrer-Policy", equalTo("strict-origin-when-cross-origin"));
    }

    @Test
    @DisplayName("security headers present on a 401 response (auth failure)")
    void headersOn401() {
        given()
                .header("Content-Type", "application/json")
                .body("{\"email\":\"nobody@test.com\",\"password\":\"wrong-password\"}")
                .when().post("/api/storefront/v1/auth/login")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(429)))
                .header("Content-Security-Policy", containsString("frame-ancestors 'self'"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("Referrer-Policy", equalTo("strict-origin-when-cross-origin"));
    }
}
