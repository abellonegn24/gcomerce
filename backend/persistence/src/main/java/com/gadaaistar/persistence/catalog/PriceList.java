package com.gadaaistar.persistence.catalog;

import java.time.Instant;
import java.time.LocalDate;

import com.gadaaistar.persistence.customer.CustomerGroup;

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

@Entity
@Table(name = "price_list")
public class PriceList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_group_id", nullable = false)
    public CustomerGroup customerGroup;

    @Column(nullable = false, length = 200)
    public String name;

    @Column(nullable = false, length = 3)
    public String currency = "USD";

    @Column(nullable = false)
    public Boolean active = Boolean.TRUE;

    @Column(name = "effective_from")
    public LocalDate effectiveFrom;

    @Column(name = "effective_to")
    public LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    // -------------------------------------------------------------------------

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

    public boolean isEffectiveNow(LocalDate on) {
        if (Boolean.FALSE.equals(active)) return false;
        if (effectiveFrom != null && on.isBefore(effectiveFrom)) return false;
        if (effectiveTo   != null && on.isAfter(effectiveTo))    return false;
        return true;
    }
}
