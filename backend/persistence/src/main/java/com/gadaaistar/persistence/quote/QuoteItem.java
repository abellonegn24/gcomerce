package com.gadaaistar.persistence.quote;

import java.math.BigDecimal;
import java.time.Instant;

import com.gadaaistar.persistence.catalog.Product;
import com.gadaaistar.persistence.catalog.ProductVariant;

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
 * A line item on a {@link Quote}. The admin can adjust the
 * {@link #proposedUnitPrice} before sending the quote to the customer.
 */
@Entity
@Table(name = "quote_item",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_quote_item_quote_product_variant",
           columnNames = {"quote_id", "product_id", "variant_id"}))
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    public Quote quote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    public Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    public ProductVariant variant;

    @Column(nullable = false, length = 64)
    public String sku;

    @Column(nullable = false, length = 255)
    public String name;

    @Column(name = "variant_sku", length = 64)
    public String variantSku;

    @Column(name = "variant_name", length = 255)
    public String variantName;

    @Column(nullable = false, precision = 10, scale = 3)
    public BigDecimal quantity;

    /** Admin-proposed unit price. Initially seeded from the cart's resolved price. */
    @Column(name = "proposed_unit_price", nullable = false, precision = 19, scale = 4)
    public BigDecimal proposedUnitPrice;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    public BigDecimal lineTotal = BigDecimal.ZERO;

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

    /** {@code proposedUnitPrice × quantity}. */
    public BigDecimal computeLineTotal() {
        if (proposedUnitPrice == null || quantity == null) return BigDecimal.ZERO;
        return proposedUnitPrice.multiply(quantity);
    }
}
