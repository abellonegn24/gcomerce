package com.gadaaistar.persistence.admin;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A rotating refresh token issued to an admin session.
 *
 * <p>Mirrors {@link com.gadaaistar.persistence.auth.CustomerRefreshToken}
 * but points at {@link AdminUser} instead of Customer. Same
 * {tokenId}.{secret} client format; SHA-256 of the secret half is stored.
 */
@Entity
@Table(name = "admin_refresh_token")
public class AdminRefreshToken {

    @Id
    @Column(length = 36)
    public String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    public AdminUser adminUser;

    @Column(name = "token_hash", nullable = false, length = 64)
    public String tokenHash;

    @Column(name = "issued_at", nullable = false)
    public Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "revoked_at")
    public Instant revokedAt;

    @Column(name = "ip_address", length = 64)
    public String ipAddress;

    @Column(name = "user_agent", length = 500)
    public String userAgent;

    @Column(name = "replaced_by_id", length = 36)
    public String replacedById;

    public boolean isActive() {
        return revokedAt == null && Instant.now().isBefore(expiresAt);
    }
}
