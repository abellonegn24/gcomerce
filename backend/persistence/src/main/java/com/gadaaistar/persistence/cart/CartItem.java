package com.gadaaistar.persistence.cart;

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
 * One line in a {@link Cart}. The price is snapshotted at add-time so an
 * intermittent price change (e.g. B2B tier upgrade, promo end) doesn't
 * silently rewrite the cart total. The service layer re-resolves prices on
 * checkout.
 */
@Entity
@Table(name = "cart_item",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_cart_item_cart_product_variant",
           columnNames = {"cart_id", "product_id", "variant_id"}))
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    public Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    public Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    public ProductVariant variant;

    @Column(nullable = false, precision = 10, scale = 3)
    public BigDecimal quantity;

    /** Price per unit at the moment this line was added / last edited. */
    @Column(name = "unit_price_snap", nullable = false, precision = 19, scale = 4)
    public BigDecimal unitPriceSnap;

    @Column(nullable = false, length = 3)
    public String currency = "USD";

    @Column(name = "added_at", nullable = false, updatable = false)
    public Instant addedAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    // -------------------------------------------------------------------------

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        if (addedAt == null)   addedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Line subtotal — snapshot price × quantity. Excludes tax + shipping. */
    public BigDecimal lineTotal() {
        if (unitPriceSnap == null || quantity == null) return BigDecimal.ZERO;
        return unitPriceSnap.multiply(quantity);
    }
}
