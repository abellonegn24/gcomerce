package com.gadaaistar.persistence.order;

import java.math.BigDecimal;

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
import jakarta.persistence.Table;

/**
 * A snapshotted line on a placed order. {@link #sku} and {@link #name} are
 * copied at order-placement time so the invoice reads correctly even if the
 * upstream product is later renamed or archived.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    public CheckoutOrder order;

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

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    public BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 3)
    public BigDecimal quantity;

    /** Effective tax rate for the line — e.g. 0.1500 for 15% domestic VAT. */
    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 4)
    public BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    public BigDecimal taxAmount = BigDecimal.ZERO;

    /** {@code (unit_price × quantity) + tax_amount}. Recomputed by the service. */
    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    public BigDecimal lineTotal = BigDecimal.ZERO;
}
