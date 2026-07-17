package com.gadaaistar.persistence.cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.gadaaistar.persistence.customer.Customer;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * The one active cart a signed-in customer keeps between sessions. Unique on
 * customer_id — a customer never has two open carts. On checkout the cart is
 * <em>cleared</em>, not deleted, so the primary key stays stable.
 */
@Entity
@Table(name = "cart")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "customer_id", nullable = true, unique = true)
    public Customer customer;

    @Column(name = "session_key", length = 36, unique = true)
    public String sessionKey;

    @Column(nullable = false, length = 3)
    public String currency = "USD";

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("addedAt ASC, id ASC")
    public List<CartItem> items = new ArrayList<>();

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

    // -------------------------------------------------------------------------
    // Convenience
    // -------------------------------------------------------------------------

    /** Total items (units) currently in the cart. */
    public BigDecimal totalUnits() {
        var total = BigDecimal.ZERO;
        for (var it : items) total = total.add(it.quantity);
        return total;
    }

    /** Subtotal from line snapshots — Phase 3b re-resolves at checkout. */
    public BigDecimal subtotal() {
        var total = BigDecimal.ZERO;
        for (var it : items) total = total.add(it.lineTotal());
        return total;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
