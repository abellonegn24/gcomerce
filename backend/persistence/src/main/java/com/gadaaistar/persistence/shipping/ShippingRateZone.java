package com.gadaaistar.persistence.shipping;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Country-specific rate override for a {@link ShippingMethod}.
 *
 * <p>When a customer's ship-to country matches {@code countryCode}, the
 * quote engine uses this zone's {@code baseCost} / {@code perKgRate} instead
 * of the method's defaults. A {@code null} column means "inherit the method's
 * default" for that component — so an admin can override only the per-kg
 * rate while keeping the base cost, or vice versa.
 *
 * <p>Transit ETA ({@code minDays} / {@code maxDays}) can also be overridden
 * per country so the picker shows accurate delivery estimates.
 */
@Entity
@Table(
    name = "shipping_rate_zone",
    uniqueConstraints = @UniqueConstraint(
        name    = "uk_zone_method_country",
        columnNames = { "shipping_method_id", "country_code" }
    )
)
public class ShippingRateZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipping_method_id", nullable = false)
    public ShippingMethod shippingMethod;

    @Column(name = "country_code", nullable = false, length = 2)
    public String countryCode;

    @Column(name = "base_cost", precision = 19, scale = 4)
    public BigDecimal baseCost;

    @Column(name = "per_kg_rate", precision = 19, scale = 4)
    public BigDecimal perKgRate;

    @Column(name = "min_days")
    public Integer minDays;

    @Column(name = "max_days")
    public Integer maxDays;

    @Column(nullable = false)
    public Boolean active = Boolean.TRUE;

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
