package com.gadaaistar.storefront.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.persistence.customer.CustomerAddress;
import com.gadaaistar.persistence.order.CheckoutOrder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Builds the YagoutPay "Aggregator Hosted (Non-Seamless)" redirect payload
 * and parses the encrypted success/failure responses the gateway POSTs back.
 *
 * <h3>Request</h3>
 * The browser POSTs three fields to the YagoutPay gateway:
 * <ul>
 *   <li>{@code me_id} — merchant id, plaintext</li>
 *   <li>{@code merchant_request} — AES-CBC ciphertext of a pipe/tilde
 *       delimited payload (9 sections joined by {@code ~}, fields within a
 *       section joined by {@code |})</li>
 *   <li>{@code hash} — AES-CBC of
 *       {@code sha256hex(me_id~order_no~amount~country~currency)}</li>
 * </ul>
 *
 * <h3>Response</h3>
 * The gateway POSTs {@code txn_response} (plus {@code pg_details},
 * {@code other_details}, …) to our success/failure URLs, each field
 * encrypted under the same key. The decrypted {@code txn_response} is:
 * <pre>
 * ag_id|me_id|order_no|amount|country|currency|txn_date|txn_time|
 * ag_ref|pg_ref|status|res_code|res_message
 * </pre>
 * Success = {@code status="Successful"} + {@code res_code="0"}.
 *
 * <p>There is no webhook and no HMAC — AES + strict field validation (me_id,
 * order_no, amount, currency) is the integrity check, and {@code udf_1}
 * carries the order UUID so the return handler can resolve the order
 * independently of the (attempt-suffixed) order number.
 */
@ApplicationScoped
public class YagoutPayService {

    private static final Logger LOG = Logger.getLogger(YagoutPayService.class);

    private static final String TXN_TYPE = "SALE";
    private static final String CHANNEL  = "WEB";

    @Inject YagoutPayConfig config;

    /**
     * What the browser needs to submit the hosted-checkout form:
     * the gateway endpoint plus the ordered form fields to POST to it.
     * Reuses the same shape as {@link CyberSourceService.RedirectPayload} so
     * the frontend's auto-POST path works unchanged.
     */
    public record RedirectPayload(String endpoint,
                                  LinkedHashMap<String, String> fields) {}

    /**
     * A parsed + validated {@code txn_response} from a gateway return POST.
     *
     * @param orderUuid   the order's public UUID, recovered from
     *                    {@code other_details.udf_1} (may be {@code null} if
     *                    the gateway omitted it)
     * @param orderNo     the order number we sent (invoice number, possibly
     *                    suffixed {@code -N} on retry attempts)
     * @param amount      the amount string echoed by the gateway
     * @param currency    the currency echoed by the gateway
     * @param status      gateway status word ("Successful", "Failed", …)
     * @param resCode     numeric response code ("0" = success)
     * @param resMessage  human-readable gateway message
     * @param agRef       YagoutPay aggregator reference (canonical txn id)
     * @param pgRef       payment-gateway reference
     */
    public record YagoutResult(String orderUuid, String orderNo,
                               String amount, String currency,
                               String status, String resCode, String resMessage,
                               String agRef, String pgRef) {

        public boolean isSuccessful() {
            return "Successful".equalsIgnoreCase(status) && "0".equals(resCode);
        }
    }

    // ---------------------------------------------------------------------
    // Outbound: hosted-checkout redirect payload
    // ---------------------------------------------------------------------

