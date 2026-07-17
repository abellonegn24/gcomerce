package com.gadaaistar.persistence.auth;

import java.time.Instant;

import com.gadaaistar.persistence.customer.Customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A rotating refresh token issued to a customer session.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@link #id} is a UUID surfaced to the client as the first half of the
 *       refresh-token string. The second half is a random secret whose
 *       SHA-256 hash is stored in {@link #tokenHash}.</li>
 *   <li>On refresh we mark the current row {@code revoked_at = now()} and
 *       set {@code replaced_by_id} to the newly issued token's id.</li>
 *   <li>If a client presents an already-revoked token we treat it as token
 *       reuse and revoke the entire chain — classic refresh-token rotation.</li>
 * </ul>
 */
@Entity
@Table(name = "customer_refresh_token")
public class CustomerRefreshToken {

    @Id
    @Column(length = 36)
    public String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;

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
