package com.gadaaistar.admin.payment;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Typed wrapper around the CyberSource REST API config keys. Kept separate
 * from the storefront-side {@code CyberSourceConfig} because the two APIs
 * are authenticated in wildly different ways:
 *
 * <ul>
 *   <li>Secure Acceptance (hosted checkout): access-key + profile-id +
 *       secret-key, HMAC-SHA256 signature embedded in the form fields.</li>
 *   <li>REST API (refunds, captures, void): merchant-id + REST key-id +
 *       REST shared-secret-key, HTTP Signature v1 header per request.</li>
 * </ul>
 *
 * <p>These are two different sets of credentials on the CyberSource side —
 * mixing them together in one config class would suggest to the ops team
 * that "just paste your Secure Acceptance secret" is enough, which it
 * isn't. Splitting also lets the storefront module deploy without ever
 * needing the REST credentials.
 *
 * <p>When {@link #isConfigured()} is false the refund service falls back
 * to recording every refund as {@code MANUAL} (the admin issued the refund
 * out-of-band, e.g. by phoning CyberSource ops or handing cash back at
 * the warehouse) — the audit row is still persisted.
 */
@ApplicationScoped
public class CyberSourceRefundConfig {

    @ConfigProperty(name = "gadaa.cybersource.rest.host",
                    defaultValue = "apitest.cybersource.com")
    String host;

    @ConfigProperty(name = "gadaa.cybersource.rest.merchant-id", defaultValue = "")
    String merchantId;

    @ConfigProperty(name = "gadaa.cybersource.rest.key-id", defaultValue = "")
    String keyId;

    @ConfigProperty(name = "gadaa.cybersource.rest.secret-key", defaultValue = "")
    String secretKey;

    /**
     * How long to wait (seconds) for a REST API response. Refunds are a
     * synchronous call — the admin clicks and blocks on the modal until
     * we hear back. Beyond ~15s the customer-support UX suffers, but a
     * flaky connection to CyberSource sandbox has been observed to take
     * 20s+. Default is generous; ops can tune.
     */
    @ConfigProperty(name = "gadaa.cybersource.rest.timeout-seconds", defaultValue = "30")
    int timeoutSeconds;

    public String host()          { return host; }
    public String merchantId()    { return merchantId; }
    public String keyId()         { return keyId; }
    public String secretKey()     { return secretKey; }
    public int    timeoutSeconds(){ return timeoutSeconds; }

    /** Base URL for API calls, e.g. {@code https://apitest.cybersource.com}. */
    public String baseUrl() {
        return "https://" + host;
    }

    /** True when every credential is present. Used by the client to decide manual vs. gateway. */
    public boolean isConfigured() {
        return !merchantId.isBlank()
                && !keyId.isBlank()
                && !secretKey.isBlank();
    }
}