    /**
     * Build the encrypted form POST for {@code order}. {@code attempt} is the
     * 1-based count of redirect payloads built for this order (see
     * {@code OrderService.registerPaymentAttempt}); attempts after the first
     * suffix {@code order_no} because YagoutPay requires it unique per
     * transaction request.
     */
    public RedirectPayload buildRedirectPayload(CheckoutOrder order, int attempt) {
        if (!config.isConfigured()) {
            throw new ConflictException("PAYMENT_GATEWAY_NOT_CONFIGURED",
                    "YagoutPay is not configured. Contact support.",
                    Map.of());
        }
        if (!config.supportsCurrency(order.currency)) {
            throw new ConflictException("PAYMENT_METHOD_CURRENCY_MISMATCH",
                    "YagoutPay cannot process orders in " + order.currency + ".",
                    Map.of("currency", order.currency));
        }

        var orderNo = orderNo(order, attempt);
        var amount  = order.grandTotal.setScale(2, RoundingMode.HALF_UP).toPlainString();

        var txnDetails = join(List.of(
                config.aggregatorId(), config.merchantId(), orderNo,
                amount, config.country(), order.currency,
                TXN_TYPE, config.successUrl(), config.failureUrl(), CHANNEL));

        var customer    = order.customer;
        var billAddress = order.billToAddress;
        var shipAddress = order.shipToAddress;
        var mobile      = firstNonBlank(
                billAddress == null ? null : billAddress.phone,
                customer.phone);

        var custDetails = join(List.of(
                customer.displayName(),
                nz(customer.email),
                nz(mobile),
                String.valueOf(customer.id),
                "Y"));

        var billDetails = billAddress == null ? "||||"
                : join(List.of(
                        street(billAddress), nz(billAddress.city), nz(billAddress.region),
                        nz(billAddress.countryCode), nz(billAddress.postalCode)));

        var shipDays = order.shippingMethod == null || order.shippingMethod.maxDays == null
                ? "" : String.valueOf(order.shippingMethod.maxDays);
        var shipDetails = shipAddress == null ? "||||||"
                : join(List.of(
                        street(shipAddress), nz(shipAddress.city), nz(shipAddress.region),
                        nz(shipAddress.countryCode), nz(shipAddress.postalCode),
                        shipDays, ""));

        var itemValues = order.items.stream()
                .map(i -> i.lineTotal.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .collect(Collectors.joining(","));
        var itemCategories = order.items.stream()
                .map(i -> i.product.category == null ? "" : i.product.category.name())
                .distinct()
                .collect(Collectors.joining(","));
        var itemDetails = join(List.of(
                String.valueOf(order.items.size()), itemValues, itemCategories));

        // other_details carries the order UUID in udf_1 so the return handler
        // can resolve the order regardless of attempt suffixes on order_no.
        var otherDetails = order.uuid + "||||";

        var message = String.join("~",
                txnDetails, "|||", "||||", custDetails, billDetails,
                shipDetails, itemDetails, "", otherDetails);

        // hash = AES(sha256hex(me_id~order_no~amount~country~currency)) —
        // TILDE-delimited per the spec, unlike the pipe-delimited sections.
        var hashInput = String.join("~",
                clean(config.merchantId()), clean(orderNo), clean(amount),
                clean(config.country()), clean(order.currency));

        var fields = new LinkedHashMap<String, String>();
        fields.put("me_id", config.merchantId());
        fields.put("merchant_request", YagoutPayCrypto.encrypt(message, config.encryptionKey()));
        fields.put("hash", YagoutPayCrypto.encrypt(
                YagoutPayCrypto.sha256Hex(hashInput), config.encryptionKey()));

        LOG.infof("Built YagoutPay redirect for order %s (order_no %s, attempt %d)",
                order.uuid, orderNo, attempt);
        return new RedirectPayload(config.gatewayUrl(), fields);
    }

    /**
     * The unique transaction order number: the invoice number, suffixed with
     * {@code -<attempt>} from the second attempt onward.
     */
    public static String orderNo(CheckoutOrder order, int attempt) {
        return attempt <= 1 ? order.invoiceNumber
                            : order.invoiceNumber + "-" + attempt;
    }

    /**
     * Inverse of {@link #orderNo}: strip a trailing {@code -N} attempt suffix
     * to recover the base invoice number. Returns the input unchanged when it
     * has no numeric suffix — or when the suffix is longer than 3 digits, so
     * an invoice number's own 5-digit tail ("GI-2026-00001") is never
     * mistaken for an attempt counter.
     */
    public static String invoiceFromOrderNo(String orderNo) {
        if (orderNo == null) return null;
        int dash = orderNo.lastIndexOf('-');
        if (dash <= 0 || dash == orderNo.length() - 1) return orderNo;
        var suffix = orderNo.substring(dash + 1);
        if (suffix.length() > 3) return orderNo;
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) return orderNo;
        }
        return orderNo.substring(0, dash);
    }

    // ---------------------------------------------------------------------
    // Inbound: success / failure return POST
    // ---------------------------------------------------------------------

    /**
     * Decrypt + parse a gateway return POST. Returns {@code null} when the
     * payload cannot be decrypted, is missing required fields, or was
     * produced for a different merchant (me_id mismatch) — the caller treats
     * {@code null} as "do not mutate any order state".
     */
    public YagoutResult parseReturn(Map<String, String> form) {
        if (!config.isConfigured()) {
            LOG.warnf("YagoutPay return received but gateway is not configured");
            return null;
        }

        var txnResponse = firstNonBlank(form.get("txn_response"), form.get("Txn_Response"));
        if (txnResponse == null) {
            LOG.warnf("YagoutPay return POST without txn_response field");
            return null;
        }

        String decrypted;
        try {
            decrypted = YagoutPayCrypto.decrypt(txnResponse, config.encryptionKey());
        } catch (Exception e) {
            LOG.warnf("YagoutPay txn_response failed to decrypt — dropping "
                    + "(%s)", e.getClass().getSimpleName());
            return null;
        }

        // ag_id|me_id|order_no|amount|country|currency|txn_date|txn_time|
        // ag_ref|pg_ref|status|res_code|res_message
        var f = decrypted.split("\\|", -1);
        if (f.length < 13) {
            LOG.warnf("YagoutPay txn_response had %d fields (expected >= 13)", f.length);
            return null;
        }

        var meId = f[1];
        if (!config.merchantId().equals(meId)) {
            LOG.warnf("YagoutPay txn_response me_id mismatch — dropping");
            return null;
        }

        var orderUuid = parseOrderUuid(form);
        var result = new YagoutResult(orderUuid, f[2], f[3], f[5],
                f[10], f[11], f[12], f[8], f[9]);
        LOG.infof("YagoutPay return parsed: order_no=%s status=%s res_code=%s "
                + "ag_ref=%s", result.orderNo(), result.status(),
                result.resCode(), result.agRef());
        return result;
    }

    /** Decrypt {@code other_details} (if present) and pull out udf_1. */
    private String parseOrderUuid(Map<String, String> form) {
        var other = form.get("other_details");
        if (other == null || other.isBlank()) return null;
        try {
            var f = YagoutPayCrypto.decrypt(other, config.encryptionKey())
                    .split("\\|", -1);
            return f.length > 0 && !f[0].isBlank() ? f[0] : null;
        } catch (Exception e) {
            LOG.warnf("YagoutPay other_details failed to decrypt");
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Formatting helpers
    // ---------------------------------------------------------------------

    /** Join section fields with {@code |}, sanitizing delimiter chars out. */
    private static String join(List<String> fields) {
        return fields.stream().map(YagoutPayService::clean)
                .collect(Collectors.joining("|"));
    }

    /** Payload delimiters must never appear inside field values. */
    private static String clean(String s) {
        return s == null ? "" : s.replace("|", " ").replace("~", " ").trim();
    }

    private static String street(CustomerAddress a) {
        var line2 = a.streetLine2;
        return (line2 == null || line2.isBlank())
                ? nz(a.streetLine1)
                : nz(a.streetLine1) + ", " + line2.trim();
    }

    private static String firstNonBlank(String... values) {
        for (var v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
