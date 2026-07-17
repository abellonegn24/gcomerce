package com.gadaaistar.storefront.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import com.gadaaistar.common.exceptions.UnauthorizedException;
import com.gadaaistar.persistence.auth.CustomerRefreshToken;
import com.gadaaistar.persistence.auth.CustomerRefreshTokenRepository;
import com.gadaaistar.persistence.customer.Customer;
// NOTE: Kept in this package (not web-common) because it is coupled to the
// Customer entity. Admin uses a parallel service in the api-admin module.

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Issues, rotates, and revokes customer refresh tokens.
 *
 * <p>Token format returned to the client:
 * <pre>
 *     {tokenId}.{secret}
 * </pre>
 * where {@code tokenId} is the row UUID and {@code secret} is a 32-byte
 * random value hex-encoded. Only SHA-256 of {@code secret} is stored server-side.
 *
 * <p>On refresh we require:
 *   1. tokenId → row exists, not revoked, not expired
 *   2. SHA-256(secret) matches token_hash
 *   3. rotate: revoke old row (link replaced_by), issue new
 *   4. token reuse (already-revoked row presented) → revoke entire subject chain
 */
@ApplicationScoped
public class RefreshTokenService {

    private static final Logger LOG = Logger.getLogger(RefreshTokenService.class);
    private static final SecureRandom RNG = new SecureRandom();

    @Inject CustomerRefreshTokenRepository repo;

    @ConfigProperty(name = "gadaa.jwt.refresh-ttl-days") int refreshTtlDays;

    // -------------------------------------------------------------------------

    @Transactional
    public IssuedRefresh issueForCustomer(Customer customer, String ip, String userAgent) {
        return persist(customer, ip, userAgent, null);
    }

    @Transactional
    public RotationResult rotate(String presented, String ip, String userAgent) {
        var parts = parse(presented);
        var row = repo.findByIdOptional(parts.id())
                .orElseThrow(() -> new UnauthorizedException("REFRESH_UNKNOWN", "Refresh token not recognized."));

        if (row.revokedAt != null) {
            // Reuse of an already-revoked token → someone leaked; nuke the whole family.
            LOG.warnf("Refresh-token reuse detected for customer=%d; revoking chain.", row.customer.id);
            repo.revokeAllForCustomer(row.customer.id);
            throw new UnauthorizedException("REFRESH_REUSE", "Refresh token was already used. Please sign in again.");
        }
        if (Instant.now().isAfter(row.expiresAt)) {
            throw new UnauthorizedException("REFRESH_EXPIRED", "Refresh token has expired.");
        }
        if (!constantTimeEquals(row.tokenHash, sha256Hex(parts.secret()))) {
            // Wrong secret against a live tokenId — treat as tampering.
            LOG.warnf("Refresh-token secret mismatch for tokenId=%s; revoking.", parts.id());
            row.revokedAt = Instant.now();
            throw new UnauthorizedException("REFRESH_INVALID", "Refresh token is invalid.");
        }

        var customer = row.customer;
        var next = persist(customer, ip, userAgent, null);

        row.revokedAt = Instant.now();
        row.replacedById = next.tokenRow().id;

        return new RotationResult(customer, next.presentedToken(), next.tokenRow().expiresAt);
    }

    @Transactional
    public void revoke(String presented) {
        try {
            var parts = parse(presented);
            repo.findByIdOptional(parts.id()).ifPresent(row -> {
                if (row.revokedAt == null) row.revokedAt = Instant.now();
            });
        } catch (UnauthorizedException ignored) {
            // silent on logout — never leak details
        }
    }

    @Transactional
    public void revokeAllForCustomer(Long customerId) {
        repo.revokeAllForCustomer(customerId);
    }

    // -------------------------------------------------------------------------

    private IssuedRefresh persist(Customer customer, String ip, String userAgent, String replacedById) {
        var row = new CustomerRefreshToken();
        row.id           = UUID.randomUUID().toString();
        row.customer     = customer;
        row.issuedAt     = Instant.now();
        row.expiresAt    = row.issuedAt.plus(Duration.ofDays(refreshTtlDays));
        row.ipAddress    = ip;
        row.userAgent    = userAgent;
        row.replacedById = replacedById;

        var secret = randomSecret();
        row.tokenHash = sha256Hex(secret);
        repo.persist(row);

        return new IssuedRefresh(row.id + "." + secret, row);
    }

    private static String randomSecret() {
        var bytes = new byte[32];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static PresentedParts parse(String presented) {
        if (presented == null) {
            throw new UnauthorizedException("REFRESH_INVALID", "Refresh token missing.");
        }
        var idx = presented.indexOf('.');
        if (idx <= 0 || idx == presented.length() - 1) {
            throw new UnauthorizedException("REFRESH_INVALID", "Refresh token is malformed.");
        }
        return new PresentedParts(presented.substring(0, idx), presented.substring(idx + 1));
    }

    // -------------------------------------------------------------------------

    private record PresentedParts(String id, String secret) {}
    public  record IssuedRefresh(String presentedToken, CustomerRefreshToken tokenRow) {}
    public  record RotationResult(Customer customer, String newRefreshToken, Instant expiresAt) {}
}
