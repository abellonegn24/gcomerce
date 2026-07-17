package com.gadaaistar.storefront.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.gadaaistar.common.CustomerStatus;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.common.exceptions.ValidationException;
import com.gadaaistar.persistence.auth.EmailVerificationToken;
import com.gadaaistar.persistence.auth.EmailVerificationTokenRepository;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.storefront.mail.MailService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * One-shot email verification tokens.
 *
 * <p>Client receives the raw token in the confirm URL. Server stores only the
 * SHA-256 hash of it, so a DB dump doesn't disclose verifier tokens.
 *
 * <p>Tokens auto-expire (see {@code gadaa.auth.email-verification-ttl-minutes})
 * and can only be used once. Requesting a new token invalidates all previously
 * issued (but not yet used) verification tokens for the same customer.
 */
@ApplicationScoped
public class EmailVerificationService {

    private static final Logger LOG = Logger.getLogger(EmailVerificationService.class);
    private static final SecureRandom RNG = new SecureRandom();

    @Inject CustomerRepository               customers;
    @Inject EmailVerificationTokenRepository tokens;
    @Inject MailService                      mail;

    @ConfigProperty(name = "gadaa.auth.email-verification-ttl-minutes", defaultValue = "60")
    long ttlMinutes;

    @ConfigProperty(name = "gadaa.frontend.storefront-url", defaultValue = "http://localhost:4200")
    String storefrontBaseUrl;

    // -------------------------------------------------------------------------

    /**
     * Issue a token and enqueue the verification email. Safe to call
     * repeatedly — earlier unused tokens for the same customer are revoked
     * to prevent stockpiling.
     */
    @Transactional
    public void start(Customer customer) {
        if (customer.emailVerifiedAt != null) {
            // Already verified — no-op is preferable to leaking that state.
            return;
        }

        // Invalidate any previously issued, still-active tokens for this user.
        tokens.update("usedAt = ?1 WHERE customer.id = ?2 AND usedAt IS NULL",
                Instant.now(), customer.id);

        var rawToken = randomToken();
        var row = new EmailVerificationToken();
        row.customer  = customer;
        row.tokenHash = sha256Hex(rawToken);
        row.expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes));
        tokens.persist(row);

        var link = storefrontBaseUrl + "/verify-email/" + rawToken;
        mail.sendVerifyEmail(customer.email, customer.displayName(), link, ttlMinutes);
        LOG.infof("Queued verification email for customer id=%d", customer.id);
    }

    /**
     * Public entrypoint invoked by {@code POST /auth/resend-verification}.
     * We deliberately return the same response whether or not the email
     * matches a real account (anti-enumeration).
     */
    @Transactional
    public void resend(String email) {
        var normalized = email == null ? "" : email.trim().toLowerCase();
        customers.findByEmail(normalized).ifPresent(this::start);
    }

    /**
     * Consume a verification token. Success sets {@code emailVerifiedAt} and
     * flips {@code PENDING_VERIFICATION → ACTIVE} for B2C accounts.
     */
    @Transactional
    public Customer confirm(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ValidationException("Verification token is required.");
        }
        var row = tokens.findByHash(sha256Hex(rawToken))
                .orElseThrow(() -> new NotFoundException("Verification token", "invalid or already used"));

        if (row.usedAt != null) {
            throw new ValidationException("This verification link has already been used.");
        }
        if (Instant.now().isAfter(row.expiresAt)) {
            throw new ValidationException("This verification link has expired. Please request a new one.");
        }

        var customer = row.customer;
        row.usedAt = Instant.now();
        if (customer.emailVerifiedAt == null) {
            customer.emailVerifiedAt = row.usedAt;
        }
        // B2B accounts move to ACTIVE only after an admin approves them; email
        // verification alone doesn't unlock login for them.
        if (customer.status == CustomerStatus.PENDING_VERIFICATION) {
            customer.status = CustomerStatus.ACTIVE;
        }
        LOG.infof("Customer id=%d completed email verification.", customer.id);
        return customer;
    }

    // -------------------------------------------------------------------------

    /** URL-safe 32-byte random token. */
    private static String randomToken() {
        var bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String input) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
