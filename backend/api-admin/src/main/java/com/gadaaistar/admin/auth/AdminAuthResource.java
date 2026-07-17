package com.gadaaistar.admin.auth;

import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

/**
 * Admin auth endpoints. All under {@code /api/admin/v1/auth}.
 *
 * <ul>
 *   <li>POST /login       — password step; returns MFA challenge</li>
 *   <li>POST /verify-mfa  — TOTP step; returns access + refresh tokens</li>
 *   <li>POST /refresh     — rotate refresh token, get new access token</li>
 *   <li>POST /logout      — revoke the presented refresh token</li>
 *   <li>GET  /me          — current admin profile (requires Bearer)</li>
 * </ul>
 */
@Path("/api/admin/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth (Admin)", description = "Admin authentication with mandatory TOTP MFA")
public class AdminAuthResource {

    @Inject AdminAuthService authService;
    @Inject SecurityContextHolder securityContext;

    @POST
    @Path("/login")
    @Operation(summary = "Step 1 — verify email + password; returns MFA challenge.")
    public AdminAuthDtos.LoginChallengeResponse login(@Valid AdminAuthDtos.LoginRequest req) {
        return authService.login(req);
    }

    @POST
    @Path("/verify-mfa")
    @Operation(summary = "Step 2 — submit the 6-digit TOTP code; returns access + refresh tokens.")
    public AdminAuthDtos.AuthResponse verifyMfa(@Valid AdminAuthDtos.VerifyMfaRequest req,
                                                  @RestHeader("X-Forwarded-For") String forwardedFor,
                                                  @RestHeader("User-Agent") String userAgent) {
        return authService.verifyMfa(req, clientIp(forwardedFor), userAgent);
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Rotate refresh token; returns a fresh access + refresh pair.")
    public AdminAuthDtos.AuthResponse refresh(@Valid AdminAuthDtos.RefreshRequest req,
                                                @RestHeader("X-Forwarded-For") String forwardedFor,
                                                @RestHeader("User-Agent") String userAgent) {
        return authService.refresh(req, clientIp(forwardedFor), userAgent);
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Revoke the presented refresh token. Always returns 204.")
    public Response logout(AdminAuthDtos.LogoutRequest req) {
        authService.logout(req == null ? null : req.refreshToken());
        return Response.noContent().build();
    }

    @GET
    @Path("/me")
    @Operation(summary = "Return the profile of the currently authenticated admin.")
    public AdminAuthDtos.AdminUserDto me() {
        var adminId = securityContext.requireAdmin();
        return authService.me(adminId);
    }

    // -------------------------------------------------------------------------

    private static String clientIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) return null;
        var comma = forwardedFor.indexOf(',');
        return comma < 0 ? forwardedFor.trim() : forwardedFor.substring(0, comma).trim();
    }
}
