package com.gadaaistar.persistence.coupon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Admin-managed discount coupon. Customers enter the {@code code} at checkout
 * to receive a discount on their order.
 *
 * <p>Supported discount types:
 * <ul>
 *   <li><b>PERCENT</b> — {@code discountValue} is a percentage (0–100) off the
 *       order subtotal.</li>
 *   <li><b>FIXED_AMOUNT</b> — {@code discountValue} is a flat currency amount
 *       off the order subtotal.</li>
 *   <li><b>FREE_SHIPPING</b> — when {@code freeShipping=true}, the shipping
 *       cost is zeroed (can be combined with a PERCENT/FIXED discount).</li>
 * </ul>
 *
 * <p>Enforcement:
 * <ul>
 *   <li>{@code maxRedemptions} — global cap across all customers.</li>
 *   <li>{@code maxPerCustomer} — per-customer cap, enforced by counting
 *       {@code coupon_redemption} rows.</li>
 *   <li>{@code minSubtotal} — the order subtotal must meet or exceed this.</li>
 *   <li>{@code startsAt} / {@code expiresAt} — validity window.</li>
 * </ul>
 */
@Entity
@Table(name = "coupon",
       uniqueConstraints = @UniqueConstraint(name = "uk_coupon_code", columnNames = "code"))
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 64)
    public String code;

    @Column(length = 500)
    public String description;

    /** PERCENT or FIXED_AMOUNT. */
    @Column(name = "discount_type", nullable = false, length = 20)
    public String discountType;

    /** Percentage (0–100) or fixed currency amount. */
    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    public BigDecimal discountValue;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(name = "min_subtotal", precision = 19, scale = 4)
    public BigDecimal minSubtotal;

    @Column(name = "free_shipping", nullable = false)
    public Boolean freeShipping = Boolean.FALSE;

    @Column(name = "max_redemptions")
    public Integer maxRedemptions;

    @Column(name = "max_per_customer")
    public Integer maxPerCustomer;

    @Column(name = "redeemed_count", nullable = false)
    public Integer redeemedCount = 0;

    @Column(nullable = false)
    public Boolean active = Boolean.TRUE;

    @Column(name = "starts_at")
    public Instant startsAt;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @OneToMany(mappedBy = "coupon", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("redeemedAt ASC, id ASC")
    public List<CouponRedemption> redemptions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Convenience
    // -------------------------------------------------------------------------

    public boolean isPercent() {
        return "PERCENT".equals(discountType);
    }

    public boolean isFixedAmount() {
        return "FIXED_AMOUNT".equals(discountType);
    }

    public boolean isCurrentlyValid(Instant now) {
        if (!Boolean.TRUE.equals(active)) return false;
        if (startsAt != null && now.isBefore(startsAt)) return false;
        if (expiresAt != null && now.isAfter(expiresAt)) return false;
        if (maxRedemptions != null && redeemedCount >= maxRedemptions) return false;
        return true;
    }
}
