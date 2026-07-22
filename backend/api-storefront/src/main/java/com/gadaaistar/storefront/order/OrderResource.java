package com.gadaaistar.storefront.order;

import java.util.List;
import java.util.Map;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.ForbiddenException;
import com.gadaaistar.storefront.payment.CyberSourceService;
import com.gadaaistar.storefront.payment.MpgsConfig;
import com.gadaaistar.storefront.payment.MpgsService;
import com.gadaaistar.storefront.ratelimit.RateLimit;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Customer-facing orders API.
 *
 * <p>Placement flow:
 * <ol>
 *   <li>{@code POST /me/orders} to freeze the cart into a PENDING_PAYMENT order.</li>
 *   <li>{@code GET  /me/orders/{uuid}/payment-payload} to get the signed
 *       CyberSource form fields; the browser then POSTs them to the gateway.</li>
 *   <li>After the customer returns, {@code GET /me/orders/{uuid}} shows the
 *       fresh status (webhook may have already flipped it to PAID).</li>
 * </ol>
 */
@Path("/api/storefront/v1/me/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Orders (Storefront)", description = "The signed-in customer's placed orders.")
public class OrderResource {

    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    @Inject OrderService          orderService;
    @Inject OrderMapper           mapper;
    @Inject CyberSourceService    cybersource;
    @Inject MpgsService           mpgs;
    @Inject MpgsConfig            mpgsConfig;
    @Inject SecurityContextHolder security;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    @GET
    @Operation(summary = "Paged list of the signed-in customer's orders (most recent first).")
    public PagedOrders list(@QueryParam("page") @DefaultValue("0") int page,
                             @QueryParam("size") @DefaultValue("20") int size) {
        var customerId = security.requireCustomer();
        var clamped = Math.min(Math.max(size, 1), 100);
        var pageIdx = Math.max(page, 0);
        var rows    = orderService.listForCustomer(customerId, pageIdx, clamped)
                .stream().map(mapper::toSummary).toList();
        var total   = orderService.countForCustomer(customerId);
        return new PagedOrders(rows, pageIdx, clamped, total);
    }

    @GET
    @Path("/transactions")
    @Operation(summary = "All transactions across all customers — only available to the B2B admin viewer.")
    public PagedTransactions listAllTransactions(@QueryParam("page") @DefaultValue("0") int page,
                                                  @QueryParam("size") @DefaultValue("20") int size) {
        var customerId = security.requireCustomer();
        if (!orderService.isTransactionViewer(customerId)) {
            throw new ForbiddenException("You do not have access to this resource.");
        }
        var clamped = Math.min(Math.max(size, 1), 100);
        var pageIdx = Math.max(page, 0);
        var rows    = orderService.listAllTransactions(pageIdx, clamped)
                .stream().map(mapper::toTransactionSummary).toList();
        var total   = orderService.countAllTransactions();
        return new PagedTransactions(rows, pageIdx, clamped, total);
    }

    @GET
    @Path("/{uuid}")
    @Operation(summary = "Full detail — line items, status history, addresses — for a single order.")
    public OrderDtos.OrderDetail get(@PathParam("uuid") String uuid) {
        var customerId = security.requireCustomer();
        return mapper.toDetail(orderService.getForCustomer(customerId, uuid));
    }

    // -------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------

    @POST
    @RateLimit(bucket = "order-place", max = 6, windowSeconds = 60)
    @Operation(summary = "Freeze the caller's cart as a PENDING_PAYMENT order. Rate-limited to blunt double-taps.")
    public Response placeOrder(@Valid OrderDtos.PlaceOrderRequest req) {
        var customerId = security.requireCustomer();
        var order = orderService.createFromCart(customerId, req);
        return Response.status(Response.Status.CREATED).entity(mapper.toDetail(order)).build();
    }

    // -------------------------------------------------------------------------
    // Payment payload
    // -------------------------------------------------------------------------

