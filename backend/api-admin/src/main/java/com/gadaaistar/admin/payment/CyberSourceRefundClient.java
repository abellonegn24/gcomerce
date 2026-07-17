package com.gadaaistar.admin.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Thin client for the CyberSource REST API — refunds only.
 *
 * <p>The reason this exists separately from the storefront-side
 * {@code CyberSourceService} is that the two APIs use completely
 * different auth: Secure Acceptance signs form fields with HMAC-SHA256,
 * REST signs a per-request set of HTTP headers using the CyberSource
 * "HTTP Signature v1" scheme.
 *
 * <p>Signature scheme (per CyberSource docs):
 * <pre>
 *   Signature: keyid="{keyId}",
 *              algorithm="HmacSHA256",
 *              headers="host date (request-target) digest v-c-merchant-id",
 *              signature="{Base64 HMAC-SHA256 of the signing string}"
 * </pre>
 * The signing string is the newline-joined header-name/value pairs in
 * the exact order declared in {@code headers}. The {@code digest} header
 * is {@code "SHA-256=" + Base64(SHA-256(body))}. GET requests still send
 * a digest of the empty body.
 *
 * <p>Gracefully degrades when credentials aren't configured
 * ({@link CyberSourceRefundConfig#isConfigured()} returns false) — the
 * refund service falls back to recording every refund as {@code MANUAL}.
 * This keeps dev environments (and prod-emergency ops) usable without
 * requiring a live sandbox account.
 */
@ApplicationScoped
public class CyberSourceRefundClient {

    private static final Logger LOG = Logger.getLogger(CyberSourceRefundClient.class);

    /** RFC 1123 with GMT zone, as CyberSource requires. */
    private static final DateTimeFormatter RFC_1123 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

    // The refund id in the JSON response is at $.id — extract with a narrow
    // regex to avoid pulling in a full JSON parser for one field. Body sizes
    // are tiny (<2 KB) so the regex is fine.
    private static final Pattern ID_FIELD  = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern STATUS    = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REASON    = Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MESSAGE   = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");

    @Inject CyberSourceRefundConfig config;

    /**
     * Outcome of a refund call. On success the {@link #refundId} field is
     * the CyberSource-side identifier — we persist it as
     * {@code order_refund.gateway_reference}. On failure the two error
     * fields carry whatever the gateway said.
     */
    public record RefundOutcome(
            boolean success,
            String refundId,
            String errorCode,
            String errorMessage
    ) {}

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Issue a refund against a prior capture. {@code captureId} is what we
     * previously stored on {@code checkout_order.payment_reference}
     * (CyberSource echoes the auth/capture id as {@code transaction_id} on
     * the webhook return).
     *
     * <p>Returns a {@link RefundOutcome} with {@code success=false} when the
     * gateway rejects the refund. Only throws {@link IllegalStateException}
     * for programmer errors (mis-called with an unconfigured client). Any
     * network / HTTP / IO issue is captured as a failure outcome so the
     * caller can persist it as {@code order_refund.status=FAILED} rather
     * than propagate an exception.
     */
    public RefundOutcome refund(String captureId, BigDecimal amount, String currency, String reason) {
        if (!config.isConfigured()) {
            throw new IllegalStateException(
                    "CyberSourceRefundClient.refund called without configured credentials — "
                    + "the caller should check config.isConfigured() and take the MANUAL path.");
        }
        if (captureId == null || captureId.isBlank()) {
            return new RefundOutcome(false, null, "MISSING_CAPTURE_ID",
                    "The order has no payment_reference to refund against.");
        }

        var path = "/pts/v2/payments/" + captureId + "/refunds";
        var body = buildBody(amount, currency, reason);

        try {
            var digest       = "SHA-256=" + base64(sha256(body.getBytes(StandardCharsets.UTF_8)));
            var date         = RFC_1123.format(ZonedDateTime.now(java.time.ZoneOffset.UTC));
            var target       = "post " + path;
            var signingString = String.join("\n",
                    "host: "              + config.host(),
                    "date: "              + date,
                    "(request-target): "  + target,
                    "digest: "            + digest,
                    "v-c-merchant-id: "   + config.merchantId());
            var signature    = base64(hmacSha256(decodeSecret(config.secretKey()),
                                                  signingString.getBytes(StandardCharsets.UTF_8)));

            var http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(config.timeoutSeconds(), 10)))
                    .build();

            var req = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("host",             config.host())
                    .header("date",             date)
                    .header("digest",           digest)
                    .header("v-c-merchant-id",  config.merchantId())
                    .header("Content-Type",     "application/json;charset=utf-8")
                    .header("Accept",           "application/hal+json;charset=utf-8")
                    .header("Signature", authHeader(config.keyId(), signature))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            var payload = res.body() == null ? "" : res.body();
            var statusCode = res.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                var refundId = firstMatch(ID_FIELD, payload);
                if (refundId == null) {
                    LOG.warnf("CyberSource refund returned %d but no id field: %s", statusCode, payload);
                    return new RefundOutcome(false, null, "MALFORMED_RESPONSE",
                            "Gateway returned success but the response body had no id.");
                }
                LOG.infof("CyberSource refund succeeded: refundId=%s (capture %s, %s %s)",
                        refundId, captureId, amount.setScale(2, RoundingMode.HALF_UP), currency);
                return new RefundOutcome(true, refundId, null, null);
            }

            // Failure path — surface whatever we can pull out of the JSON.
            var reasonCode = firstNonNull(firstMatch(REASON, payload),
                                          "HTTP_" + statusCode);
            var message    = firstNonNull(firstMatch(MESSAGE, payload),
                                          firstMatch(STATUS, payload),
                                          "Refund rejected by gateway (HTTP " + statusCode + ").");
            LOG.warnf("CyberSource refund failed (capture %s, HTTP %d, reason %s): %s",
                    captureId, statusCode, reasonCode, message);
            return new RefundOutcome(false, null, reasonCode, message);
        }
        catch (java.net.http.HttpTimeoutException te) {
            LOG.warnf(te, "CyberSource refund timed out (capture %s)", captureId);
            return new RefundOutcome(false, null, "GATEWAY_TIMEOUT",
                    "The refund request timed out before the gateway responded. "
                    + "Verify with CyberSource before retrying.");
        }
        catch (Exception e) {
            LOG.warnf(e, "CyberSource refund threw (capture %s)", captureId);
            return new RefundOutcome(false, null, "GATEWAY_ERROR",
                    "Unable to reach the payment gateway: " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : " — " + e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Body + signature helpers
    // -------------------------------------------------------------------------

    /**
     * CyberSource refund payload — the minimum they accept. We do not
     * include {@code orderInformation.lineItems} because the API happily
     * refunds totalAmount alone and we don't want to force a per-line
     * refund experience on the admin UI.
     */
    private static String buildBody(BigDecimal amount, String currency, String reason) {
        var amt = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        var cur = (currency == null ? "USD" : currency).toUpperCase();
        var safeReason = jsonEscape(truncate(reason, 240));
        // The clientReferenceInformation.code shows up in CyberSource reports;
        // making it unique per attempt aids reconciliation.
        var clientRef = jsonEscape("REFUND-" + UUID.randomUUID());
        return "{"
                + "\"clientReferenceInformation\":{\"code\":\"" + clientRef + "\"},"
                + "\"orderInformation\":{"
                +   "\"amountDetails\":{"
                +     "\"totalAmount\":\"" + amt + "\","
                +     "\"currency\":\""    + cur + "\""
                +   "}"
                + "}"
                + (safeReason.isEmpty() ? ""
                                        : ",\"processingInformation\":{\"reconciliationId\":\"" + safeReason + "\"}")
                + "}";
    }

    private static String authHeader(String keyId, String signature) {
        return "keyid=\"" + keyId + "\", "
             + "algorithm=\"HmacSHA256\", "
             + "headers=\"host date (request-target) digest v-c-merchant-id\", "
             + "signature=\"" + signature + "\"";
    }

    // -------------------------------------------------------------------------
    // Crypto primitives
    // -------------------------------------------------------------------------

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable in this JVM", e);
        }
    }

    private static String base64(byte[] raw) {
        return Base64.getEncoder().encodeToString(raw);
    }

    /**
     * CyberSource issues the REST secret key as a Base64-encoded blob; the
     * raw bytes are what feeds the HMAC. If someone accidentally pastes
     * the raw text (no padding, non-standard chars) we fall back to the
     * UTF-8 bytes so the auth still runs — the signature will simply fail
     * verification and CyberSource will 401, which is far more actionable
     * than a NullPointerException at startup.
     */
    private static byte[] decodeSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException notBase64) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    // -------------------------------------------------------------------------
    // Little utilities
    // -------------------------------------------------------------------------

    private static String firstMatch(Pattern p, String haystack) {
        if (haystack == null || haystack.isEmpty()) return null;
        Matcher m = p.matcher(haystack);
        return m.find() ? m.group(1) : null;
    }

    private static String firstNonNull(String... choices) {
        for (var c : choices) if (c != null && !c.isBlank()) return c;
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Minimal JSON string escaper — enough for reason text + client ref. */
    private static String jsonEscape(String s) {
        if (s == null || s.isEmpty()) return "";
        var sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else          sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
