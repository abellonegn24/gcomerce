package com.gadaaistar.common;

public enum CustomerStatus {
    /** Awaiting email confirmation (typical B2C signup). */
    PENDING_VERIFICATION,
    /** B2B signup awaiting admin approval of the business license. */
    PENDING_APPROVAL,
    /** Fully active account (can browse, order, pay). */
    ACTIVE,
    /** Temporarily suspended by admin (login blocked). */
    SUSPENDED,
    /** Account closed / soft-deleted; PII scheduled for erasure per data-retention policy. */
    DEACTIVATED;

    public boolean canLogin() {
        return this == ACTIVE || this == PENDING_VERIFICATION || this == PENDING_APPROVAL;
    }

    public boolean canPlaceOrders() {
        return this == ACTIVE;
    }
}
