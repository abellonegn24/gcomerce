package com.gadaaistar.persistence.shipping;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Admin-managed shipping rate card. One row = one purchasable shipping option
 * for a specific currency. The customer-facing cost for an order is:
 * <pre>
 *     shippingCost = baseCost + (perKgRate * totalWeightKg)
 * </pre>
 * where {@code totalWeightKg} is the sum of {@code product.weightKg *
 * lineQuantity} across the cart (products with a null weight contribute 0).
 *
 * <p>Design notes:
 * <ul>
 *   <li><b>Weight-based tiered model</b>. Simple enough to explain to a
 *       customer, still useful — flat base for light parcels, per-kg
 *       surcharge scales with weight. More sophisticated banded pricing is
 *       Phase 5 if the business ever asks for it.</li>
 *   <li><b>Currency-scoped</b>. Each method targets exactly one currency.
 *       Prevents accidentally quoting an ETB per-kg rate against a USD
 *       cart.</li>
 *   <li><b>Soft on/off via {@link #active}</b>. Deleting a method that has
 *       historical orders would either cascade-corrupt the invoice (bad) or
 *       be forbidden (annoying); toggling {@code active=false} takes it off
 *       the storefront picker while leaving history intact.</li>
 * </ul>
 */
@Entity
@Table(
    name = "shipping_method",
    uniqueConstraints = @UniqueConstraint(
        name    = "uk_shipping_method_code_currency",
        columnNames = { "code", "currency" }
    )
)
public class ShippingMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Machine key. Unique per currency so DOMESTIC_ROAD can exist for USD + ETB. */
    @Column(nullable = false, length = 64)
    public String code;

    /** Customer-facing label shown on the checkout picker. */
    @Column(nullable = false, length = 200)
    public String name;

    /** Optional blurb — e.g. "3–5 business days, tracked door-to-door". */
    @Column(length = 1000)
    public String description;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(name = "base_cost", nullable = false, precision = 19, scale = 4)
    public BigDecimal baseCost = BigDecimal.ZERO;

    @Column(name = "per_kg_rate", nullable = false, precision = 19, scale = 4)
    public BigDecimal perKgRate = BigDecimal.ZERO;

    /** Lower bound of the delivery ETA in business days (nullable). */
    @Column(name = "min_days")
    public Integer minDays;

    /** Upper bound of the delivery ETA in business days (nullable). */
    @Column(name = "max_days")
    public Integer maxDays;

    /** Soft on/off — {@code false} hides it from the storefront picker. */
    @Column(nullable = false)
    public Boolean active = Boolean.TRUE;

    /** Lower value = shown first on the picker. Ties broken by id. */
    @Column(name = "sort_order", nullable = false)
    public Integer sortOrder = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

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
}
