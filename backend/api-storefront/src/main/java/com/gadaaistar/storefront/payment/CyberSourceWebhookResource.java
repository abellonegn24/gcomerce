package com.gadaaistar.storefront.payment;

import java.util.HashMap;
import java.util.Map;

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
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Public webhook the CyberSource hosted-checkout page POSTs to on success,
 * failure, or cancellation. Both the customer's return-to-site landing page
 * and the server-to-server "silent" notification post form-urlencoded fields
 * back here — the shape is identical; we route both to the same handler.
 *
 * <p>Endpoint is intentionally unauthenticated (public) — CyberSource has no
 * bearer token — but every request is HMAC-verified before we mutate any
 * order state. Any request that fails signature verification returns 400
 * without logging the failed body (defensive against attackers spraying
 * malformed POSTs).
 *
 * <p>Return-to-customer redirects are handled entirely on the frontend; this
 * endpoint just processes state.
 */
@Path("/api/storefront/v1/payment/cybersource/webhook")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Tag(name = "Payment webhooks", description = "CyberSource Secure Acceptance return + notify endpoints.")
public class CyberSourceWebhookResource {

    private static final Logger LOG = Logger.getLogger(CyberSourceWebhookResource.class);

    @Inject CyberSourceService        cybersource;
    @Inject CheckoutOrderRepository   orders;
    @Inject OrderService              orderService;

    @POST
    @Transactional
    @Operation(summary = "Accept a CyberSource Secure Acceptance return / notify POST. Verifies HMAC before mutating.")
    public Response handle(MultivaluedMap<String, String> form) {
        Map<String, String> params = new HashMap<>();
        form.forEach((k, v) -> params.put(k, v == null || v.isEmpty() ? "" : v.get(0)));

        if (!cybersource.verifySignature(params)) {
            LOG.warnf("CyberSource webhook signature mismatch (reference=%s, txn=%s)",
                    params.get("req_reference_number"), params.get("transaction_id"));
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("code", "INVALID_SIGNATURE",
                                   "message", "Signature verification failed."))
                    .build();
        }

        // CyberSource echoes the invoice_number back as `req_reference_number`.
        var invoiceNumber = params.get("req_reference_number");
        var decision      = params.get("decision");           // ACCEPT / REVIEW / DECLINE / CANCEL / ERROR
        var reason        = params.get("reason_code");
        var transactionId = params.get("transaction_id");

        var orderOpt = invoiceNumber == null
                ? java.util.Optional.<com.gadaaistar.persistence.order.CheckoutOrder>empty()
                : orders.findByInvoiceNumber(invoiceNumber);
        if (orderOpt.isEmpty()) {
            LOG.warnf("CyberSource webhook for unknown invoice %s (txn %s)", invoiceNumber, transactionId);
            return Response.ok(Map.of("status", "unknown_order")).build();
        }
        var order = orderOpt.get();

        // Idempotency guard: if the webhook fires twice we ignore the second
        // one so we don't double-decrement inventory or re-emit events.
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            LOG.infof("Ignoring CyberSource webhook — order %s already in state %s.",
                    order.uuid, order.status);
            return Response.ok(Map.of("status", "already_processed",
                                       "orderStatus", order.status.name())).build();
        }

        switch (decision == null ? "" : decision.toUpperCase()) {
            case "ACCEPT" -> {
                orderService.markPaid(order, transactionId, "CYBERSOURCE");
                LOG.infof("CyberSource ACCEPT for order %s (txn %s, reason %s)",
                        order.uuid, transactionId, reason);
                return Response.ok(Map.of("status", "paid", "orderUuid", order.uuid)).build();
            }
            case "DECLINE", "CANCEL", "ERROR" -> {
                orderService.markPaymentFailed(order,
                        "Payment " + decision.toLowerCase() + " (reason " + reason + ").");
                LOG.infof("CyberSource %s for order %s (txn %s, reason %s)",
                        decision, order.uuid, transactionId, reason);
                return Response.ok(Map.of("status", "cancelled", "orderUuid", order.uuid)).build();
            }
            default -> {
                // REVIEW / unknown — leave in PENDING_PAYMENT and let a human check.
                LOG.warnf("CyberSource returned indeterminate decision '%s' for order %s (txn %s, reason %s)",
                        decision, order.uuid, transactionId, reason);
                return Response.ok(Map.of("status", "pending_review",
                                          "orderUuid", order.uuid,
                                          "decision", decision == null ? "" : decision)).build();
            }
        }
    }
}
