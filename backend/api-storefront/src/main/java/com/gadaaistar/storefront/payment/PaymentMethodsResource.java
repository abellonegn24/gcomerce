package com.gadaaistar.storefront.payment;

import java.util.List;

import com.gadaaistar.persistence.cart.CartRepository;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Returns the list of payment methods available to the customer at checkout,
 * based on which gateways are configured. The frontend uses this to render
 * the payment method picker.
 *
 * <p>If neither gateway is configured (dev/staging without credentials),
 * an empty list is returned and the checkout falls back to the
 * "gateway missing" path.
 */
@Path("/api/storefront/v1/me/payment-methods")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Payment methods", description = "Available payment options at checkout.")
public class PaymentMethodsResource {

    @Inject CyberSourceConfig    cybersourceConfig;
    @Inject MpgsConfig           mpgsConfig;
    @Inject YagoutPayConfig      yagoutPayConfig;
    @Inject SecurityContextHolder security;
    @Inject CartRepository       carts;

    @GET
    @Operation(summary = "List the payment methods available at checkout based on gateway configuration.")
    public List<PaymentMethodOption> list() {
        var methods = new java.util.ArrayList<PaymentMethodOption>();

        if (cybersourceConfig.isConfigured()) {
            methods.add(new PaymentMethodOption(
                    "CYBERSOURCE", "CyberSource",
                    "Pay with Visa, Mastercard, Amex via CyberSource",
                    null));
        }

        if (mpgsConfig.isConfigured()) {
            methods.add(new PaymentMethodOption(
                    "MPGS", "Mastercard",
                    "Pay with Mastercard via MPGS",
                    null));
        }

        if (yagoutPayConfig.isConfigured() && yagoutPayEligibleForCart()) {
            methods.add(new PaymentMethodOption(
                    "YAGOUTPAY", "YagoutPay",
                    "Pay with telebirr, CBE Birr, M-Pesa or cards via YagoutPay",
                    null));
        }

        return methods;
    }

    /**
     * When {@code gadaa.yagoutpay.supported-currencies} is an allowlist,
     * YagoutPay is only offered if the caller's cart is in an allowed
     * currency. An empty allowlist (the default while international support
     * rolls out) means every currency is eligible.
     */
    private boolean yagoutPayEligibleForCart() {
        if (yagoutPayConfig.supportedCurrencies().isBlank()) return true;
        return security.currentPrincipalId()
                .flatMap(carts::findByCustomer)
                .map(cart -> yagoutPayConfig.supportsCurrency(cart.currency))
                .orElse(false);
    }

    public record PaymentMethodOption(
            String id,
            String name,
            String description,
            String logoUrl
    ) {}
}
