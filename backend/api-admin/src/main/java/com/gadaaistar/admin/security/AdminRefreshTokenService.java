package com.gadaaistar.admin.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import com.gadaaistar.common.exceptions.UnauthorizedException;
import com.gadaaistar.persistence.admin.AdminRefreshToken;
import com.gadaaistar.persistence.admin.AdminRefreshTokenRepository;
import com.gadaaistar.persistence.admin.AdminUser;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Issues, rotates, and revokes admin refresh tokens.
 *
 * <p>Same {tokenId}.{secret} client format and rotation semantics as
 * {@code com.gadaaistar.storefront.security.RefreshTokenService}, but points
 * at {@link AdminUser} instead of Customer. Extracted into the admin module
 * because it is tightly coupled to the admin entity graph.
 */
@ApplicationScoped
public class AdminRefreshTokenService {

    private static final Logger LOG = Logger.getLogger(AdminRefreshTokenService.class);
    private static final SecureRandom RNG = new SecureRandom();

    @Inject AdminRefreshTokenRepository repo;

    @ConfigProperty(name = "gadaa.jwt.refresh-ttl-days") int refreshTtlDays;

    // -------------------------------------------------------------------------

    @Transactional
    public IssuedRefresh issueForAdmin(AdminUser admin, String ip, String userAgent) {
        return persist(admin, ip, userAgent, null);
    }

    @Transactional
    public RotationResult rotate(String presented, String ip, String userAgent) {
        var parts = parse(presented);
        var row = repo.findByIdOptional(parts.id())
                .orElseThrow(() -> new UnauthorizedException("REFRESH_UNKNOWN", "Refresh token not recognized."));

        if (row.revokedAt != null) {
            LOG.warnf("Admin refresh-token reuse detected for adminUser=%d; revoking chain.", row.adminUser.id);
            repo.revokeAllForAdmin(row.adminUser.id);
            throw new UnauthorizedException("REFRESH_REUSE", "Refresh token was already used. Please sign in again.");
        }
        if (Instant.now().isAfter(row.expiresAt)) {
            throw new UnauthorizedException("REFRESH_EXPIRED", "Refresh token has expired.");
        }
        if (!constantTimeEquals(row.tokenHash, sha256Hex(parts.secret()))) {
            LOG.warnf("Admin refresh-token secret mismatch for tokenId=%s; revoking.", parts.id());
            row.revokedAt = Instant.now();
            throw new UnauthorizedException("REFRESH_INVALID", "Refresh token is invalid.");
        }

        var admin = row.adminUser;
        var next  = persist(admin, ip, userAgent, null);

        row.revokedAt    = Instant.now();
        row.replacedById = next.tokenRow().id;

        return new RotationResult(admin, next.presentedToken(), next.tokenRow().expiresAt);
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
    public void revokeAllForAdmin(Long adminUserId) {
        repo.revokeAllForAdmin(adminUserId);
    }

    // -------------------------------------------------------------------------

    private IssuedRefresh persist(AdminUser admin, String ip, String userAgent, String replacedById) {
        var row = new AdminRefreshToken();
        row.id           = UUID.randomUUID().toString();
        row.adminUser    = admin;
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
    public  record IssuedRefresh(String presentedToken, AdminRefreshToken tokenRow) {}
    public  record RotationResult(AdminUser admin, String newRefreshToken, Instant expiresAt) {}
}
