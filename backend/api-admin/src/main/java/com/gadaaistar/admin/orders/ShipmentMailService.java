package com.gadaaistar.admin.orders;

import java.util.concurrent.CompletionStage;

import com.gadaaistar.persistence.order.CheckoutOrder;

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
 * Sends the shipment-notification email to the customer when an admin
 * marks an order as SHIPPED. Lives in the admin API because shipping
 * is an admin-initiated action.
 */
@ApplicationScoped
public class ShipmentMailService {

    private static final Logger LOG = Logger.getLogger(ShipmentMailService.class);

    @Inject ReactiveMailer mailer;

    @Location("mail/shipment-notification.html") Template shipmentHtml;
    @Location("mail/shipment-notification.txt")  Template shipmentText;

    @ConfigProperty(name = "quarkus.mailer.from") String fromAddress;
    @ConfigProperty(name = "gadaa.frontend.storefront-url") String storefrontUrl;

    /**
     * @param order           the order that was shipped
     * @param carrier         carrier name (e.g. "DHL Express")
     * @param trackingNumber  tracking number, or null if not provided
     */
    public CompletionStage<Void> sendShipmentNotification(CheckoutOrder order,
                                                           String carrier,
                                                           String trackingNumber) {
        var c = order.customer;
        var displayName = c.firstName != null && !c.firstName.isBlank()
                ? c.firstName
                : (c.companyName != null ? c.companyName : "there");
        var orderLink = storefrontUrl + "/account/orders/" + order.uuid;
        var subject = "Your order " + order.invoiceNumber + " has shipped";

        var html = fill(shipmentHtml,
                "displayName", displayName,
                "invoiceNumber", order.invoiceNumber,
                "carrier", carrier != null ? carrier : "the carrier",
                "trackingNumber", trackingNumber,
                "orderLink", orderLink);
        var text = fill(shipmentText,
                "displayName", displayName,
                "invoiceNumber", order.invoiceNumber,
                "carrier", carrier != null ? carrier : "the carrier",
                "trackingNumber", trackingNumber,
                "orderLink", orderLink);

        var mail = Mail.withHtml(c.email, subject, html.render())
                .setText(text.render())
                .setFrom(fromAddress);

        return mailer.send(mail)
                .invoke(() -> LOG.infof("Sent shipment notification to %s for order %s", c.email, order.uuid))
                .onFailure().invoke(err -> LOG.errorf(err, "Failed to send shipment notification to %s", c.email))
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