    @GET
    @Path("/{uuid}/payment-payload")
    @Operation(summary = "Return the signed form fields (CyberSource) or session config (MPGS) the browser needs to redirect to the hosted checkout page.")
    public PaymentPayload paymentPayload(@PathParam("uuid") String uuid) {
        var customerId = security.requireCustomer();
        var order = orderService.getForCustomer(customerId, uuid);
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("ORDER_NOT_AWAITING_PAYMENT",
                    "This order is no longer awaiting payment.",
                    Map.of("status", order.status.name()));
        }
        var method = order.paymentMethod == null ? "CYBERSOURCE" : order.paymentMethod.toUpperCase();
        if ("MPGS".equals(method)) {
            if (order.mpgsSessionId == null || order.mpgsSessionId.isBlank()) {
                throw new ConflictException("PAYMENT_GATEWAY_NOT_CONFIGURED",
                        "MPGS session was not created for this order. Contact support.",
                        Map.of());
            }
            var fields = new java.util.LinkedHashMap<String, String>();
            fields.put("merchantId", mpgsConfig.merchantId());
            fields.put("sessionId", order.mpgsSessionId);
            fields.put("successIndicator", order.mpgsSuccessIndicator == null ? "" : order.mpgsSuccessIndicator);
            fields.put("merchantName", mpgsConfig.merchantName());
            fields.put("orderAmount", order.grandTotal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            fields.put("orderCurrency", order.currency);
            fields.put("orderId", order.invoiceNumber);
            // gatewayUrl = checkout.js script URL; frontend loads it then calls Checkout.configure().
            return new PaymentPayload("MPGS", mpgsConfig.checkoutJsUrl(), fields);
        }
        // Default: CyberSource signed form fields.
        var payload = cybersource.buildRedirectPayload(order);
        return new PaymentPayload("CYBERSOURCE", payload.endpoint(), payload.fields());
    }

    // -------------------------------------------------------------------------
    // MPGS payment confirmation (pull-based fallback for when the webhook
    // hasn't been configured in the MPGS Merchant Administration portal)
    // -------------------------------------------------------------------------

    @POST
    @Path("/{uuid}/confirm-mpgs")
    @Transactional
    @Operation(summary = "Pull the order status from MPGS and mark the order PAID if captured. "
            + "Used as a fallback when the MPGS webhook notification URL is not configured.")
    public OrderDtos.OrderDetail confirmMpgsPayment(@PathParam("uuid") String uuid) {
        var customerId = security.requireCustomer();
        var order = orderService.getForCustomer(customerId, uuid);

        if (order.status != OrderStatus.PENDING_PAYMENT) {
            return mapper.toDetail(order);
        }

        if (!"MPGS".equals(order.paymentMethod == null ? "" : order.paymentMethod.toUpperCase())) {
            throw new ConflictException("ORDER_NOT_MPGS",
                    "This order was not placed with MPGS.",
                    Map.of("paymentMethod", order.paymentMethod == null ? "UNKNOWN" : order.paymentMethod));
        }

        var result = mpgs.retrieveOrder(order.invoiceNumber);
        if (result == null) {
            throw new ConflictException("MPGS_LOOKUP_FAILED",
                    "Could not retrieve payment status from MPGS. Please try again or contact support.",
                    Map.of());
        }

        var statusUpper = result.status() == null ? "" : result.status().toUpperCase();
        var resultUpper = result.result() == null ? "" : result.result().toUpperCase();

        if (("CAPTURED".equals(statusUpper) || "AUTHORIZED".equals(statusUpper))
                && "SUCCESS".equals(resultUpper)) {
            orderService.markPaid(order, result.transactionId(), "MPGS");
            LOG.infof("MPGS payment confirmed via pull (%s) for order %s (txn %s)", statusUpper, order.uuid, result.transactionId());
        } else if ("FAILED".equals(statusUpper) || "FAILURE".equals(resultUpper)
                || "CANCELLED".equals(statusUpper)) {
            var reason = "MPGS payment " + statusUpper.toLowerCase() + " (result " + resultUpper + ")";
            orderService.markPaymentFailed(order, reason);
            LOG.infof("MPGS payment %s via pull for order %s", statusUpper, order.uuid);
        } else {
            LOG.warnf("MPGS pull returned indeterminate status '%s' (result '%s') for order %s",
                    result.status(), result.result(), order.uuid);
            throw new ConflictException("MPGS_PAYMENT_PENDING",
                    "Payment status is " + statusUpper + " — the payment may still be processing. "
                            + "Please check back in a moment or contact support if this persists.",
                    Map.of("mpgsStatus", result.status() == null ? "" : result.status()));
        }

        return mapper.toDetail(orderService.getForCustomer(customerId, uuid));
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    @POST
    @Path("/{uuid}/cancel")
    @Operation(summary = "Cancel a PENDING_PAYMENT order. Reservations are released back to stock.")
    public OrderDtos.OrderDetail cancel(@PathParam("uuid") String uuid,
                                        @Valid OrderDtos.CancelOrderRequest req) {
        var customerId = security.requireCustomer();
        var order = orderService.cancelByCustomer(customerId, uuid,
                req == null ? null : req.reason());
        return mapper.toDetail(order);
    }

    // -------------------------------------------------------------------------
    // Inline response records
    // -------------------------------------------------------------------------

    public record PagedOrders(List<OrderDtos.OrderSummary> items, int page, int size, long total) {}

    public record PagedTransactions(List<OrderDtos.TransactionSummary> items, int page, int size, long total) {}

    public record PaymentPayload(String provider, String gatewayUrl, java.util.LinkedHashMap<String, String> fields) {}
}
