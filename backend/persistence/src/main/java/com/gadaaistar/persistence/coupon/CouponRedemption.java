package com.gadaaistar.persistence.coupon;

import java.math.BigDecimal;
import java.time.Instant;

import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.order.CheckoutOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Audit row for a single coupon redemption. One per order (enforced by
 * {@code uk_redemption_order}). Snapshots the code and discount amount so
 * historical orders remain accurate even if the coupon is later modified or
 * deleted.
 */
@Entity
@Table(name = "coupon_redemption",
       uniqueConstraints = @UniqueConstraint(name = "uk_redemption_order", columnNames = "order_id"))
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    public Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    public CheckoutOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;

    @Column(nullable = false, length = 64)
    public String code;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    public BigDecimal discountAmount;

    @Column(name = "redeemed_at", nullable = false, updatable = false)
    public Instant redeemedAt;

    @PrePersist
    void onCreate() {
        if (redeemedAt == null) redeemedAt = Instant.now();
    }
}
