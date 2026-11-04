package com.gadaaistar.storefront.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.jboss.logging.Logger;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;
import com.gadaaistar.storefront.order.OrderService;
import com.gadaaistar.storefront.ratelimit.RateLimit;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

/**
 * Public return endpoints for the YagoutPay hosted-checkout flow.
 *
 * <p>YagoutPay has no server-to-server webhook — the gateway POSTs the
 * (AES-encrypted) payment result straight to the browser-facing
 * {@code success_url} / {@code failure_url} embedded in the checkout
 * payload. These endpoints decrypt + validate that POST, mutate the order,
 * and 303-redirect the browser back to the Angular checkout pages, which
 * then poll {@code /me/orders/{uuid}} as usual.
 *
 * <p>Integrity model: forgery requires the shared merchant key (AES-CBC),
 * and we additionally require the decrypted {@code me_id}, amount, and
 * currency to match the order before any state mutation. On any validation
 * failure we mutate nothing and redirect to the cancel page with a generic
 * error flag — gateway internals never reach the browser.
 */
@Path("/api/storefront/v1/payment/yagoutpay")
public class YagoutPayReturnResource {

    private static final Logger LOG = Logger.getLogger(YagoutPayReturnResource.class);

    private static final String PROVIDER = "YAGOUTPAY";

    @jakarta.inject.Inject YagoutPayService yagout;
    @jakarta.inject.Inject YagoutPayConfig config;
    @jakarta.inject.Inject CheckoutOrderRepository orders;
    @jakarta.inject.Inject OrderService orderService;

    /** success_url — gateway POSTs here after a completed (or declined) checkout. */
    @POST
    @Path("/return")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    @RateLimit(bucket = "yagoutpay-return", max = 60, windowSeconds = 60)
    @Transactional
    public Response paymentReturn(MultivaluedMap<String, String> form) {
        return handle(form, true);
    }

    /** failure_url — gateway POSTs here when checkout is cancelled or errors. */
    @POST
    @Path("/failure")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    @RateLimit(bucket = "yagoutpay-return", max = 60, windowSeconds = 60)
    @Transactional
    public Response paymentFailure(MultivaluedMap<String, String> form) {
        return handle(form, false);
    }

    private Response handle(MultivaluedMap<String, String> form, boolean successPath) {
        var result = yagout.parseReturn(toMap(form));
        if (result == null) {
            return redirect(cancelUrl(null, true));
        }

        var order = resolveOrder(result);
        if (order == null) {
            LOG.warnf("YagoutPay return for unknown order_no %s — no mutation",
                    result.orderNo());
            return redirect(cancelUrl(null, true));
        }

        if (!PROVIDER.equalsIgnoreCase(order.paymentMethod)) {
            // A YagoutPay response arriving for an order placed through
            // another gateway means a mismatched/replayed callback.
            LOG.warnf("YagoutPay return for order %s whose paymentMethod is %s "
                    + "— no mutation", order.uuid, order.paymentMethod);
            return redirect(cancelUrl(order.uuid, true));
        }

        if (order.status != OrderStatus.PENDING_PAYMENT) {
            // Idempotent replay — the order already reached a terminal state.
            LOG.infof("YagoutPay return for already-processed order %s "
                    + "(status %s)", order.uuid, order.status);
            return redirect(returnUrl(order.uuid));
        }

        if (!fieldsMatch(result, order)) {
            LOG.errorf("YagoutPay return field mismatch for order %s "
                    + "(gateway amount=%s %s, order total=%s %s) — no mutation",
                    order.uuid, result.amount(), result.currency(),
                    order.grandTotal, order.currency);
            return redirect(cancelUrl(order.uuid, true));
        }

        if (successPath && result.isSuccessful()) {
            var reference = result.agRef() != null && !result.agRef().isBlank()
                    ? result.agRef()
                    : result.pgRef();
            if (result.pgRef() != null && !result.pgRef().isBlank()
                    && !result.pgRef().equals(reference)) {
                reference = reference + "/" + result.pgRef();
            }
            orderService.markPaid(order, reference, PROVIDER);
            LOG.infof("YagoutPay payment captured for order %s (ref %s)",
                    order.uuid, reference);
            return redirect(returnUrl(order.uuid));
        }

        // Any other outcome — decline on the success URL, or anything at all
        // on the failure URL — is treated as a failed payment attempt.
        var reason = result.resMessage() != null && !result.resMessage().isBlank()
                ? result.resMessage()
                : "YagoutPay payment failed (" + result.status()
                        + "/" + result.resCode() + ")";
        orderService.markPaymentFailed(order, reason);
        LOG.infof("YagoutPay payment failed for order %s: %s", order.uuid, reason);
        return redirect(cancelUrl(order.uuid, false));
    }

    /**
     * Resolve the order via {@code udf_1} (the order UUID we embedded in the
     * request), falling back to the invoice number recovered by stripping the
     * {@code -N} attempt suffix off {@code order_no}.
     */
    private CheckoutOrder resolveOrder(YagoutPayService.YagoutResult result) {
        if (result.orderUuid() != null) {
            var order = orders.findByUuid(result.orderUuid());
            if (order.isPresent()) return order.get();
        }
        if (result.orderNo() == null) return null;
        // Exact invoice match first — order_no is the invoice number on the
        // first attempt, and exact match beats suffix-stripping guesses.
        var order = orders.findByInvoiceNumber(result.orderNo());
        if (order.isPresent()) return order.get();
        var invoice = YagoutPayService.invoiceFromOrderNo(result.orderNo());
        return invoice.equals(result.orderNo())
                ? null
                : orders.findByInvoiceNumber(invoice).orElse(null);
    }

    /** Amount + currency echoed by the gateway must match the order exactly. */
    private static boolean fieldsMatch(YagoutPayService.YagoutResult result,
                                       CheckoutOrder order) {
        if (result.currency() == null
                || !result.currency().equalsIgnoreCase(order.currency)) {
            return false;
        }
        try {
            var charged = new BigDecimal(result.amount())
                    .setScale(2, RoundingMode.HALF_UP);
            var expected = order.grandTotal.setScale(2, RoundingMode.HALF_UP);
            return charged.compareTo(expected) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Map<String, String> toMap(MultivaluedMap<String, String> form) {
        var map = new HashMap<String, String>();
        if (form != null) {
            form.forEach((k, v) -> map.put(k, v == null || v.isEmpty() ? null : v.get(0)));
        }
        return map;
    }

    private String returnUrl(String orderUuid) {
        return config.storefrontBaseUrl()
                + "/checkout/return?order=" + orderUuid
                + "&provider=" + PROVIDER;
    }

    private String cancelUrl(String orderUuid, boolean paymentError) {
        var url = config.storefrontBaseUrl() + "/checkout/cancel?provider=" + PROVIDER;
        if (orderUuid != null) url += "&order=" + orderUuid;
        if (paymentError) url += "&paymentError=1";
        return url;
    }

    private static Response redirect(String url) {
        // 303 See Other — converts the gateway's POST into a browser GET on
        // the storefront without re-posting the form.
        return Response.seeOther(URI.create(url)).build();
    }
}
