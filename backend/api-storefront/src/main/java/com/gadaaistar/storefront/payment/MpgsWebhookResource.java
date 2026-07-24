package com.gadaaistar.storefront.payment;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;
import com.gadaaistar.storefront.order.OrderService;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Public webhook that MPGS (Mastercard Payment Gateway Services) POSTs to
 * when a payment event occurs (capture, authorization, decline, cancellation).
 *
 * <p>Endpoint is intentionally unauthenticated (public) — MPGS has no bearer
 * token — but every request is verified against the configured notification
 * secret before we mutate any order state. Any request that fails secret
 * verification returns 400.
 *
 * <p>The notification URL must be configured in the MPGS Merchant
 * Administration portal (Admin → Webhook Notifications). The secret
 * generated there must match {@code gadaa.mpgs.notification-secret}.
 *
 * <p>MPGS sends the notification as a JSON body. The {@code notificationSecret}
 * field in the body is verified against our configured value. The
 * {@code order.id} field maps to our {@code invoiceNumber}.
 */
@Path("/api/storefront/v1/payment/mpgs/webhook")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Payment webhooks", description = "MPGS hosted checkout notification endpoint.")
public class MpgsWebhookResource {

    private static final Logger LOG = Logger.getLogger(MpgsWebhookResource.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject MpgsService             mpgs;
    @Inject CheckoutOrderRepository orders;
    @Inject OrderService            orderService;

    @POST
    @Transactional
    @Operation(summary = "Accept an MPGS webhook notification. Verifies notification secret before mutating.")
    public Response handle(String body) {
        JsonNode json;
        try {
            json = MAPPER.readTree(body);
        } catch (Exception e) {
            LOG.warnf("MPGS webhook: could not parse JSON body");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("code", "INVALID_JSON",
                                   "message", "Could not parse request body."))
                    .build();
        }

        var providedSecret = json.has("notificationSecret")
                ? json.get("notificationSecret").asText() : null;

        if (!mpgs.verifyNotificationSecret(providedSecret)) {
            LOG.warnf("MPGS webhook: notification secret mismatch");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("code", "INVALID_SECRET",
                                   "message", "Notification secret verification failed."))
                    .build();
        }

        // MPGS order.id = our invoiceNumber.
        var orderId = json.has("order") && json.get("order").has("id")
                ? json.get("order").get("id").asText() : null;

        if (orderId == null || orderId.isBlank()) {
            LOG.warnf("MPGS webhook: no order.id in notification");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("code", "MISSING_ORDER_ID",
                                   "message", "No order.id in notification."))
                    .build();
        }

        var orderOpt = orders.findByUuid(orderId)
                .or(() -> orders.findByInvoiceNumber(orderId));
        if (orderOpt.isEmpty()) {
            LOG.warnf("MPGS webhook for unknown order.id %s", orderId);
            return Response.ok(Map.of("status", "unknown_order")).build();
        }
        var order = orderOpt.get();

        // Idempotency guard.
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            LOG.infof("Ignoring MPGS webhook — order %s already in state %s.",
                    order.uuid, order.status);
            return Response.ok(Map.of("status", "already_processed",
                                       "orderStatus", order.status.name())).build();
        }

        // Extract transaction details.
        var txnNode = json.has("transaction") ? json.get("transaction") : null;
        var txnId = txnNode != null && txnNode.has("id")
                ? txnNode.get("id").asText() : null;
        var txnResult = txnNode != null && txnNode.has("result")
                ? txnNode.get("result").asText() : null;
        var orderStatus = json.has("status")
                ? json.get("status").asText() : null;

        // Determine outcome. MPGS status can be: CAPTURED, AUTHORIZED,
        // FAILED, CANCELLED, etc. Both CAPTURED and AUTHORIZED with SUCCESS
        // result indicate a successful payment — CAPTURED means the funds
        // have been captured, AUTHORIZED means the card was authenticated
        // and the funds are reserved (capture may happen separately).
        var statusUpper = orderStatus == null ? "" : orderStatus.toUpperCase();
        var resultUpper = txnResult == null ? "" : txnResult.toUpperCase();

        if (("CAPTURED".equals(statusUpper) || "AUTHORIZED".equals(statusUpper))
                && "SUCCESS".equals(resultUpper)) {
            orderService.markPaid(order, txnId, "MPGS");
            LOG.infof("MPGS %s for order %s (txn %s)", statusUpper, order.uuid, txnId);
            return Response.ok(Map.of("status", "paid", "orderUuid", order.uuid)).build();
        }

        if ("FAILED".equals(statusUpper) || "FAILURE".equals(resultUpper)
                || "CANCELLED".equals(statusUpper)) {
            var reason = "MPGS payment " + statusUpper.toLowerCase()
                    + " (result " + resultUpper + ")";
            orderService.markPaymentFailed(order, reason);
            LOG.infof("MPGS %s for order %s (txn %s)", statusUpper, order.uuid, txnId);
            return Response.ok(Map.of("status", "cancelled", "orderUuid", order.uuid)).build();
        }

        // AUTHORIZED or unknown — leave in PENDING_PAYMENT.
        LOG.warnf("MPGS returned indeterminate status '%s' (result '%s') for order %s (txn %s)",
                orderStatus, txnResult, order.uuid, txnId);
        return Response.ok(Map.of("status", "pending_review",
                                  "orderUuid", order.uuid,
                                  "mpgsStatus", orderStatus == null ? "" : orderStatus)).build();
    }
}
