package com.gadaaistar.admin;

import java.time.Instant;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Public health / smoke-test resource for the admin API.
 * All other admin endpoints require authentication.
 */
@Path("/api/admin/v1/system")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "System (Admin)", description = "Health, version, and public metadata")
public class SystemResource {

    @GET
    @Path("/hello")
    @Operation(summary = "Sanity-check endpoint. Returns app metadata.")
    public Map<String, Object> hello() {
        return Map.of(
                "app",     "gadaa-istar-admin",
                "version", "0.1.0-SNAPSHOT",
                "time",    Instant.now().toString()
        );
    }
}
