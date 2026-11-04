package com.gadaaistar.persistence.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerAddress;
import com.gadaaistar.persistence.shipping.ShippingMethod;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A placed order. Named {@code checkout_order} in SQL because {@code order}
 * is a reserved word. This entity is populated by {@code OrderService.createFromCart}
 * (Phase 3b) — right now the class exists so downstream code can import it.
 */
@Entity
@Table(name = "checkout_order")
public class CheckoutOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Public identifier — used in URLs / customer-visible surfaces. */
    @Column(nullable = false, unique = true, length = 36)
    public String uuid;

    /** Sequential invoice ref, e.g. {@code GI-2026-00001}. */
    @Column(name = "invoice_number", nullable = false, unique = true, length = 40)
    public String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    public OrderStatus status = OrderStatus.PENDING_PAYMENT;

    // Money -------------------------------------------------------------------
    @Column(nullable = false, length = 3)
    public String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    public BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
    public BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "shipping_total", nullable = false, precision = 19, scale = 4)
    public BigDecimal shippingTotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4)
    public BigDecimal discountTotal = BigDecimal.ZERO;

    /**
     * Cumulative refunded amount across all {@code order_refund} rows for
     * this order. Denormalised so the admin list can render "40.00 / 100.00"
     * cheaply; the refund service keeps this in sync inside the same tx
     * as the child insert.
     */
    @Column(name = "refunded_total", nullable = false, precision = 19, scale = 4)
    public BigDecimal refundedTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    public BigDecimal grandTotal = BigDecimal.ZERO;

    // Addresses ---------------------------------------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_to_address_id")
    public CustomerAddress shipToAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_to_address_id")
    public CustomerAddress billToAddress;

    // Shipping (Phase 4d) -----------------------------------------------------
    // The FK is nullable so pickup / FCA orders can still be placed; the two
    // snapshot columns preserve the customer-visible label in case the method
    // is later renamed or deleted.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_method_id")
    public ShippingMethod shippingMethod;

    @Column(name = "shipping_method_code", length = 64)
    public String shippingMethodCode;

    @Column(name = "shipping_method_name", length = 200)
    public String shippingMethodName;

    // Payment (populated on webhook return, Phase 3b) --------------------------
    @Column(name = "payment_reference", length = 120)
    public String paymentReference;

    @Column(name = "payment_provider", length = 40)
    public String paymentProvider;

    // Payment method chosen by customer at checkout: "CYBERSOURCE" | "MPGS" | null (legacy)
    @Column(name = "payment_method", length = 20)
    public String paymentMethod;

    // MPGS session (populated when paymentMethod == "MPGS") -------------------
    @Column(name = "mpgs_session_id", length = 128)
    public String mpgsSessionId;

    @Column(name = "mpgs_success_indicator", length = 128)
    public String mpgsSuccessIndicator;

    /**
     * Number of hosted-checkout redirect payloads built for this order.
     * YagoutPay requires {@code order_no} to be unique per transaction
     * request, so attempts after the first append {@code -<attempt>} to the
     * invoice number. Incremented by the payment-payload endpoint.
     */
    @Column(name = "payment_attempt", nullable = false)
    public int paymentAttempt = 0;

    // Timestamps --------------------------------------------------------------
    @Column(name = "placed_at", nullable = false, updatable = false)
    public Instant placedAt;

    @Column(name = "paid_at")
    public Instant paidAt;

    @Column(name = "cancelled_at")
    public Instant cancelledAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    // Notes -------------------------------------------------------------------
    @Column(name = "customer_notes", length = 1000)
    public String customerNotes;

    // Coupon snapshot (populated at placement when a coupon is applied) --------------
    @Column(name = "coupon_code", length = 64)
    public String couponCode;

    @Column(name = "coupon_description", length = 500)
    public String couponDescription;

    @Column(name = "coupon_discount", nullable = false, precision = 19, scale = 4)
    public BigDecimal couponDiscount = BigDecimal.ZERO;

    @Column(name = "internal_notes", length = 1000)
    public String internalNotes;

    // Children ----------------------------------------------------------------
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    public List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("occurredAt ASC, id ASC")
    public List<OrderStatusEvent> statusEvents = new ArrayList<>();

    /** Refund history. Append-only from the admin API. Ordered oldest-first. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    public List<OrderRefund> refunds = new ArrayList<>();

    // -------------------------------------------------------------------------

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        if (placedAt == null) placedAt = now;
        if (uuid == null || uuid.isBlank()) uuid = UUID.randomUUID().toString();
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
