package com.gadaaistar.storefront.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.persistence.order.CheckoutOrder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Server-side service for the MPGS (Mastercard Payment Gateway Services)
 * Hosted Checkout integration.
 *
 * <p>This service is intentionally stateless — the order lifecycle and DB
 * writes live in {@link com.gadaaistar.storefront.order.OrderService}. All we
 * do here is:
 * <ol>
 *   <li>Create a checkout session via the MPGS REST API (Step 1 of the
 *       hosted checkout flow).</li>
 *   <li>Retrieve the order result from MPGS after payment (Step 3).</li>
 *   <li>Verify webhook notification secrets (constant-time comparison).</li>
 * </ol>
 *
 * <p>Reference: MPGS Hosted Checkout Integration Guide —
 * {@link https://github.com/Mastercard-MEA/MPGS-Integration-Guides-and-Samples/blob/main/docs/hosted-checkout.md}
 */
@ApplicationScoped
public class MpgsService {

    private static final Logger LOG = Logger.getLogger(MpgsService.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject MpgsConfig config;

    /**
     * Result of {@link #createCheckoutSession(CheckoutOrder)}. The frontend
     * uses {@link #checkoutJsUrl()} to load the Checkout.js library and
     * {@link #fields()} to configure it. The {@link #successIndicator} is
     * stored on the order so the webhook handler can verify the
     * {@code resultIndicator} match without relying on the browser.
     */
    public record MpgsSession(String sessionId, String successIndicator,
                              String checkoutJsUrl, String merchantId,
                              LinkedHashMap<String, String> fields) {}

    /**
     * Result of {@link #retrieveOrder(String)}. We only extract the fields
     * we need to confirm payment — the full MPGS response is much larger.
     */
    public record MpgsOrderResult(String status, String result,
                                  BigDecimal totalCapturedAmount,
                                  String transactionId) {}

    /**
     * Call the MPGS CREATE_CHECKOUT_SESSION endpoint to start a hosted
     * checkout session for this order.
     *
     * @throws ConflictException when MPGS is not configured or the API
     *         returns FAILURE
     */
    public MpgsSession createCheckoutSession(CheckoutOrder order) {
        if (!config.isConfigured()) {
            throw new ConflictException("PAYMENT_GATEWAY_NOT_CONFIGURED",
                    "MPGS is not configured. Contact support.",
                    Map.of());
        }

        var amount = order.grandTotal.setScale(2, RoundingMode.HALF_UP).toPlainString();
        var returnUrl = config.storefrontBaseUrl()
                + "/checkout/return?order=" + order.uuid + "&provider=MPGS";
        var cancelUrl = config.storefrontBaseUrl()
                + "/checkout/cancel?order=" + order.uuid + "&provider=MPGS";

        // Build the merchant object — only include phone/email when configured.
        var merchantJson = new StringBuilder();
        merchantJson.append("\"name\": \"").append(escapeJson(config.merchantName())).append("\"");
        if (!config.merchantPhone().isBlank()) {
            merchantJson.append(", \"phone\": \"").append(escapeJson(config.merchantPhone())).append("\"");
        }
        if (!config.merchantEmail().isBlank()) {
            merchantJson.append(", \"email\": \"").append(escapeJson(config.merchantEmail())).append("\"");
        }

        var body = String.format("""
                {
                  "apiOperation": "CREATE_CHECKOUT_SESSION",
                  "interaction": {
                    "operation": "PURCHASE",
                    "returnUrl": "%s",
                    "cancelUrl": "%s",
                    "merchant": {
                      %s
                    },
                    "displayControl": {
                      "customerEmail": "READ_ONLY",
                      "billingAddress": "HIDE",
                      "shipping": "HIDE"
                    }
                  },
                  "order": {
                    "amount": %s,
                    "currency": "%s",
                    "id": "%s",
                    "reference": "%s",
                    "description": "Gadaa Istar order %s"
                  },
                  "transaction": {
                    "reference": "%s-TXN-1"
                  }
                }""", returnUrl, cancelUrl, merchantJson.toString(),
                amount, order.currency,
                order.invoiceNumber, order.uuid, order.invoiceNumber,
                order.invoiceNumber);

        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(config.sessionUrl()))
                    .header("Authorization", config.basicAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var json = MAPPER.readTree(response.body());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                var result = str(json, "result");
                var explanation = str(json, "error.explanation");
                LOG.errorf("MPGS session creation failed for order %s: HTTP %d, result=%s, explanation=%s",
                        order.uuid, response.statusCode(), result, explanation);
                throw new ConflictException("MPGS_SESSION_FAILED",
                        "MPGS session creation failed: " + nz(result) + " — " + nz(explanation),
                        Map.of("httpStatus", response.statusCode()));
            }

            var result = str(json, "result");
            if (!"SUCCESS".equalsIgnoreCase(result)) {
                var explanation = str(json, "error.explanation");
                LOG.errorf("MPGS session creation returned non-SUCCESS for order %s: result=%s, explanation=%s",
                        order.uuid, result, explanation);
                throw new ConflictException("MPGS_SESSION_FAILED",
                        "MPGS session creation returned " + nz(result) + ": " + nz(explanation),
                        Map.of());
            }

            var sessionId = str(json, "session.id");
            var successIndicator = str(json, "successIndicator");

            if (sessionId == null || sessionId.isBlank()) {
                LOG.errorf("MPGS session creation returned SUCCESS but no session.id for order %s", order.uuid);
                throw new ConflictException("MPGS_SESSION_FAILED",
                        "MPGS returned SUCCESS but no session id.",
                        Map.of());
            }

            LOG.infof("Created MPGS checkout session for order %s (invoice %s, session %s)",
                    order.uuid, order.invoiceNumber, sessionId);

            var fields = new LinkedHashMap<String, String>();
            fields.put("merchantId", config.merchantId());
            fields.put("sessionId", sessionId);
            fields.put("successIndicator", nz(successIndicator));
            fields.put("merchantName", config.merchantName());
            fields.put("orderAmount", amount);
            fields.put("orderCurrency", order.currency);
            fields.put("orderId", order.invoiceNumber);

            return new MpgsSession(sessionId, nz(successIndicator),
                    config.checkoutJsUrl(), config.merchantId(), fields);

        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "MPGS session creation error for order %s", order.uuid);
            throw new ConflictException("MPGS_SESSION_FAILED",
                    "Could not contact MPGS gateway. Please try again.",
                    Map.of());
        }
    }

    /**
     * Retrieve the order status from MPGS after payment. Used as a
     * fallback when the webhook hasn't arrived yet.
     */
    public MpgsOrderResult retrieveOrder(String mpgsOrderId) {
        if (!config.isConfigured()) {
            LOG.warnf("MPGS retrieveOrder skipped — MPGS is not configured (enabled=%s, merchantId set=%s)",
                    config.enabled(), !config.merchantId().isBlank());
            return null;
        }

        try {
            var url = config.orderUrl(mpgsOrderId);
            LOG.infof("MPGS retrieveOrder: GET %s", url);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", config.basicAuthHeader())
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            LOG.infof("MPGS retrieveOrder response: HTTP %d for order %s — body: %s",
                    response.statusCode(), mpgsOrderId, response.body());

            if (response.statusCode() != 200) {
                LOG.errorf("MPGS retrieveOrder returned non-200 for order %s: HTTP %d — body: %s",
                        mpgsOrderId, response.statusCode(), response.body());
                return null;
            }

            var json = MAPPER.readTree(response.body());

            var status = str(json, "status");
            var result = str(json, "result");
            var captured = num(json, "totalCapturedAmount");
            String txnId = null;
            if (json.has("transaction") && json.get("transaction").isArray()
                    && json.get("transaction").size() > 0) {
                var firstTxn = json.get("transaction").get(0);
                txnId = firstTxn.has("id") ? firstTxn.get("id").asText() : null;
            }

            LOG.infof("MPGS retrieveOrder parsed for order %s: status=%s, result=%s, captured=%s, txnId=%s",
                    mpgsOrderId, status, result, captured, txnId);

            return new MpgsOrderResult(status, result, captured, txnId);

        } catch (Exception e) {
            LOG.errorf(e, "MPGS retrieve order error for order id %s", mpgsOrderId);
            return null;
        }
    }

    /**
     * Verify the webhook notification secret. Constant-time comparison to
     * defeat timing side-channels.
     */
    public boolean verifyNotificationSecret(String provided) {
        var expected = config.notificationSecret();
        if (expected == null || expected.isBlank() || provided == null) return false;
        var a = expected.getBytes(StandardCharsets.UTF_8);
        var b = provided.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= (a[i] ^ b[i]);
        return diff == 0;
    }

    // -------------------------------------------------------------------------
    // JSON helpers (Jackson JsonNode)
    // -------------------------------------------------------------------------

    private static String str(JsonNode node, String path) {
        if (node == null) return null;
        var parts = path.split("\\.");
        JsonNode current = node;
        for (var p : parts) {
            if (current == null || !current.has(p)) return null;
            current = current.get(p);
        }
        return current == null || current.isNull() ? null : current.asText();
    }

    private static BigDecimal num(JsonNode node, String path) {
        var s = str(node, path);
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
