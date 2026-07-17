package com.gadaaistar.persistence.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * One row per refund attempt against an order — full or partial. Both
 * success + failure land here so support has a full audit trail of what
 * was attempted, not just what worked.
 *
 * <p>See {@code V7__order_refunds.sql} for the full column-by-column
 * design rationale; the entity fields mirror that schema.
 *
 * <p>Currency is intentionally not on this row — it always equals
 * {@code order.currency}. Storing it here would create the risk of drift
 * with the parent.
 */
@Entity
@Table(name = "order_refund")
public class OrderRefund {

    /** Terminal states of an individual refund attempt. */
    public enum Status {
        /** Persisted but the gateway call has not returned yet. Rare in practice — we make the gateway call inline and land on SUCCEEDED or FAILED. */
        PENDING,
        /** Gateway confirmed the refund. Money is on its way back to the customer. */
        SUCCEEDED,
        /** Gateway rejected the refund. The row stays for the audit trail; retry is a new row. */
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Public identifier — safe to expose. */
    @Column(nullable = false, unique = true, length = 36)
    public String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    public CheckoutOrder order;

    /** Always > 0. In {@code order.currency}. */
    @Column(nullable = false, precision = 19, scale = 4)
    public BigDecimal amount;

    /** Free-text admin justification. Surfaced verbatim on the customer-facing refund receipt. */
    @Column(nullable = false, length = 1000)
    public String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public Status status = Status.PENDING;

    /** {@code CYBERSOURCE} for gateway-processed refunds, {@code MANUAL} for offline. */
    @Column(name = "gateway_provider", length = 64)
    public String gatewayProvider;

    /** CyberSource refund id (or null for MANUAL refunds). */
    @Column(name = "gateway_reference", length = 200)
    public String gatewayReference;

    /** Populated only on {@link Status#FAILED}. */
    @Column(name = "gateway_error_code", length = 64)
    public String gatewayErrorCode;

    @Column(name = "gateway_error_message", length = 1000)
    public String gatewayErrorMessage;

    /** admin_user.id — the admin who initiated the refund. Null for automated / dev flows. */
    @Column(name = "initiated_by_admin_id")
    public Long initiatedByAdminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    // -------------------------------------------------------------------------

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (uuid == null || uuid.isBlank()) uuid = UUID.randomUUID().toString();
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
