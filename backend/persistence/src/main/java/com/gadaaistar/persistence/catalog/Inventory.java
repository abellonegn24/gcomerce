package com.gadaaistar.persistence.catalog;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    public Product product;

    /** NULL for products without variants; otherwise points to the specific variant. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    public ProductVariant variant;

    @Column(nullable = false, precision = 12, scale = 3)
    public BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reserved_quantity", nullable = false, precision = 12, scale = 3)
    public BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "restock_threshold", precision = 12, scale = 3)
    public BigDecimal restockThreshold;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    // -------------------------------------------------------------------------

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Convenience: quantity currently available for purchase. */
    public BigDecimal available() {
        return quantity.subtract(reservedQuantity);
    }

    public boolean isLow() {
        return restockThreshold != null && available().compareTo(restockThreshold) <= 0;
    }
}
