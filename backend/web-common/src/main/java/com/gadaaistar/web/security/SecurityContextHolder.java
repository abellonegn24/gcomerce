package com.gadaaistar.web.security;

import java.util.Arrays;
import java.util.Optional;

import com.gadaaistar.common.Audience;
import com.gadaaistar.common.exceptions.ForbiddenException;
import com.gadaaistar.common.exceptions.UnauthorizedException;

import jakarta.enterprise.context.RequestScoped;

/**
 * Per-request principal carrier. Populated by {@link JwtAuthFilter} when the
 * incoming request presents a valid Bearer token. Endpoints call
 * {@link #requireCustomer()} or {@link #requireAdmin()} at the top to enforce
 * auth, and {@link #requireAnyRole(String...)} for RBAC.
 */
@RequestScoped
public class SecurityContextHolder {

    private Long principalId;
    private String email;
    private Audience audience;
    private String[] roles = new String[0];

    public void set(Audience audience, long principalId, String email, String[] roles) {
        this.audience    = audience;
        this.principalId = principalId;
        this.email       = email;
        this.roles       = roles == null ? new String[0] : roles;
    }

    public Optional<Long>     currentPrincipalId() { return Optional.ofNullable(principalId); }
    public Optional<String>   currentEmail()       { return Optional.ofNullable(email); }
    public Optional<Audience> currentAudience()    { return Optional.ofNullable(audience); }
    public String[]           currentRoles()       { return roles; }
    public boolean            isAuthenticated()    { return principalId != null; }

    public long requireCustomer() {
        if (audience != Audience.STOREFRONT || principalId == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        return principalId;
    }

    public long requireAdmin() {
        if (audience != Audience.ADMIN || principalId == null) {
            throw new UnauthorizedException("Admin authentication required.");
        }
        return principalId;
    }

    /**
     * Enforces role-based authorization: throws {@link ForbiddenException}
     * unless the current principal has at least one of {@code allowed}.
     * Case-sensitive match against the {@code roles} claim.
     */
    public void requireAnyRole(String... allowed) {
        if (principalId == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        if (allowed == null || allowed.length == 0) return;
        for (var role : allowed) {
            for (var mine : roles) {
                if (role.equals(mine)) return;
            }
        }
        throw new ForbiddenException(
                "Missing required role. Need one of: " + Arrays.toString(allowed));
    }
}
