package com.gadaaistar.admin.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Thin client for the MPGS REST API — refunds only.
 *
 * <p>MPGS refunds are issued by POSTing a {@code REFUND} transaction
 * against the original order + transaction. The endpoint is:
 * <pre>
 *   POST /api/rest/version/{ver}/merchant/{mid}/order/{orderId}/transaction/{txnId}
 *   { "apiOperation": "REFUND", "transaction": { "amount": ..., "currency": ..., "reference": "..." } }
 * </pre>
 *
 * <p>Auth is Basic HTTP with {@code merchant.<merchant-id>:<api-password>}.
 *
 * <p>Gracefully degrades when credentials aren't configured
 * ({@link MpgsRefundConfig#isConfigured()} returns false) — the refund
 * service falls back to recording every refund as {@code MANUAL}.
 */
@ApplicationScoped
public class MpgsRefundClient {

    private static final Logger LOG = Logger.getLogger(MpgsRefundClient.class);

    @Inject MpgsRefundConfig config;

    /**
     * Outcome of a refund call. On success the {@link #refundId} field is
     * the MPGS transaction id — we persist it as
     * {@code order_refund.gateway_reference}.
     */
    public record RefundOutcome(
            boolean success,
            String refundId,
            String errorCode,
            String errorMessage
    ) {}

    /**
     * Issue a refund against a prior MPGS capture.
     *
     * @param orderId    the MPGS order id (our invoiceNumber)
     * @param transactionId the MPGS transaction id (stored as paymentReference)
     * @param amount     refund amount
     * @param currency   order currency
     * @param reason     admin-supplied reason text
     */
    public RefundOutcome refund(String orderId, String transactionId,
                                BigDecimal amount, String currency, String reason) {
        if (!config.isConfigured()) {
            throw new IllegalStateException(
                    "MpgsRefundClient.refund called without configured credentials — "
                    + "the caller should check config.isConfigured() and take the MANUAL path.");
        }
        if (transactionId == null || transactionId.isBlank()) {
            return new RefundOutcome(false, null, "MISSING_TRANSACTION_ID",
                    "The order has no payment_reference to refund against.");
        }
        if (orderId == null || orderId.isBlank()) {
            return new RefundOutcome(false, null, "MISSING_ORDER_ID",
                    "The order has no MPGS order id (invoice number).");
        }

        var amt = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        var cur = (currency == null ? "USD" : currency).toUpperCase();
        var txnRef = "REFUND-" + UUID.randomUUID().toString().substring(0, 8);
        var body = String.format("""
                {
                  "apiOperation": "REFUND",
                  "transaction": {
                    "amount": %s,
                    "currency": "%s",
                    "reference": "%s"
                  }
                }""", amt, cur, txnRef);

        try {
            var http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(config.timeoutSeconds(), 10)))
                    .build();

            var req = HttpRequest.newBuilder(
                    URI.create(config.refundUrl(orderId, transactionId)))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Authorization", config.basicAuthHeader())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var payload = res.body() == null ? "" : res.body();
            var statusCode = res.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                // MPGS returns the transaction id in the response. We use the
                // same transaction id as the refund reference since MPGS
                // creates a new transaction under the same order.
                var refundTxnId = extractField(payload, "id");
                if (refundTxnId == null) {
                    refundTxnId = transactionId + "-REFUND";
                }
                LOG.infof("MPGS refund succeeded: refundTxnId=%s (order %s, txn %s, %s %s)",
                        refundTxnId, orderId, transactionId, amt, cur);
                return new RefundOutcome(true, refundTxnId, null, null);
            }

            // Failure path
            var errorCode = extractField(payload, "error.cause");
            if (errorCode == null) errorCode = "HTTP_" + statusCode;
            var errorMsg = extractField(payload, "error.explanation");
            if (errorMsg == null) errorMsg = extractField(payload, "result");
            if (errorMsg == null) errorMsg = "Refund rejected by gateway (HTTP " + statusCode + ").";
            LOG.warnf("MPGS refund failed (order %s, txn %s, HTTP %d, cause %s): %s",
                    orderId, transactionId, statusCode, errorCode, errorMsg);
            return new RefundOutcome(false, null, errorCode, errorMsg);

        } catch (java.net.http.HttpTimeoutException te) {
            LOG.warnf(te, "MPGS refund timed out (order %s, txn %s)", orderId, transactionId);
            return new RefundOutcome(false, null, "GATEWAY_TIMEOUT",
                    "The refund request timed out before the gateway responded. "
                    + "Verify with MPGS before retrying.");
        } catch (Exception e) {
            LOG.warnf(e, "MPGS refund threw (order %s, txn %s)", orderId, transactionId);
            return new RefundOutcome(false, null, "GATEWAY_ERROR",
                    "Unable to reach the payment gateway: " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : " — " + e.getMessage()));
        }
    }

    /**
     * Minimal JSON field extractor — avoids pulling in a JSON parser for
     * one or two fields. Body sizes are tiny.
     */
    private static String extractField(String json, String fieldPath) {
        if (json == null || json.isEmpty()) return null;
        // MPGS uses dot-notation in error fields like "error.explanation".
        // In the JSON they're nested: { "error": { "explanation": "..." } }
        // We try the full path as a literal key first, then nested.
        var parts = fieldPath.split("\\.");
        if (parts.length == 1) {
            return simpleExtract(json, parts[0]);
        }
        // For nested paths, search for the deepest key.
        return simpleExtract(json, parts[parts.length - 1]);
    }

    private static String simpleExtract(String json, String key) {
        var pattern = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        var m = pattern.matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
