package com.gadaaistar.persistence.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.gadaaistar.common.ProductCategory;
import com.gadaaistar.common.ProductStatus;
import com.gadaaistar.persistence.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    public String sku;

    @Column(nullable = false, unique = true, length = 200)
    public String slug;

    @Column(nullable = false, length = 255)
    public String name;

    @Column(name = "short_description", length = 500)
    public String shortDescription;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "weight_kg", precision = 10, scale = 3)
    public BigDecimal weightKg;

    @Column(name = "hs_code", length = 32)
    public String hsCode;

    @Column(name = "origin_country", nullable = false, length = 2)
    public String originCountry = "ET";

    /** FK to tax_class.id; nullable — falls back to catalog default. */
    @Column(name = "tax_class_id")
    public Long taxClassId;

    @Column(name = "base_currency", nullable = false, length = 3)
    public String baseCurrency = "USD";

    @Column(name = "base_price", nullable = false, precision = 19, scale = 4)
    public BigDecimal basePrice;

    /** Internal-only — never returned in storefront responses. */
    @Column(name = "cost_price", precision = 19, scale = 4)
    public BigDecimal costPrice;

    @Column(name = "min_order_quantity", nullable = false, precision = 10, scale = 3)
    public BigDecimal minOrderQuantity = BigDecimal.ONE;

    @Column(name = "packaging_notes", length = 500)
    public String packagingNotes;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("isPrimary DESC, sortOrder ASC, id ASC")
    public List<ProductMedia> media = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    public List<ProductVariant> variants = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Convenience
    // -------------------------------------------------------------------------

    public String primaryImageUrl() {
        for (var m : media) {
            if (Boolean.TRUE.equals(m.isPrimary) && m.url != null) return m.url;
        }
        return media.isEmpty() ? null : media.get(0).url;
    }

    /** 300px card thumbnail of the primary image, falling back to the full URL. */
    public String primaryCardThumbnailUrl() {
        for (var m : media) {
            if (Boolean.TRUE.equals(m.isPrimary)) {
                if (m.cardThumbnailUrl != null) return m.cardThumbnailUrl;
                if (m.url != null) return m.url;
            }
        }
        if (!media.isEmpty()) {
            var m = media.get(0);
            if (m.cardThumbnailUrl != null) return m.cardThumbnailUrl;
            return m.url;
        }
        return null;
    }

    /** 800px detail thumbnail of the primary image, falling back to the full URL. */
    public String primaryDetailThumbnailUrl() {
        for (var m : media) {
            if (Boolean.TRUE.equals(m.isPrimary)) {
                if (m.detailThumbnailUrl != null) return m.detailThumbnailUrl;
                if (m.url != null) return m.url;
            }
        }
        if (!media.isEmpty()) {
            var m = media.get(0);
            if (m.detailThumbnailUrl != null) return m.detailThumbnailUrl;
            return m.url;
        }
        return null;
    }

    /** True if the product is safe to expose on the public storefront. */
    public boolean isPublic() {
        return status != null && status.isVisibleOnStorefront();
    }

    public Instant lastUpdated() {
        return updatedAt;
    }
}
