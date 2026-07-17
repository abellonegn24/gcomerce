package com.gadaaistar.persistence.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.gadaaistar.persistence.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_variant")
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    public Product product;

    @Column(nullable = false, unique = true, length = 64)
    public String sku;

    @Column(nullable = false, length = 255)
    public String name;

    /** NULL means use product.basePrice. */
    @Column(name = "price_override", precision = 19, scale = 4)
    public BigDecimal priceOverride;

    /** NULL means use product.weightKg. */
    @Column(name = "weight_kg_override", precision = 10, scale = 3)
    public BigDecimal weightKgOverride;

    @Column(nullable = false)
    public Boolean active = Boolean.TRUE;

    @Column(name = "sort_order", nullable = false)
    public Integer sortOrder = 0;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("attrKey ASC")
    public List<ProductVariantAttribute> attributes = new ArrayList<>();

    public BigDecimal effectivePrice(BigDecimal fallback) {
        return priceOverride != null ? priceOverride : fallback;
    }

    public BigDecimal effectiveWeightKg(BigDecimal fallback) {
        return weightKgOverride != null ? weightKgOverride : fallback;
    }
}
