package com.gadaaistar.storefront.mail;

import java.util.concurrent.CompletionStage;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Thin wrapper around Quarkus's {@link ReactiveMailer} that renders a Qute
 * template pair (html + text) and sends the message.
 *
 * <p>All customer-facing transactional email flows go through this service so
 * we have one place to plug in DKIM, unsubscribe headers, and (later)
 * SendGrid/Mailgun analytics.
 *
 * <p>Templates are resolved from {@code src/main/resources/templates/mail/…}:
 * pass the file stem (e.g. {@code "verify-email"}) and both {@code .html} and
 * {@code .txt} variants are rendered.
 */
@ApplicationScoped
public class MailService {

    private static final Logger LOG = Logger.getLogger(MailService.class);

    @Inject ReactiveMailer mailer;

    @Location("mail/verify-email.html")   Template verifyEmailHtml;
    @Location("mail/verify-email.txt")    Template verifyEmailText;
    @Location("mail/password-reset.html") Template passwordResetHtml;
    @Location("mail/password-reset.txt")  Template passwordResetText;
    @Location("mail/order-confirmation.html") Template orderConfirmationHtml;
    @Location("mail/order-confirmation.txt")  Template orderConfirmationText;
    @Location("mail/order-cancelled.html")    Template orderCancelledHtml;
    @Location("mail/order-cancelled.txt")     Template orderCancelledText;

    @ConfigProperty(name = "quarkus.mailer.from") String fromAddress;
    @ConfigProperty(name = "gadaa.frontend.storefront-url") String storefrontUrl;

    // -------------------------------------------------------------------------

    /**
     * Send the email-verification message.
     *
     * @param toEmail        recipient
     * @param displayName    what to greet them by (first name or company)
     * @param verifyLink     absolute URL the user should click to confirm
     * @param expiresMinutes how many minutes the link stays valid (for copy)
     */
    public CompletionStage<Void> sendVerifyEmail(String toEmail, String displayName,
                                                  String verifyLink, long expiresMinutes) {
        var subject = "Confirm your Gadaa Istar account";
        return sendPair(toEmail, subject, verifyEmailHtml, verifyEmailText,
                "displayName", displayName,
                "verifyLink",  verifyLink,
                "expiresMinutes", expiresMinutes);
    }

    /**
     * Send the password-reset message.
     *
     * @param toEmail        recipient
     * @param displayName    what to greet them by
     * @param resetLink      absolute URL the user should click to set a new password
     * @param expiresMinutes how many minutes the link stays valid
     */
    public CompletionStage<Void> sendPasswordReset(String toEmail, String displayName,
                                                    String resetLink, long expiresMinutes) {
        var subject = "Reset your Gadaa Istar password";
        return sendPair(toEmail, subject, passwordResetHtml, passwordResetText,
                "displayName", displayName,
                "resetLink",   resetLink,
                "expiresMinutes", expiresMinutes);
    }

    /**
     * Send the order-confirmation message after successful order placement.
     *
     * @param toEmail       recipient
     * @param displayName   what to greet them by
     * @param invoiceNumber the order's invoice number
     * @param currency      order currency code (e.g. USD)
     * @param grandTotal    formatted grand total string
     * @param orderUuid     the order UUID (used to build the deep link)
     */
    public CompletionStage<Void> sendOrderConfirmation(String toEmail, String displayName,
                                                        String invoiceNumber, String currency,
                                                        String grandTotal, String orderUuid) {
        var subject = "Your Gadaa Istar order " + invoiceNumber;
        var orderLink = storefrontUrl + "/account/orders/" + orderUuid;
        return sendPair(toEmail, subject, orderConfirmationHtml, orderConfirmationText,
                "displayName", displayName,
                "invoiceNumber", invoiceNumber,
                "currency", currency,
                "grandTotal", grandTotal,
                "orderLink", orderLink);
    }

    /**
     * Send the order-cancelled message when the abandoned-order sweeper
     * cancels an order (or the customer self-cancels).
     *
     * @param toEmail       recipient
     * @param displayName   what to greet them by
     * @param invoiceNumber the order's invoice number
     */
    public CompletionStage<Void> sendOrderCancelled(String toEmail, String displayName,
                                                     String invoiceNumber) {
        var subject = "Your order " + invoiceNumber + " was cancelled";
        var storeLink = storefrontUrl + "/products";
        return sendPair(toEmail, subject, orderCancelledHtml, orderCancelledText,
                "displayName", displayName,
                "invoiceNumber", invoiceNumber,
                "storeLink", storeLink);
    }

    // -------------------------------------------------------------------------

    private CompletionStage<Void> sendPair(String to, String subject,
                                             Template htmlTpl, Template textTpl,
                                             Object... vars) {
        var html = fill(htmlTpl, vars);
        var text = fill(textTpl, vars);

        var mail = Mail.withHtml(to, subject, html.render())
                .setText(text.render())
                .setFrom(fromAddress);

        return mailer.send(mail)
                .invoke(() -> LOG.infof("Sent '%s' to %s", subject, to))
                .onFailure().invoke(err -> LOG.errorf(err, "Failed to send '%s' to %s", subject, to))
                .subscribeAsCompletionStage();
    }

    /** Convenience: build a TemplateInstance from alternating key/value pairs. */
    private static TemplateInstance fill(Template tpl, Object... vars) {
        var instance = tpl.instance();
        for (int i = 0; i + 1 < vars.length; i += 2) {
            instance = instance.data(String.valueOf(vars[i]), vars[i + 1]);
        }
        return instance;
    }

    /** Escape hatch for callers that want Uni ergonomics instead of CompletionStage. */
    @SuppressWarnings("unused")
    private Uni<Void> asUni(CompletionStage<Void> cs) {
        return Uni.createFrom().completionStage(cs);
    }
}
