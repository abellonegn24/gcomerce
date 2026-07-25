package com.gadaaistar.storefront.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.common.exceptions.ValidationException;
import com.gadaaistar.persistence.auth.PasswordResetToken;
import com.gadaaistar.persistence.auth.PasswordResetTokenRepository;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.storefront.mail.MailService;
import com.gadaaistar.storefront.security.RefreshTokenService;
import com.gadaaistar.web.security.PasswordHasher;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * One-shot password-reset tokens.
 *
 * <p>Similar model to {@link EmailVerificationService}: raw token in the URL,
 * SHA-256 in the DB. The {@code start()} method always returns silently even
 * for unknown emails so the endpoint can't be used to enumerate accounts.
 *
 * <p>Completing a reset revokes every refresh-token for the customer — anyone
 * who was signed in on other devices is forcibly logged out.
 */
@ApplicationScoped
public class PasswordResetService {

    private static final Logger LOG = Logger.getLogger(PasswordResetService.class);
    private static final SecureRandom RNG = new SecureRandom();

    @Inject CustomerRepository          customers;
    @Inject PasswordResetTokenRepository tokens;
    @Inject MailService                  mail;
    @Inject PasswordHasher               hasher;
    @Inject RefreshTokenService          refreshTokens;

    @ConfigProperty(name = "gadaa.auth.password-reset-ttl-minutes", defaultValue = "30")
    long ttlMinutes;

    @ConfigProperty(name = "gadaa.frontend.storefront-url", defaultValue = "https://gedaaistar.et")
    String storefrontBaseUrl;

    // -------------------------------------------------------------------------

    /**
     * Public entrypoint invoked by {@code POST /auth/forgot-password}. Always
     * returns silently — never let the caller distinguish "unknown email" from
     * "email sent" (anti-enumeration).
     */
    @Transactional
    public void start(String email, String ipAddress) {
        var normalized = email == null ? "" : email.trim().toLowerCase();
        var maybeCustomer = customers.findByEmail(normalized);
        if (maybeCustomer.isEmpty()) {
            LOG.debugf("Password-reset requested for unknown email; ignoring.");
            return;
        }
        var customer = maybeCustomer.get();

        // Invalidate previously-issued unused tokens
        tokens.update("usedAt = ?1 WHERE customer.id = ?2 AND usedAt IS NULL",
                Instant.now(), customer.id);

        var rawToken = randomToken();
        var row = new PasswordResetToken();
        row.customer  = customer;
        row.tokenHash = EmailVerificationService.sha256Hex(rawToken);
        row.expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes));
        row.ipAddress = ipAddress;
        tokens.persist(row);

        var link = storefrontBaseUrl + "/reset-password/" + rawToken;
        mail.sendPasswordReset(customer.email, customer.displayName(), link, ttlMinutes);
        LOG.infof("Queued password-reset email for customer id=%d", customer.id);
    }

    /**
     * Consume a password-reset token and set a new password. On success, all
     * refresh tokens for the customer are revoked.
     */
    @Transactional
    public void complete(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ValidationException("Reset token is required.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters long.",
                    java.util.Map.of("newPassword", "min length 8"));
        }
        var row = tokens.findByHash(EmailVerificationService.sha256Hex(rawToken))
                .orElseThrow(() -> new NotFoundException("Password-reset token", "invalid or already used"));

        if (row.usedAt != null) {
            throw new ValidationException("This reset link has already been used.");
        }
        if (Instant.now().isAfter(row.expiresAt)) {
            throw new ValidationException("This reset link has expired. Please request a new one.");
        }

        var customer = row.customer;
        customer.passwordHash = hasher.hash(newPassword);
        row.usedAt = Instant.now();

        // Force sign-out on every device using existing refresh tokens.
        refreshTokens.revokeAllForCustomer(customer.id);

        LOG.infof("Customer id=%d completed password reset; all sessions revoked.", customer.id);
    }

    // -------------------------------------------------------------------------

    private static String randomToken() {
        var bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
