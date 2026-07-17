package com.gadaaistar.storefront.auth;

import java.time.Instant;

import com.gadaaistar.common.CustomerStatus;
import com.gadaaistar.common.CustomerType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request / response DTOs for the storefront auth endpoints.
 *
 * <p>Kept in one file so the auth API surface is easy to read; split into
 * per-record files if this grows.
 */
public final class AuthDtos {

    private AuthDtos() {}

    // -------------------------------------------------------------------------
    // Requests
    // -------------------------------------------------------------------------

    public record RegisterRequest(
            @Email @NotBlank @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 120) String firstName,
            @NotBlank @Size(max = 120) String lastName,
            String customerType,          // "B2C" or "B2B"; defaults to B2C
            @Size(max = 255) String companyName,
            @Size(max = 64)  @Pattern(regexp = "^[A-Za-z0-9\\- ]*$", message = "TIN may contain only letters, digits, spaces, and hyphens") String companyTin,
            @Size(max = 64)  String companyVatNumber,
            @Size(max = 64)  String phone,
            @Pattern(regexp = "^(USD|EUR|GBP)?$", message = "Currency must be USD, EUR, or GBP") String preferredCurrency,
            @Size(max = 10) String preferredLocale,
            @Size(max = 32) String customerGroupCode   // optional; will be validated server-side
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record LogoutRequest(
            String refreshToken
    ) {}

    public record ForgotPasswordRequest(
            @Email @NotBlank String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {}

    public record VerifyEmailRequest(
            @NotBlank String token
    ) {}

    public record ResendVerificationRequest(
            @Email @NotBlank String email
    ) {}

    /**
     * Partial update for the signed-in customer's profile.
     * Null / blank fields are ignored — no field can be blanked out via this endpoint.
     * Email and customerType are intentionally NOT editable here (email change
     * requires re-verification; type change is an admin-only operation).
     */
    public record UpdateProfileRequest(
            @Size(max = 120) String firstName,
            @Size(max = 120) String lastName,
            @Size(max = 255) String companyName,
            @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9\\- ]*$", message = "TIN may contain only letters, digits, spaces, and hyphens") String companyTin,
            @Size(max = 64) String companyVatNumber,
            @Size(max = 64) String phone,
            @Pattern(regexp = "^(USD|EUR|GBP)?$", message = "Currency must be USD, EUR, or GBP") String preferredCurrency,
            @Size(max = 10) String preferredLocale
    ) {}

    /**
     * Change password for the signed-in customer. On success EVERY refresh
     * token for this customer is revoked (mirrors the reset-password flow) so
     * all other devices are forced to sign in again.
     */
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {}

    /**
     * Ack shape returned by {@code /forgot-password} and {@code /resend-verification}.
     * These endpoints always return HTTP 202 with this generic acknowledgment so
     * they can't be used to enumerate accounts.
     */
    public record AckResponse(String status, String message) {
        public static AckResponse queued(String message) {
            return new AckResponse("QUEUED", message);
        }
    }

    // -------------------------------------------------------------------------
    // Responses
    // -------------------------------------------------------------------------

    public record AuthResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            CustomerDto customer
    ) {}

    public record CustomerDto(
            Long id,
            String email,
            CustomerType customerType,
            CustomerStatus status,
            String firstName,
            String lastName,
            String companyName,
            String companyTin,
            String phone,
            String preferredCurrency,
            String preferredLocale,
            String customerGroupCode,
            boolean emailVerified,
            Instant createdAt
    ) {}
}
