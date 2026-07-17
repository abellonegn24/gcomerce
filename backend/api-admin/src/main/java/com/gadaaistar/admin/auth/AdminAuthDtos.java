package com.gadaaistar.admin.auth;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request / response DTOs for the admin auth endpoints.
 *
 * <p>Login is a two-step process:
 * <ol>
 *   <li>POST /login       — email + password ⇒ {@link LoginChallengeResponse}</li>
 *   <li>POST /verify-mfa  — challenge token + 6-digit code ⇒ {@link AuthResponse}</li>
 * </ol>
 *
 * <p>On the first login of a fresh admin, {@link LoginChallengeResponse#setup}
 * is populated with the newly-generated TOTP secret + provisioning URI so the
 * client can render a QR code. On subsequent logins {@code setup} is {@code null}
 * and the client jumps straight to the 6-digit input.
 */
public final class AdminAuthDtos {

    private AdminAuthDtos() {}

    // -------------------------------------------------------------------------
    // Login step 1 — password
    // -------------------------------------------------------------------------

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    /** State machine for the login flow. */
    public enum LoginState {
        /** MFA is already enrolled — client should collect a 6-digit code and call /verify-mfa. */
        MFA_REQUIRED,
        /** First-time login — client must display the QR / secret and collect the first code. */
        MFA_ENROLLMENT_REQUIRED
    }

    public record LoginChallengeResponse(
            LoginState state,
            String challengeToken,          // short-lived JWT (typ=mfa-challenge) tying step 1 to step 2
            Instant challengeExpiresAt,
            MfaSetup setup                  // populated only when state == MFA_ENROLLMENT_REQUIRED
    ) {}

    public record MfaSetup(
            String secretBase32,            // scan this into an authenticator app
            String otpauthUri,              // full otpauth://totp/... URI (for QR code rendering)
            String issuer                   // display label
    ) {}

    // -------------------------------------------------------------------------
    // Login step 2 — TOTP
    // -------------------------------------------------------------------------

    public record VerifyMfaRequest(
            @NotBlank String challengeToken,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "code must be 6 digits") String code
    ) {}

    // -------------------------------------------------------------------------
    // Refresh / logout
    // -------------------------------------------------------------------------

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record LogoutRequest(
            String refreshToken
    ) {}

    // -------------------------------------------------------------------------
    // Responses
    // -------------------------------------------------------------------------

    public record AuthResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            AdminUserDto admin
    ) {}

    public record AdminUserDto(
            Long id,
            String email,
            @Size(max = 200) String fullName,
            String role,
            String status,
            boolean mfaEnrolled,
            Instant lastLoginAt
    ) {}
}
