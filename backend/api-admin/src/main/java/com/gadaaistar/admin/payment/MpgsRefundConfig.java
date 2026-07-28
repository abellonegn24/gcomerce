package com.gadaaistar.admin.payment;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Typed wrapper around the {@code gadaa.mpgs.*} config keys for the admin
 * API — used by {@link MpgsRefundClient} to issue refunds via the MPGS
 * REST API.
 *
 * <p>MPGS uses Basic HTTP auth with {@code merchant.<merchant-id>} as the
 * username and an API password from the Merchant Administration portal.
 * The same credentials work for both the storefront (session creation)
 * and the admin (refunds) — they are the same MPGS merchant account.
 *
 * <p>When {@link #isConfigured()} is false the refund service falls back
 * to recording every refund as {@code MANUAL} — the audit row is still
 * persisted.
 */
@ApplicationScoped
public class MpgsRefundConfig {

    @ConfigProperty(name = "gadaa.mpgs.gateway-url",
                    defaultValue = "https://mtf.gateway.mastercard.com")
    String gatewayUrl;

    @ConfigProperty(name = "gadaa.mpgs.merchant-id", defaultValue = "")
    String merchantId;

    @ConfigProperty(name = "gadaa.mpgs.api-password", defaultValue = "")
    String apiPassword;

    @ConfigProperty(name = "gadaa.mpgs.api-version", defaultValue = "100")
    String apiVersion;

    @ConfigProperty(name = "gadaa.mpgs.timeout-seconds", defaultValue = "30")
    int timeoutSeconds;

    public String gatewayUrl()     { return gatewayUrl; }
    public String merchantId()     { return merchantId; }
    public String apiPassword()    { return apiPassword; }
    public String apiVersion()     { return apiVersion; }
    public int    timeoutSeconds() { return timeoutSeconds; }

    /** Basic-Auth header value: {@code Basic base64("merchant.<id>:<password>")}. */
    public String basicAuthHeader() {
        var raw = "merchant." + merchantId + ":" + apiPassword;
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Full URL for the refund transaction endpoint. */
    public String refundUrl(String orderId, String transactionId) {
        return gatewayUrl + "/api/rest/version/" + apiVersion
                + "/merchant/" + merchantId + "/order/" + orderId
                + "/transaction/" + transactionId;
    }

    /** True when every credential is present. */
    public boolean isConfigured() {
        return !merchantId.isBlank()
                && !apiPassword.isBlank()
                && !gatewayUrl.isBlank();
    }
}
