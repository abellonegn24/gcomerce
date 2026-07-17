package com.gadaaistar.admin.refunds;

import java.math.RoundingMode;
import java.util.concurrent.CompletionStage;

import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.OrderRefund;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Sends the refund-receipt email to the customer when a refund succeeds.
 * Lives in the admin API because refunds are admin-initiated.
 */
@ApplicationScoped
public class RefundMailService {

    private static final Logger LOG = Logger.getLogger(RefundMailService.class);

    @Inject ReactiveMailer mailer;

    @Location("mail/refund-receipt.html") Template refundReceiptHtml;
    @Location("mail/refund-receipt.txt")  Template refundReceiptText;

    @ConfigProperty(name = "quarkus.mailer.from") String fromAddress;
    @ConfigProperty(name = "gadaa.frontend.storefront-url") String storefrontUrl;

    public CompletionStage<Void> sendRefundReceipt(CheckoutOrder order, OrderRefund refund) {
        var c = order.customer;
        var displayName = c.firstName != null && !c.firstName.isBlank()
                ? c.firstName
                : (c.companyName != null ? c.companyName : "there");
        var amountStr = refund.amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        var orderLink = storefrontUrl + "/account/orders/" + order.uuid;
        var subject = "Refund receipt for order " + order.invoiceNumber;

        var html = fill(refundReceiptHtml,
                "displayName", displayName,
                "invoiceNumber", order.invoiceNumber,
                "currency", order.currency,
                "refundAmount", amountStr,
                "reason", refund.reason,
                "orderLink", orderLink);
        var text = fill(refundReceiptText,
                "displayName", displayName,
                "invoiceNumber", order.invoiceNumber,
                "currency", order.currency,
                "refundAmount", amountStr,
                "reason", refund.reason,
                "orderLink", orderLink);

        var mail = Mail.withHtml(c.email, subject, html.render())
                .setText(text.render())
                .setFrom(fromAddress);

        return mailer.send(mail)
                .invoke(() -> LOG.infof("Sent refund receipt to %s for order %s", c.email, order.uuid))
                .onFailure().invoke(err -> LOG.errorf(err, "Failed to send refund receipt to %s", c.email))
                .subscribeAsCompletionStage();
    }

    private static TemplateInstance fill(Template tpl, Object... vars) {
        var instance = tpl.instance();
        for (int i = 0; i + 1 < vars.length; i += 2) {
            instance = instance.data(String.valueOf(vars[i]), vars[i + 1]);
        }
        return instance;
    }
}
