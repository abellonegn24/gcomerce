package com.gadaaistar.storefront.auth;

import com.gadaaistar.storefront.ratelimit.RateLimit;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

/**
 * Storefront auth endpoints. All under {@code /api/storefront/v1/auth}.
 *
 * <ul>
 *   <li>POST /register              — create an account, get tokens, queue verify email</li>
 *   <li>POST /login                 — verify credentials, get tokens</li>
 *   <li>POST /refresh               — rotate refresh token, get new access token</li>
 *   <li>POST /logout                — revoke the presented refresh token</li>
 *   <li>GET  /me                    — current customer profile (requires Bearer)</li>
 *   <li>POST /resend-verification   — re-queue the verify email (always 202)</li>
 *   <li>POST /verify-email          — consume verify token → ACTIVE</li>
 *   <li>POST /forgot-password       — queue reset email (always 202)</li>
 *   <li>POST /reset-password        — consume reset token + new password → revoke all sessions</li>
 * </ul>
 */
@Path("/api/storefront/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth (Storefront)", description = "Customer authentication and session management")
public class AuthResource {

    @Inject AuthService authService;
    @Inject SecurityContextHolder securityContext;
    @Inject EmailVerificationService emailVerification;
    @Inject PasswordResetService     passwordReset;
    @Inject AuthMapper               mapper;

    @Context HttpHeaders headers;
    @Context UriInfo     uriInfo;

    @POST
    @Path("/register")
    @RateLimit(bucket = "auth-register", max = 5, windowSeconds = 600)
    @Operation(summary = "Create a new customer account (B2C or B2B).")
    public Response register(@Valid AuthDtos.RegisterRequest req,
                             @RestHeader("X-Forwarded-For") String forwardedFor,
                             @RestHeader("User-Agent") String userAgent) {
        var body = authService.register(req, clientIp(forwardedFor), userAgent);
        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    @POST
    @Path("/login")
    @RateLimit(bucket = "auth-login", max = 10, windowSeconds = 60)
    @Operation(summary = "Verify credentials; returns access + refresh tokens.")
    public AuthDtos.AuthResponse login(@Valid AuthDtos.LoginRequest req,
                                       @RestHeader("X-Forwarded-For") String forwardedFor,
                                       @RestHeader("User-Agent") String userAgent) {
        return authService.login(req, clientIp(forwardedFor), userAgent);
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Rotate refresh token; returns a fresh access + refresh pair.")
    public AuthDtos.AuthResponse refresh(@Valid AuthDtos.RefreshRequest req,
                                         @RestHeader("X-Forwarded-For") String forwardedFor,
                                         @RestHeader("User-Agent") String userAgent) {
        return authService.refresh(req, clientIp(forwardedFor), userAgent);
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Revoke the presented refresh token. Always returns 204.")
    public Response logout(AuthDtos.LogoutRequest req) {
        authService.logout(req == null ? null : req.refreshToken());
        return Response.noContent().build();
    }

    @GET
    @Path("/me")
    @Operation(summary = "Return the profile of the currently authenticated customer.")
    public AuthDtos.CustomerDto me() {
        var customerId = securityContext.requireCustomer();
        return authService.me(customerId);
    }

    @jakarta.ws.rs.PATCH
    @Path("/me")
    @Operation(summary = "Partially update the signed-in customer's profile. Null / blank fields are ignored.")
    public AuthDtos.CustomerDto updateMe(@Valid AuthDtos.UpdateProfileRequest req) {
        var customerId = securityContext.requireCustomer();
        return authService.updateProfile(customerId, req);
    }

    @POST
    @Path("/me/change-password")
    @RateLimit(bucket = "auth-change-password", max = 5, windowSeconds = 600)
    @Operation(summary = "Change the signed-in customer's password. Revokes every existing refresh token on success.")
    public Response changePassword(@Valid AuthDtos.ChangePasswordRequest req) {
        var customerId = securityContext.requireCustomer();
        authService.changePassword(customerId, req);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Email verification
    // -------------------------------------------------------------------------

    @POST
    @Path("/resend-verification")
    @RateLimit(bucket = "auth-resend-verification", max = 3, windowSeconds = 600)
    @Operation(summary = "Re-send the email-verification link. Always returns 202 regardless of whether the email exists.")
    public Response resendVerification(@Valid AuthDtos.ResendVerificationRequest req) {
        emailVerification.resend(req.email());
        return Response.status(Response.Status.ACCEPTED)
                .entity(AuthDtos.AckResponse.queued(
                        "If an account with that email exists, a verification message has been sent."))
                .build();
    }

    @POST
    @Path("/verify-email")
    @Operation(summary = "Consume a verification token; on success the customer becomes ACTIVE (B2C).")
    public AuthDtos.CustomerDto verifyEmail(@Valid AuthDtos.VerifyEmailRequest req) {
        var customer = emailVerification.confirm(req.token());
        return mapper.toDto(customer);
    }

    // -------------------------------------------------------------------------
    // Password reset
    // -------------------------------------------------------------------------

    @POST
    @Path("/forgot-password")
    @RateLimit(bucket = "auth-forgot-password", max = 3, windowSeconds = 600)
    @Operation(summary = "Request a password-reset email. Always returns 202 regardless of whether the email exists.")
    public Response forgotPassword(@Valid AuthDtos.ForgotPasswordRequest req,
                                    @RestHeader("X-Forwarded-For") String forwardedFor) {
        passwordReset.start(req.email(), clientIp(forwardedFor));
        return Response.status(Response.Status.ACCEPTED)
                .entity(AuthDtos.AckResponse.queued(
                        "If an account with that email exists, a reset link has been sent."))
                .build();
    }

    @POST
    @Path("/reset-password")
    @RateLimit(bucket = "auth-reset-password", max = 10, windowSeconds = 600)
    @Operation(summary = "Consume a reset token, set a new password, and revoke every existing session.")
    public Response resetPassword(@Valid AuthDtos.ResetPasswordRequest req) {
        passwordReset.complete(req.token(), req.newPassword());
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------

    private static String clientIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) return null;
        var comma = forwardedFor.indexOf(',');
        return comma < 0 ? forwardedFor.trim() : forwardedFor.substring(0, comma).trim();
    }
}
