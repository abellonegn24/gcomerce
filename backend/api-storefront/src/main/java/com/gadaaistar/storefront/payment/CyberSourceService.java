package com.gadaaistar.storefront.payment;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.persistence.order.CheckoutOrder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Builds the signed field set the browser POSTs to CyberSource Secure
 * Acceptance Hosted Checkout to redirect the customer into the hosted payment
 * page.
 *
 * <p>Reference: CyberSource Secure Acceptance Hosted Checkout Developer
 * Guide (Signature Version 1, algorithm HMAC-SHA256).
 *
 * <p>The signature is computed as the Base64(HMAC-SHA256(secretKey,
 * "field1=value1,field2=value2,...")) where the fields are the exact values
 * of the {@code signed_field_names} CSV in the same order.
 *
 * <p>This service is intentionally stateless — the order lifecycle and DB
 * writes live in {@link com.gadaaistar.storefront.order.OrderService}. All we
 * do here is package the right payload for the wire.
 */
@ApplicationScoped
public class CyberSourceService {

    private static final Logger LOG = Logger.getLogger(CyberSourceService.class);
    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(java.time.ZoneOffset.UTC);

    @Inject CyberSourceConfig config;

    /**
     * Result of {@link #buildRedirectPayload(CheckoutOrder)}. Callers hand
     * the whole struct back to the browser: {@link #endpoint} is where to POST,
     * {@link #fields} is what to POST.
     */
    public record RedirectPayload(String endpoint, LinkedHashMap<String, String> fields) {}

    /**
     * Build the fully-signed form the browser should POST to the CyberSource
     * hosted payment page for this order.
     *
     * @throws ConflictException when gateway credentials aren't configured
     *         (e.g. in dev environments without real keys)
     */
    public RedirectPayload buildRedirectPayload(CheckoutOrder order) {
        if (!config.isConfigured()) {
            throw new ConflictException("PAYMENT_GATEWAY_NOT_CONFIGURED",
                    "The payment gateway is not configured. Contact support.",
                    Map.of());
        }

        var unsigned = new LinkedHashMap<String, String>();
        unsigned.put("access_key",         config.accessKey());
        unsigned.put("profile_id",         config.profileId());
        unsigned.put("transaction_uuid",   UUID.randomUUID().toString());
        unsigned.put("signed_date_time",   ISO_UTC.format(Instant.now()));
        unsigned.put("locale",             "en");
        unsigned.put("transaction_type",   "sale");
        unsigned.put("reference_number",   order.invoiceNumber);
        unsigned.put("amount",             order.grandTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
        unsigned.put("currency",           order.currency);

        // Success / failure / cancel return URLs — CyberSource POSTs the
        // customer back here after the hosted page finishes.
        var returnBase = config.storefrontBaseUrl();
        unsigned.put("override_custom_receipt_page", returnBase + "/checkout/return?order=" + order.uuid);
        unsigned.put("override_custom_cancel_page",  returnBase + "/checkout/cancel?order=" + order.uuid);

        if (order.billToAddress != null) {
            var b = order.billToAddress;
            unsigned.put("bill_to_forename",       nz(b.fullName != null ? firstOf(b.fullName) : order.customer.firstName));
            unsigned.put("bill_to_surname",        nz(b.fullName != null ? lastOf(b.fullName)  : order.customer.lastName));
            unsigned.put("bill_to_email",          nz(order.customer.email));
            unsigned.put("bill_to_phone",          nz(b.phone));
            unsigned.put("bill_to_address_line1",  nz(b.streetLine1));
            if (b.streetLine2 != null && !b.streetLine2.isBlank()) {
                unsigned.put("bill_to_address_line2", b.streetLine2);
            }
            unsigned.put("bill_to_address_city",   nz(b.city));
            unsigned.put("bill_to_address_state",  nz(b.region));
            unsigned.put("bill_to_address_postal_code", nz(b.postalCode));
            unsigned.put("bill_to_address_country", nz(b.countryCode));
        }

        // Sign every key we added, plus signed_field_names / unsigned_field_names
        // as CyberSource requires — the two meta-fields are themselves signed.
        var signedFieldNames = String.join(",", unsigned.keySet())
                + ",signed_field_names,unsigned_field_names";
        unsigned.put("signed_field_names",   signedFieldNames);
        unsigned.put("unsigned_field_names", "");
        var signature = sign(unsigned, signedFieldNames);
        unsigned.put("signature", signature);

        LOG.infof("Built CyberSource redirect payload for order %s (invoice %s, total %s %s)",
                order.uuid, order.invoiceNumber, order.grandTotal, order.currency);
        return new RedirectPayload(config.endpoint(), unsigned);
    }

    // -------------------------------------------------------------------------
    // Signature verification (used by the webhook)
    // -------------------------------------------------------------------------

    /**
     * Verify a signature returned by CyberSource on a POST-back. Constant-time
     * comparison to defeat timing side-channels.
     */
    public boolean verifySignature(Map<String, String> params) {
        var signedFieldNames = params.get("signed_field_names");
        var providedSig      = params.get("signature");
        if (signedFieldNames == null || providedSig == null) return false;
        var expected = sign(params, signedFieldNames);
        // Constant-time equals — Java's String.equals leaks length on mismatch.
        var a = expected.getBytes(StandardCharsets.UTF_8);
        var b = providedSig.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= (a[i] ^ b[i]);
        return diff == 0;
    }

    // -------------------------------------------------------------------------
    // HMAC-SHA256 core
    // -------------------------------------------------------------------------

    private String sign(Map<String, String> params, String signedFieldNamesCsv) {
        var sb = new StringBuilder();
        var names = signedFieldNamesCsv.split(",");
        for (int i = 0; i < names.length; i++) {
            var name  = names[i];
            var value = params.getOrDefault(name, "");
            if (i > 0) sb.append(',');
            sb.append(name).append('=').append(value);
        }
        return hmacSha256Base64(config.secretKey(), sb.toString());
    }

    static String hmacSha256Base64(String key, String message) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable in this JVM", e);
        }
    }

    // -------------------------------------------------------------------------

    private static String nz(String s) { return s == null ? "" : s; }
    private static String firstOf(String full) {
        var parts = full.trim().split("\\s+", 2);
        return parts.length == 0 ? "" : parts[0];
    }
    private static String lastOf(String full) {
        var parts = full.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }
}
