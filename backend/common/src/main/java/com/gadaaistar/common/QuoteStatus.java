package com.gadaaistar.common;

import java.util.Set;

/**
 * Lifecycle of a B2B quote.
 *
 * <pre>
 *   DRAFT → SUBMITTED → UNDER_REVIEW → SENT → ACCEPTED → CONVERTED
 *                                       └→ REJECTED
 *                                       └→ EXPIRED
 * </pre>
 *
 * <p>Transitions are enforced in the service layer.
 */
public enum QuoteStatus {
    /** Created by the customer but not yet submitted. */
    DRAFT,

    /** Customer has submitted the quote request — awaiting admin review. */
    SUBMITTED,

    /** Admin is reviewing / editing the proposed pricing. */
    UNDER_REVIEW,

    /** Admin has sent the quote back to the customer with proposed prices. */
    SENT,

    /** Customer accepted the quote — ready for order conversion. */
    ACCEPTED,

    /** Quote has been converted into a checkout_order. Terminal. */
    CONVERTED,

    /** Admin rejected the quote request. Terminal. */
    REJECTED,

    /** Quote validity period elapsed without acceptance. Terminal. */
    EXPIRED;

    public boolean isTerminal() {
        return this == CONVERTED || this == REJECTED || this == EXPIRED;
    }

    public boolean canTransitionTo(QuoteStatus next) {
        if (this == next) return false;
        if (isTerminal()) return false;
        return NEXT.getOrDefault(this, Set.of()).contains(next);
    }

    private static final java.util.EnumMap<QuoteStatus, Set<QuoteStatus>> NEXT =
            new java.util.EnumMap<>(QuoteStatus.class);

    static {
        NEXT.put(DRAFT,        Set.of(SUBMITTED, REJECTED));
        NEXT.put(SUBMITTED,    Set.of(UNDER_REVIEW, REJECTED));
        NEXT.put(UNDER_REVIEW, Set.of(SENT, REJECTED));
        NEXT.put(SENT,         Set.of(ACCEPTED, EXPIRED, REJECTED));
        NEXT.put(ACCEPTED,     Set.of(CONVERTED, EXPIRED));
    }
}
