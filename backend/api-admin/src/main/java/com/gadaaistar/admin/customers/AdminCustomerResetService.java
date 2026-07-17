package com.gadaaistar.admin.customers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.auth.CustomerRefreshTokenRepository;
import com.gadaaistar.persistence.auth.PasswordResetToken;
import com.gadaaistar.persistence.auth.PasswordResetTokenRepository;
import com.gadaaistar.persistence.customer.CustomerRepository;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Admin-initiated password reset for a customer account.
 *
 * <p>Mirrors the storefront's {@code PasswordResetService.start()} but bound to
 * the admin app: the caller is an authenticated admin (not the customer
 * themselves), so we skip the anti-enumeration silence — if the customer
 * doesn't exist we return a 404 to the admin instead of hiding it.
 *
 * <p>Side effects on success:
 * <ol>
 *   <li>Any previously-unused password-reset tokens for the customer are
 *       marked used (so the freshly-mailed link is the only live one).</li>
 *   <li>Every live refresh token for the customer is revoked — they'll be
 *       logged out on every device the moment their access token expires
 *       (15 min max).</li>
 *   <li>An HTML+text email with the reset link is queued via Quarkus Mailer.</li>
 * </ol>
 *
 * <p>The reset link points at the storefront (customer-facing) app — the same
 * URL the storefront's own "Forgot password" flow uses, so
 * {@code PasswordResetService.complete()} handles the exchange.
 */
@ApplicationScoped
public class AdminCustomerResetService {

    private static final Logger LOG = Logger.getLogger(AdminCustomerResetService.class);
    private static final SecureRandom RNG = new SecureRandom();

    @Inject CustomerRepository              customers;
    @Inject PasswordResetTokenRepository    tokens;
    @Inject CustomerRefreshTokenRepository  refreshTokens;
    @Inject ReactiveMailer                  mailer;

    @Location("mail/password-reset.html") Template resetHtml;
    @Location("mail/password-reset.txt")  Template resetText;

    @ConfigProperty(name = "gadaa.auth.admin-password-reset-ttl-minutes")
    long ttlMinutes;

    @ConfigProperty(name = "gadaa.frontend.storefront-url")
    String storefrontBaseUrl;

    @ConfigProperty(name = "quarkus.mailer.from")
    String fromAddress;

    // -------------------------------------------------------------------------

    /**
     * Issue a fresh password-reset token for the given customer and email it
     * to their inbox. Returns the token's expiry so the UI can display it.
     *
     * @param customerId the target customer
     * @param actorId    the admin performing the action (audit log only)
     * @return non-null response describing what was issued
     */
    @Transactional
    public CustomersDtos.ActionResponse start(long customerId, long actorId) {
        var customer = customers.findByIdOptional(customerId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));

        // 1. Invalidate any previously-unused tokens so only the new link works.
        long invalidated = tokens.update(
                "usedAt = ?1 WHERE customer.id = ?2 AND usedAt IS NULL",
                Instant.now(), customer.id);

        // 2. Mint a new one-shot token; store only the SHA-256 hex.
        var raw = randomToken();
        var row = new PasswordResetToken();
        row.customer  = customer;
        row.tokenHash = sha256Hex(raw);
        row.expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes));
        row.ipAddress = "admin:" + actorId;    // Audit trail: who initiated
        tokens.persist(row);

        // 3. Revoke live sessions — the customer must sign in again after
        //    completing the reset. Prevents an attacker with a stolen session
        //    from acting while the customer is choosing a new password.
        long revoked = refreshTokens.revokeAllForCustomer(customer.id);

        // 4. Send the email.
        var link = trimTrailingSlash(storefrontBaseUrl) + "/reset-password/" + raw;
        sendMail(customer.email, customer.displayName(), link).exceptionally(err -> {
            LOG.errorf(err, "Failed to queue admin password-reset email to %s (customer id=%d)",
                    customer.email, customer.id);
            return null;
        });

        LOG.infof("Admin id=%d issued password-reset for customer id=%d "
                + "(invalidated=%d prior tokens, revoked=%d sessions, expires=%s)",
                actorId, customer.id, invalidated, revoked, row.expiresAt);

        return new CustomersDtos.ActionResponse(
                customer.id,
                customer.status == null ? null : customer.status.name(),
                "Password-reset email queued. Link expires in " + ttlMinutes + " minutes."
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CompletionStage<Void> sendMail(String to, String displayName, String resetLink) {
        var subject = "Reset your Gadaa Istar password";
        var html = resetHtml.data(
                "displayName", displayName,
                "resetLink",   resetLink,
                "expiresMinutes", ttlMinutes
        ).render();
        var text = resetText.data(
                "displayName", displayName,
                "resetLink",   resetLink,
                "expiresMinutes", ttlMinutes
        ).render();

        var mail = Mail.withHtml(to, subject, html)
                .setText(text)
                .setFrom(fromAddress);
        return mailer.send(mail)
                .invoke(() -> LOG.infof("Sent admin password-reset email to %s", to))
                .subscribeAsCompletionStage();
    }

    private static String randomToken() {
        var bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Matches {@code EmailVerificationService.sha256Hex} in api-storefront so
     * both apps produce identical hashes for the same raw token. Kept
     * private + duplicated here rather than moving to web-common because the
     * only two callers are these two auth services.
     */
    static String sha256Hex(String input) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable — JVM broken", e);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
