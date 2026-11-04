package com.gadaaistar.admin.refunds;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import com.gadaaistar.admin.payment.CyberSourceRefundClient;
import com.gadaaistar.admin.payment.CyberSourceRefundConfig;
import com.gadaaistar.admin.payment.MpgsRefundClient;
import com.gadaaistar.admin.payment.MpgsRefundConfig;
import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.common.exceptions.ValidationException;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;
import com.gadaaistar.persistence.order.OrderRefund;
import com.gadaaistar.persistence.order.OrderRefundRepository;
import com.gadaaistar.persistence.order.OrderStatusEvent;
import com.gadaaistar.persistence.order.OrderStatusEventRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Coordinates a refund from the admin UI end-to-end:
 *
 * <ol>
 *   <li>Validate the order is in a refundable state (paid or beyond).</li>
 *   <li>Validate the requested amount doesn't exceed the remaining
 *       refundable balance (grand_total − already_refunded).</li>
 *   <li>Call the CyberSource REST API (or record MANUAL when creds are
 *       absent / the admin explicitly ticked the manual box).</li>
 *   <li>Persist the {@code order_refund} audit row with the outcome.</li>
 *   <li>Update the parent order's denormalised {@code refunded_total} and
 *       transition the {@link OrderStatus} to {@code PARTIALLY_REFUNDED}
 *       or {@code REFUNDED} as appropriate.</li>
 *   <li>Emit an {@code OrderStatusEvent} for the timeline.</li>
 * </ol>
 *
 * <p>All the state mutation happens inside a single {@link Transactional}
 * so a gateway-succeeded refund that fails to persist rolls back cleanly.
 * The gateway itself is not transactional — a partial failure (gateway
 * OK, DB write failed) requires manual reconciliation, but the wide log
 * lines mean an admin can find the CyberSource refund id and either add
 * a MANUAL row or roll it back gateway-side.
 */
@ApplicationScoped
public class RefundService {

    private static final Logger LOG = Logger.getLogger(RefundService.class);

    @Inject CheckoutOrderRepository    orders;
    @Inject OrderRefundRepository      refunds;
    @Inject OrderStatusEventRepository events;
    @Inject CyberSourceRefundClient    cybersourceGateway;
    @Inject CyberSourceRefundConfig    cybersourceConfig;
    @Inject MpgsRefundClient           mpgsGateway;
    @Inject MpgsRefundConfig           mpgsConfig;
    @Inject RefundMailService          refundMail;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Transactional
    public OrderRefund refund(String orderUuid, RefundDtos.CreateRefundRequest req, Long adminId) {
        var order = orders.findByUuid(orderUuid)
                .orElseThrow(() -> new NotFoundException("Order", orderUuid));

        assertRefundable(order);
        var amount = normaliseAmount(req.amount());
        assertAmountFits(order, amount);

        // Decide provider up-front. If the admin explicitly ticked
        // "manual" we don't call the gateway at all; otherwise we call
        // the appropriate gateway based on the order's paymentProvider.
        // A dev environment without creds transparently degrades to
        // MANUAL — the audit row still lands and the order status still
        // transitions.
        var provider = order.paymentProvider == null ? "CYBERSOURCE" : order.paymentProvider.toUpperCase();
        boolean callGateway = !Boolean.TRUE.equals(req.manual());
        if ("MPGS".equals(provider)) {
            callGateway = callGateway && mpgsConfig.isConfigured();
        } else if ("YAGOUTPAY".equals(provider)) {
            // YagoutPay's integration documents no refund API — every refund
            // is settled out-of-band and only the audit row + status update
            // happen here, regardless of the manual checkbox.
            callGateway = false;
        } else {
            callGateway = callGateway && cybersourceConfig.isConfigured();
        }

        var row = new OrderRefund();
        row.order              = order;
        row.amount             = amount;
        row.reason             = req.reason().trim();
        row.initiatedByAdminId = adminId;
        row.status             = OrderRefund.Status.PENDING;

        if (callGateway) {
            boolean success;
            String refundId = null;
            String errorCode = null;
            String errorMessage = null;
            if ("MPGS".equals(provider)) {
                var outcome = mpgsGateway.refund(order.invoiceNumber, order.paymentReference,
                        amount, order.currency, req.reason());
                row.gatewayProvider = "MPGS";
                success = outcome.success();
                refundId = outcome.refundId();
                errorCode = outcome.errorCode();
                errorMessage = outcome.errorMessage();
            } else {
                var outcome = cybersourceGateway.refund(order.paymentReference,
                        amount, order.currency, req.reason());
                row.gatewayProvider = "CYBERSOURCE";
                success = outcome.success();
                refundId = outcome.refundId();
                errorCode = outcome.errorCode();
                errorMessage = outcome.errorMessage();
            }
            if (success) {
                row.status           = OrderRefund.Status.SUCCEEDED;
                row.gatewayReference = refundId;
            } else {
                row.status              = OrderRefund.Status.FAILED;
                row.gatewayErrorCode    = errorCode;
                row.gatewayErrorMessage = errorMessage;
            }
        } else {
            // MANUAL — the admin refunded the customer out-of-band and
            // just needs the audit row + status update. Keep the original
            // gateway on the row for YagoutPay so the audit trail shows the
            // refund was an out-of-band YagoutPay refund, not a generic one.
            row.gatewayProvider = "YAGOUTPAY".equals(provider) ? "YAGOUTPAY" : "MANUAL";
            row.status          = OrderRefund.Status.SUCCEEDED;
        }

        refunds.persist(row);

        if (row.status == OrderRefund.Status.SUCCEEDED) {
            applySucceededRefundToOrder(order, amount, adminId, req.reason());
        } else {
            // Emit a timeline event even on failure — support wants to see
            // "attempted a $40 refund; gateway said DECLINED" without
            // spelunking log files.
            var evt = new OrderStatusEvent();
            evt.order      = order;
            evt.fromStatus = order.status;
            evt.toStatus   = order.status;                  // no transition on failure
            evt.source     = OrderStatusEvent.Source.ADMIN;
            evt.actorId    = adminId;
            evt.note       = "Refund attempt failed ("
                    + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    + " " + order.currency + "): "
                    + row.gatewayErrorCode + " — " + row.gatewayErrorMessage;
            events.persist(evt);
        }

        LOG.infof("Admin %s refund on order %s: %s %s via %s → %s%s",
                adminId, order.uuid,
                amount.setScale(2, RoundingMode.HALF_UP), order.currency,
                row.gatewayProvider, row.status,
                row.status == OrderRefund.Status.FAILED
                        ? " (" + row.gatewayErrorCode + ")"
                        : "");

        // Send refund receipt email on success — fire-and-forget.
        if (row.status == OrderRefund.Status.SUCCEEDED) {
            refundMail.sendRefundReceipt(order, row)
                    .exceptionally(err -> { LOG.errorf(err, "Failed to send refund receipt to %s", order.customer.email); return null; });
        }

        return row;
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private static void assertRefundable(CheckoutOrder order) {
        // The set of states we allow refunds in. Notably absent:
        // PENDING_PAYMENT (no capture yet — customer never paid) and
        // CANCELLED (we already refunded / never captured, depending on
        // when the cancel happened).
        switch (order.status) {
            case PAID, FULFILLING, SHIPPED, DELIVERED, PARTIALLY_REFUNDED -> {
                // OK to refund
            }
            case REFUNDED -> throw new ConflictException("ORDER_FULLY_REFUNDED",
                    "This order has already been fully refunded.",
                    Map.of("orderUuid", order.uuid, "status", order.status.name()));
            default -> throw new ConflictException("ORDER_NOT_REFUNDABLE",
                    "An order in status " + order.status + " cannot be refunded.",
                    Map.of("orderUuid", order.uuid, "status", order.status.name()));
        }
    }

    private static BigDecimal normaliseAmount(BigDecimal raw) {
        if (raw == null) {
            throw new ValidationException("Refund amount is required.", Map.of());
        }
        var amount = raw.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new ValidationException("Refund amount must be greater than zero.",
                    Map.of("amount", raw.toPlainString()));
        }
        return amount;
    }

