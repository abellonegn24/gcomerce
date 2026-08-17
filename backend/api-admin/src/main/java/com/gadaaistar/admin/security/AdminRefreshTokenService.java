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
 *
 * <p><strong>Admin session policy (stricter than storefront):</strong>
 * <ul>
 *   <li><em>Idle timeout</em> ({@code gadaa.admin.session.idle-timeout-minutes}):
 *       the refresh token expires this many minutes after it was last issued /
 *       rotated. If the admin does not exercise the token within the window,
 *       they must sign in again.</li>
 *   <li><em>Absolute cap</em> ({@code gadaa.admin.session.max-lifetime-minutes}):
 *       measured from {@code sessionStartedAt} — the moment the session began.
 *       The value is carried forward across every rotation in the chain so a
 *       rotating token cannot indefinitely extend a session past this wall.</li>
 * </ul>
 */
@ApplicationScoped
public class AdminRefreshTokenService {

    private static final Logger LOG = Logger.getLogger(AdminRefreshTokenService.class);
    private static final SecureRandom RNG = new SecureRandom();

    @Inject AdminRefreshTokenRepository repo;

    @ConfigProperty(name = "gadaa.admin.session.idle-timeout-minutes", defaultValue = "10")
    int idleTimeoutMinutes;

    @ConfigProperty(name = "gadaa.admin.session.max-lifetime-minutes", defaultValue = "480")
    int maxLifetimeMinutes;

    // -------------------------------------------------------------------------

    @Transactional
    public IssuedRefresh issueForAdmin(AdminUser admin, String ip, String userAgent) {
        var now = Instant.now();
        return persist(admin, ip, userAgent, null, now, now);
    }

    @Transactional
    public RotationResult rotate(String presented, String ip, String userAgent) {
        var parts = parse(presented);
        var row = repo.findByIdOptional(parts.id())
                .orElseThrow(() -> new UnauthorizedException("REFRESH_UNKNOWN", "Refresh token not recognized."));

        var now = Instant.now();

        if (row.revokedAt != null) {
            LOG.warnf("Admin refresh-token reuse detected for adminUser=%d; revoking chain.", row.adminUser.id);
            repo.revokeAllForAdmin(row.adminUser.id);
            throw new UnauthorizedException("REFRESH_REUSE", "Refresh token was already used. Please sign in again.");
        }
        if (now.isAfter(row.expiresAt)) {
            throw new UnauthorizedException("REFRESH_EXPIRED", "Refresh token has expired.");
        }
        if (row.sessionStartedAt != null && now.isAfter(sessionAbsoluteExpiry(row.sessionStartedAt))) {
            // Belt-and-braces: even if the row's own expiresAt hasn't hit, refuse
            // once the underlying session has been alive past its absolute cap.
            LOG.infof("Admin session id=%s exceeded absolute lifetime cap; forcing re-auth.", row.id);
            row.revokedAt = now;
            throw new UnauthorizedException("SESSION_EXPIRED",
                    "This admin session has reached its maximum lifetime. Please sign in again.");
        }
        if (!constantTimeEquals(row.tokenHash, sha256Hex(parts.secret()))) {
            LOG.warnf("Admin refresh-token secret mismatch for tokenId=%s; revoking.", parts.id());
            row.revokedAt = now;
            throw new UnauthorizedException("REFRESH_INVALID", "Refresh token is invalid.");
        }

        var admin = row.adminUser;
        // Preserve the session's original start time so the rotated token still
        // ages against the same absolute cap.
        var sessionStartedAt = row.sessionStartedAt != null ? row.sessionStartedAt : row.issuedAt;
        var next  = persist(admin, ip, userAgent, null, now, sessionStartedAt);

        row.revokedAt    = now;
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

    private IssuedRefresh persist(AdminUser admin, String ip, String userAgent, String replacedById,
                                   Instant issuedAt, Instant sessionStartedAt) {
        var row = new AdminRefreshToken();
        row.id               = UUID.randomUUID().toString();
        row.adminUser        = admin;
        row.issuedAt         = issuedAt;
        row.sessionStartedAt = sessionStartedAt;
        // The new row expires at the earlier of (a) idle timeout from now, or
        // (b) the session's absolute cap. This is what prevents infinite rotation.
        var idleExpiry      = issuedAt.plus(Duration.ofMinutes(idleTimeoutMinutes));
        var sessionExpiry   = sessionAbsoluteExpiry(sessionStartedAt);
        row.expiresAt        = idleExpiry.isBefore(sessionExpiry) ? idleExpiry : sessionExpiry;
        row.ipAddress        = ip;
        row.userAgent        = userAgent;
        row.replacedById     = replacedById;

        var secret = randomSecret();
        row.tokenHash = sha256Hex(secret);
        repo.persist(row);

        return new IssuedRefresh(row.id + "." + secret, row);
    }

    private Instant sessionAbsoluteExpiry(Instant sessionStartedAt) {
        return sessionStartedAt.plus(Duration.ofMinutes(maxLifetimeMinutes));
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
