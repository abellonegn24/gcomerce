package com.gadaaistar.persistence.customer;

import java.math.BigDecimal;
import java.time.Instant;

import com.gadaaistar.common.PriceTier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_group")
public class CustomerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 64)
    public String code;

    @Column(nullable = false, length = 200)
    public String name;

    @Column(length = 500)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_tier", nullable = false, length = 32)
    public PriceTier priceTier = PriceTier.RETAIL;

    @Column(name = "moq_multiplier", nullable = false, precision = 6, scale = 3)
    public BigDecimal moqMultiplier = BigDecimal.ONE;

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
