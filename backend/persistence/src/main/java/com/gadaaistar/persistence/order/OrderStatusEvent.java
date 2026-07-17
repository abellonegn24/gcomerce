package com.gadaaistar.persistence.order;

import java.time.Instant;

import com.gadaaistar.common.OrderStatus;

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
import jakarta.persistence.Table;

/**
 * Append-only status-change record. Every non-cosmetic transition on
 * {@link CheckoutOrder#status} writes one of these so we have a full audit
 * trail — including who kicked it off and any operator note.
 */
@Entity
@Table(name = "order_status_event")
public class OrderStatusEvent {

    /** Origin of the transition. */
    public enum Source { SYSTEM, CUSTOMER, ADMIN, PAYMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    public CheckoutOrder order;

    /** Null only for the initial event on an order. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 24)
    public OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 24)
    public OrderStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    public Source source = Source.SYSTEM;

    /** Loose FK — customer.id if source=CUSTOMER, admin_user.id if source=ADMIN. */
    @Column(name = "actor_id")
    public Long actorId;

    @Column(length = 500)
    public String note;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    public Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