    private static void assertAmountFits(CheckoutOrder order, BigDecimal amount) {
        var remaining = order.grandTotal.setScale(2, RoundingMode.HALF_UP)
                .subtract(order.refundedTotal.setScale(2, RoundingMode.HALF_UP));
        if (amount.compareTo(remaining) > 0) {
            throw new ConflictException("REFUND_EXCEEDS_REMAINING",
                    "Requested refund exceeds the remaining refundable balance ("
                            + remaining.toPlainString() + " " + order.currency + ").",
                    Map.of("orderUuid", order.uuid,
                            "requested", amount.toPlainString(),
                            "remaining", remaining.toPlainString(),
                            "currency",  order.currency));
        }
    }

    // -------------------------------------------------------------------------
    // Aggregate update + timeline event on a successful refund
    // -------------------------------------------------------------------------

    private void applySucceededRefundToOrder(CheckoutOrder order, BigDecimal amount,
                                              Long adminId, String reason) {
        var previousStatus = order.status;

        order.refundedTotal = order.refundedTotal
                .setScale(2, RoundingMode.HALF_UP)
                .add(amount);

        // Decide the next status. Compare against grandTotal at scale 2
        // to avoid a rounding-driven "0.0001 short of full" bug.
        var full = order.grandTotal.setScale(2, RoundingMode.HALF_UP);
        var refunded = order.refundedTotal.setScale(2, RoundingMode.HALF_UP);
        OrderStatus next;
        if (refunded.compareTo(full) >= 0) {
            next = OrderStatus.REFUNDED;
        } else {
            next = OrderStatus.PARTIALLY_REFUNDED;
        }

        // PARTIALLY_REFUNDED can already be the current state (multiple
        // partials over time) — that's a "same-state" event, no transition.
        boolean stateChanges = next != previousStatus;
        if (stateChanges) {
            if (!previousStatus.canTransitionTo(next)) {
                // Should never happen given assertRefundable above, but
                // fail loud rather than silently corrupt the audit trail.
                throw new ConflictException("ILLEGAL_ORDER_TRANSITION",
                        "Cannot move order from " + previousStatus + " to " + next
                                + " on refund.",
                        Map.of("from", previousStatus.name(), "to", next.name()));
            }
            order.status = next;
        }

        var evt = new OrderStatusEvent();
        evt.order      = order;
        evt.fromStatus = previousStatus;
        evt.toStatus   = order.status;
        evt.source     = OrderStatusEvent.Source.ADMIN;
        evt.actorId    = adminId;
        evt.note       = "Refunded " + amount.toPlainString() + " " + order.currency
                + " (" + refunded.toPlainString() + "/" + full.toPlainString() + ")"
                + (reason == null || reason.isBlank() ? "" : " — " + reason.trim());
        events.persist(evt);
    }
}
