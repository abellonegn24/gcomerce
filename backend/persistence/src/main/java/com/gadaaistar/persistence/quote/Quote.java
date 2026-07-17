package com.gadaaistar.persistence.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gadaaistar.common.QuoteStatus;
import com.gadaaistar.persistence.BaseEntity;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.order.CheckoutOrder;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A B2B quote request. Created from the customer's cart, reviewed and priced
 * by an admin, then accepted by the customer — at which point it converts
 * into a {@link CheckoutOrder}.
 */
@Entity
@Table(name = "quote")
public class Quote extends BaseEntity {

    @Column(nullable = false, unique = true, length = 36)
    public String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    public QuoteStatus status = QuoteStatus.DRAFT;

    @Column(nullable = false, length = 3)
    public String currency = "USD";

    // Money -------------------------------------------------------------------
    @Column(nullable = false, precision = 19, scale = 4)
    public BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
    public BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "shipping_total", nullable = false, precision = 19, scale = 4)
    public BigDecimal shippingTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    public BigDecimal grandTotal = BigDecimal.ZERO;

    // Notes -------------------------------------------------------------------
    @Column(name = "customer_notes", length = 2000)
    public String customerNotes;

    @Column(name = "admin_notes", length = 2000)
    public String adminNotes;

    /** Quote validity deadline set by the admin when sending. */
    @Column(name = "valid_until")
    public Instant validUntil;

    /** Set when the customer accepts and the quote is converted to an order. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_order_id")
    public CheckoutOrder convertedOrder;

    // Children ----------------------------------------------------------------
    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    public List<QuoteItem> items = new ArrayList<>();

    // -------------------------------------------------------------------------

    @PrePersist
    void onCreate() {
        if (uuid == null || uuid.isBlank()) uuid = UUID.randomUUID().toString();
    }
}
