package com.gadaaistar.storefront.payment;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Typed wrapper around the {@code gadaa.mpgs.*} config keys — kept in
 * one place so tests can point at a stub set of values and prod code can inject
 * a single dependency instead of seven {@code @ConfigProperty} fields.
 *
 * <p>MPGS (Mastercard Payment Gateway Services) uses Basic HTTP auth with
 * {@code merchant.<merchant-id>} as the username and an API password from
 * the Merchant Administration portal. The gateway URL varies by provider
 * and region — it is a config key, never hardcoded.
 */
@ApplicationScoped
public class MpgsConfig {

    @ConfigProperty(name = "gadaa.mpgs.gateway-url",
                    defaultValue = "https://test-gateway.mastercard.com")
    String gatewayUrl;

    @ConfigProperty(name = "gadaa.mpgs.merchant-id", defaultValue = "")
    String merchantId;

    @ConfigProperty(name = "gadaa.mpgs.api-password", defaultValue = "")
    String apiPassword;

    @ConfigProperty(name = "gadaa.mpgs.api-version", defaultValue = "62")
    String apiVersion;

    @ConfigProperty(name = "gadaa.mpgs.checkout-version", defaultValue = "62")
    String checkoutVersion;

    @ConfigProperty(name = "gadaa.mpgs.notification-secret", defaultValue = "")
    String notificationSecret;

    @ConfigProperty(name = "gadaa.mpgs.enabled", defaultValue = "false")
    boolean enabled;

    // Merchant display info shown on the hosted checkout page
    @ConfigProperty(name = "gadaa.mpgs.merchant-name", defaultValue = "Gadaa Istar")
    String merchantName;

    @ConfigProperty(name = "gadaa.mpgs.merchant-phone", defaultValue = "")
    String merchantPhone;

    @ConfigProperty(name = "gadaa.mpgs.merchant-email", defaultValue = "")
    String merchantEmail;

    @ConfigProperty(name = "gadaa.frontend.storefront-url",
                    defaultValue = "http://localhost:4200")
    String storefrontBaseUrl;

    public String gatewayUrl()         { return gatewayUrl; }
    public String merchantId()         { return merchantId; }
    public String apiPassword()        { return apiPassword; }
    public String apiVersion()         { return apiVersion; }
    public String checkoutVersion()    { return checkoutVersion; }
    public String notificationSecret() { return notificationSecret; }
    public boolean enabled()           { return enabled; }
    public String storefrontBaseUrl()  { return storefrontBaseUrl; }
    public String merchantName()       { return merchantName; }
    public String merchantPhone()      { return merchantPhone; }
    public String merchantEmail()      { return merchantEmail; }

    /** True when MPGS credentials are wired up and the feature flag is on. */
    public boolean isConfigured() {
        return enabled
                && !merchantId.isBlank()
                && !apiPassword.isBlank()
                && !gatewayUrl.isBlank();
    }

    /** Basic-Auth header value: {@code Basic base64("merchant.<id>:<password>")}. */
    public String basicAuthHeader() {
        var raw = "merchant." + merchantId + ":" + apiPassword;
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Full URL for the CREATE_CHECKOUT_SESSION REST endpoint. */
    public String sessionUrl() {
        return gatewayUrl + "/api/rest/version/" + apiVersion
                + "/merchant/" + merchantId + "/session";
    }

    /** Full URL for the Retrieve Order REST endpoint. */
    public String orderUrl(String orderId) {
        return gatewayUrl + "/api/rest/version/" + apiVersion
                + "/merchant/" + merchantId + "/order/" + orderId;
    }

    /** Full URL for the Checkout.js script tag. */
    public String checkoutJsUrl() {
        return gatewayUrl + "/checkout/version/" + checkoutVersion + "/checkout.js";
    }
}
