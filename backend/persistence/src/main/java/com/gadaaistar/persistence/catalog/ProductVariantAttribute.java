package com.gadaaistar.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "product_variant_attribute",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_variant_attr",
           columnNames = {"variant_id", "attr_key"}))
public class ProductVariantAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    public ProductVariant variant;

    @Column(name = "attr_key", nullable = false, length = 100)
    public String attrKey;

    @Column(name = "attr_value", nullable = false, length = 255)
    public String attrValue;
}
