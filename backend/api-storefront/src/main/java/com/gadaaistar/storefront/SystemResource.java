package com.gadaaistar.storefront;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.gadaaistar.common.SupportedCurrency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Phase-0 health / hello endpoint. Verifies that the API server is up,
 * that PostgreSQL is reachable (indirectly via Flyway startup migrations), and
 * exposes the platform's basic self-description.
 *
 * <p>Once Phase 1 lands, this will be replaced by real domain endpoints.
 */
@Path("/api/storefront/v1/system")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "System", description = "Platform meta and health endpoints")
public class SystemResource {

    @Inject
    @ConfigProperty(name = "quarkus.application.name")
    String appName;

    @Inject
    @ConfigProperty(name = "quarkus.application.version")
    String appVersion;

    @Inject
    @ConfigProperty(name = "gadaa.company.legal-name")
    String companyLegalName;

    @GET
    @Path("/hello")
    @Operation(summary = "Sanity-check endpoint; returns platform metadata.")
    public Map<String, Object> hello() {
        return Map.of(
                "app", appName,
                "version", appVersion,
                "company", companyLegalName,
                "supportedCurrencies", List.of(SupportedCurrency.values()),
                "timestamp", Instant.now().toString(),
                "message", "Gadaa Istar Storefront API is running."
        );
    }
}
