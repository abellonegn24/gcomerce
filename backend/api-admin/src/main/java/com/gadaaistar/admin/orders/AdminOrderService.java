package com.gadaaistar.admin.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;
import com.gadaaistar.persistence.order.OrderStatusEvent;
import com.gadaaistar.persistence.order.OrderStatusEventRepository;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;
import com.gadaaistar.persistence.order.OrderStatusEvent;
import com.gadaaistar.persistence.order.OrderStatusEventRepository;
import com.gadaaistar.persistence.catalog.InventoryRepository;

/**
 * Admin-side order use-cases: listing / filtering, detail, and status
 * transitions kicked off by an operator (mark shipped, mark delivered,
 * refund, etc.).
 *
 * <p>Deliberately does NOT import from {@code api-storefront} — the admin API
 * is a separate deployable and must be able to compile without the storefront
 * module. Business rules (state machine, inventory release on cancel) are
 * duplicated locally rather than shared through a common module for now.
 * Phase 4 may lift both services onto a shared {@code order-domain} module.
 */
@ApplicationScoped
public class AdminOrderService {

    private static final Logger LOG = Logger.getLogger(AdminOrderService.class);

    @Inject CheckoutOrderRepository    orders;
    @Inject OrderStatusEventRepository events;
    @Inject InventoryRepository        inventories;
    @Inject EntityManager              em;
    @Inject ShipmentMailService        shipmentMail;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    public List<CheckoutOrder> list(OrderStatus status, String search, int page, int size) {
        var pageIdx = Math.max(page, 0);
        var clamped = Math.min(Math.max(size, 1), 100);
        var sort    = Sort.by("placedAt").descending();

        // Building the where clause piece by piece — Panache supports named params.
        if (status != null && search != null && !search.isBlank()) {
            return orders.find(
                    "status = :s AND (invoiceNumber LIKE :q OR customer.email LIKE :q OR customer.companyName LIKE :q)",
                    sort,
                    Map.of("s", status, "q", "%" + search.trim() + "%"))
                    .page(Page.of(pageIdx, clamped)).list();
        }
        if (status != null) {
            return orders.find("status", sort, status)
                    .page(Page.of(pageIdx, clamped)).list();
        }
        if (search != null && !search.isBlank()) {
            return orders.find(
                    "invoiceNumber LIKE :q OR customer.email LIKE :q OR customer.companyName LIKE :q",
                    sort,
                    Map.of("q", "%" + search.trim() + "%"))
                    .page(Page.of(pageIdx, clamped)).list();
        }
        return orders.findAll(sort).page(Page.of(pageIdx, clamped)).list();
    }

    public long count(OrderStatus status, String search) {
        if (status != null && search != null && !search.isBlank()) {
            return orders.count(
                    "status = :s AND (invoiceNumber LIKE :q OR customer.email LIKE :q OR customer.companyName LIKE :q)",
                    Map.of("s", status, "q", "%" + search.trim() + "%"));
        }
        if (status != null) {
            return orders.count("status", status);
        }
        if (search != null && !search.isBlank()) {
            return orders.count(
                    "invoiceNumber LIKE :q OR customer.email LIKE :q OR customer.companyName LIKE :q",
                    Map.of("q", "%" + search.trim() + "%"));
        }
        return orders.count();
    }

    public CheckoutOrder get(String uuid) {
        return orders.findByUuid(uuid).orElseThrow(() -> new NotFoundException("Order", uuid));
    }

    // -------------------------------------------------------------------------
    // Transitions
    // -------------------------------------------------------------------------

    @Transactional
    public CheckoutOrder transition(String uuid, OrderStatus next, String note, Long adminId) {
        var order = get(uuid);
        if (!order.status.canTransitionTo(next)) {
            throw new ConflictException("ILLEGAL_ORDER_TRANSITION",
                    "Cannot move order from " + order.status + " to " + next + ".",
                    Map.of("from", order.status.name(), "to", next.name()));
        }

        // Inventory side effects for terminal states.
        if (next == OrderStatus.CANCELLED || next == OrderStatus.REFUNDED) {
            releaseReservedStockIfApplicable(order);
        }

        var evt = new OrderStatusEvent();
        evt.order      = order;
        evt.fromStatus = order.status;
        evt.toStatus   = next;
        evt.source     = OrderStatusEvent.Source.ADMIN;
        evt.actorId    = adminId;
        evt.note       = note == null || note.isBlank()
                ? "Status changed by admin."
                : note;
        events.persist(evt);
        order.status = next;

        // Terminal-state timestamps
        if (next == OrderStatus.CANCELLED && order.cancelledAt == null) {
            order.cancelledAt = Instant.now();
        }
        // paidAt is set by the payment webhook; admin doesn't override it.

        LOG.infof("Admin %s transitioned order %s: %s → %s",
                adminId, order.uuid, evt.fromStatus, next);

        // Send shipment notification email when order is marked SHIPPED.
        if (next == OrderStatus.SHIPPED && order.customer != null) {
            var carrier = note != null && !note.isBlank() ? note : null;
            shipmentMail.sendShipmentNotification(order, carrier, null);
        }

        return order;
    }

    /**
     * Reservations were consumed when the order went PAID (they became real
     * decrements). Only release them if the order is still PENDING_PAYMENT.
     */
    private void releaseReservedStockIfApplicable(CheckoutOrder order) {
        if (order.status != OrderStatus.PENDING_PAYMENT) return;
        for (var item : order.items) {
            com.gadaaistar.persistence.catalog.Inventory inv;
            if (item.variant != null) {
                inv = inventories.findByVariant(item.variant.id).orElse(null);
            } else {
                inv = inventories.findByProduct(item.product.id).orElse(null);
            }
            if (inv == null) continue;
            inv.reservedQuantity = inv.reservedQuantity.subtract(item.quantity);
            if (inv.reservedQuantity.signum() < 0) inv.reservedQuantity = BigDecimal.ZERO;
        }
    }
}
