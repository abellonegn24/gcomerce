package com.gadaaistar.admin.admins;

import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin-user management endpoints. All under {@code /api/admin/v1/admin-users}.
 *
 * <p>Every endpoint requires an authenticated admin JWT. The MFA-reset
 * endpoint further requires the {@code SUPERADMIN} role.
 */
@Path("/api/admin/v1/admin-users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin Users", description = "List admins and manage their MFA state (SUPERADMIN-gated).")
public class AdminUsersResource {

    @Inject AdminUsersService     service;
    @Inject SecurityContextHolder securityContext;

    @GET
    @Operation(summary = "List every admin user with their MFA + role status.")
    public AdminUsersDtos.ListResponse list() {
        securityContext.requireAdmin();
        return service.list();
    }

    @POST
    @Path("/{id}/mfa/reset")
    @Operation(summary = "Wipe target admin's TOTP secret + revoke all their sessions. SUPERADMIN only.")
    public AdminUsersDtos.ResetMfaResponse resetMfa(@PathParam("id") long targetId) {
        var actorId = securityContext.requireAdmin();
        securityContext.requireAnyRole("SUPERADMIN");
        return service.resetMfa(targetId, actorId);
    }

    @POST
    @Path("/{id}/unlock")
    @Operation(summary = "Clear a brute-force lockout on the admin account (INSA CWE-307). SUPERADMIN only.")
    public AdminUsersDtos.ResetMfaResponse unlock(@PathParam("id") long targetId) {
        var actorId = securityContext.requireAdmin();
        securityContext.requireAnyRole("SUPERADMIN");
        return service.unlock(targetId, actorId);
    }
}
