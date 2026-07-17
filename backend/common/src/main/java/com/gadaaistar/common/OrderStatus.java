package com.gadaaistar.common;

import java.util.Set;

/**
 * Life-cycle of a {@code checkout_order} row.
 *
 * <p>Transitions are enforced in the service layer, not by DB constraint —
 * see {@code OrderService} once Phase 3b wires it up. The allowed-next-states
 * table lives inside {@link #canTransitionTo(OrderStatus)}.
 */
public enum OrderStatus {
    /** Draft — legacy slot for future "save-cart-as-order" flows. Not used today. */
    DRAFT,

    /** Placed by the customer, waiting on payment gateway confirmation. */
    PENDING_PAYMENT,

    /** Payment provider confirmed funds. Order is now billable. */
    PAID,

    /** Warehouse is picking / packing. */
    FULFILLING,

    /** Handed to the carrier — tracking number expected. */
    SHIPPED,

    /** Delivery confirmed. Terminal for happy-path orders. */
    DELIVERED,

    /** Customer or admin cancelled before shipment. Terminal. */
    CANCELLED,

    /**
     * At least one refund has been issued but the cumulative refunded
     * amount is still strictly less than {@code grand_total}. Fulfilment
     * can still proceed from here (a partial refund doesn't cancel the
     * order — think a customer who returned one line of a multi-line
     * shipment).
     */
    PARTIALLY_REFUNDED,

    /**
     * Cumulative refunds have reached {@code grand_total}. Terminal —
     * the order is financially closed out. Any further refund attempt
     * is rejected upstream.
     */
    REFUNDED;

    /** True if the status is a terminal one — no further transitions allowed. */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == REFUNDED;
    }

    /**
     * Simple state-machine gate. Returns false if {@code next} isn't a legal
     * follow-up to {@code this}. Enforce in the service layer; unit-tested there.
     *
     * <p>Note: {@link #PARTIALLY_REFUNDED} is a "soft" state — it can loop
     * back to itself as further partial refunds are recorded (the state
     * machine simply gates the initial transition, and the refund service
     * skips the transition call when we're already in that state).
     */
    public boolean canTransitionTo(OrderStatus next) {
        if (this == next)  return false;
        if (isTerminal())  return false;
        return NEXT.getOrDefault(this, Set.of()).contains(next);
    }

    private static final java.util.EnumMap<OrderStatus, Set<OrderStatus>> NEXT = new java.util.EnumMap<>(OrderStatus.class);
    static {
        NEXT.put(DRAFT,             Set.of(PENDING_PAYMENT, CANCELLED));
        NEXT.put(PENDING_PAYMENT,   Set.of(PAID, CANCELLED));
        NEXT.put(PAID,              Set.of(FULFILLING, PARTIALLY_REFUNDED, REFUNDED, CANCELLED));
        NEXT.put(FULFILLING,        Set.of(SHIPPED, PARTIALLY_REFUNDED, REFUNDED, CANCELLED));
        NEXT.put(SHIPPED,           Set.of(DELIVERED, PARTIALLY_REFUNDED, REFUNDED));
        NEXT.put(DELIVERED,         Set.of(PARTIALLY_REFUNDED, REFUNDED));
        NEXT.put(PARTIALLY_REFUNDED, Set.of(REFUNDED));
    }
}
