package com.gadaaistar.storefront.payment;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Typed wrapper around the {@code gadaa.yagoutpay.*} config keys — kept in
 * one place so tests can point at a stub set of values and prod code can
 * inject a single dependency instead of {@code @ConfigProperty} fields.
 *
 * <p>YagoutPay's "Aggregator Hosted (Non-Seamless)" flow authenticates the
 * redirect payload with AES-256-CBC using a shared merchant encryption key —
 * there is no HMAC and no separate webhook secret. The same key decrypts the
 * encrypted {@code txn_response} the gateway POSTs back to the success /
 * failure return URLs.
 */
@ApplicationScoped
public class YagoutPayConfig {

    @ConfigProperty(name = "gadaa.yagoutpay.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "gadaa.yagoutpay.merchant-id", defaultValue = "")
    String merchantId;

    @ConfigProperty(name = "gadaa.yagoutpay.encryption-key", defaultValue = "")
    String encryptionKey;

    @ConfigProperty(name = "gadaa.yagoutpay.aggregator-id", defaultValue = "yagout")
    String aggregatorId;

    @ConfigProperty(name = "gadaa.yagoutpay.gateway-url",
                    defaultValue = "https://uatcheckout.yagoutpay.com/ms-transaction-core-1-0/paymentRedirection/checksumGatewayPage")
    String gatewayUrl;

    /**
     * Public base URL of this API, used to build the success_url / failure_url
     * embedded in the encrypted payload. Must be a domain registered with
     * YagoutPay — requests from unregistered domains are rejected with
     * "Invalid Referral URL".
     */
    @ConfigProperty(name = "gadaa.yagoutpay.callback-base-url",
                    defaultValue = "http://localhost:8080")
    String callbackBaseUrl;

    /**
     * Country code sent in the txn_details section. Stays "ETH" — this is the
     * merchant/processing country, not the customer's.
     */
    @ConfigProperty(name = "gadaa.yagoutpay.country", defaultValue = "ETH")
    String country;

    /**
     * Optional comma-separated allowlist of ISO-4217 currencies YagoutPay may
     * process for us (e.g. {@code "ETB,USD"}). Empty = all currencies — the
     * default while YagoutPay's international support rolls out.
     */
    @ConfigProperty(name = "gadaa.yagoutpay.supported-currencies", defaultValue = "")
    String supportedCurrencies;

    @ConfigProperty(name = "gadaa.frontend.storefront-url",
                    defaultValue = "https://gedaaistar.et")
    String storefrontBaseUrl;

    public boolean enabled()             { return enabled; }
    public String merchantId()           { return merchantId; }
    public String encryptionKey()        { return encryptionKey; }
    public String aggregatorId()         { return aggregatorId; }
    public String gatewayUrl()           { return gatewayUrl; }
    public String callbackBaseUrl()      { return callbackBaseUrl; }
    public String country()              { return country; }
    public String supportedCurrencies()  { return supportedCurrencies; }
    public String storefrontBaseUrl()    { return storefrontBaseUrl; }

    /** True when the platform has real gateway credentials wired up. */
    public boolean isConfigured() {
        return enabled
                && !merchantId.isBlank()
                && !encryptionKey.isBlank()
                && !gatewayUrl.isBlank()
                && !callbackBaseUrl.isBlank();
    }

    /**
     * True when this gateway may process an order in the given currency.
     * An empty allowlist means every currency is accepted (multi-currency
     * rollout); a non-empty list restricts to the listed ISO-4217 codes.
     */
    public boolean supportsCurrency(String currency) {
        if (supportedCurrencies.isBlank()) return true;
        if (currency == null) return false;
        Set<String> allowed = Arrays.stream(supportedCurrencies.split(","))
                .map(String::trim).map(String::toUpperCase)
                .collect(Collectors.toSet());
        return allowed.contains(currency.toUpperCase());
    }

    /** Endpoint the gateway POSTs the (encrypted) success response to. */
    public String successUrl() {
        return callbackBaseUrl + "/api/storefront/v1/payment/yagoutpay/return";
    }

    /** Endpoint the gateway POSTs the (encrypted) failure response to. */
    public String failureUrl() {
        return callbackBaseUrl + "/api/storefront/v1/payment/yagoutpay/failure";
    }
}
