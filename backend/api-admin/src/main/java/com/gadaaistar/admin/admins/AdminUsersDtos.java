package com.gadaaistar.admin.admins;

import java.time.Instant;
import java.util.List;

/**
 * Request / response DTOs for the admin-users management endpoints.
 * Kept intentionally small — Phase 2.2 only wires listing + MFA reset.
 */
public final class AdminUsersDtos {

    private AdminUsersDtos() {}

    public record AdminUserSummary(
            Long id,
            String email,
            String fullName,
            String role,
            String status,
            boolean mfaEnrolled,
            Instant lastLoginAt,
            Instant createdAt
    ) {}

    public record ListResponse(List<AdminUserSummary> items) {}

    /** Response body from {@code POST /admin-users/{id}/mfa/reset}. */
    public record ResetMfaResponse(
            Long adminUserId,
            String email,
            String status,          // Always "MFA_RESET"
            String message
    ) {}
}
