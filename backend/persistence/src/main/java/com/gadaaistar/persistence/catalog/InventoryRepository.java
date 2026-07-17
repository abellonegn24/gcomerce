package com.gadaaistar.persistence.catalog;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InventoryRepository implements PanacheRepository<Inventory> {

    /** Product-level inventory (variant_id IS NULL). */
    public Optional<Inventory> findByProduct(Long productId) {
        return find("product.id = ?1 AND variant.id IS NULL", productId).firstResultOptional();
    }

    /** Variant-level inventory. */
    public Optional<Inventory> findByVariant(Long variantId) {
        return find("variant.id", variantId).firstResultOptional();
    }

    /** All inventory rows for a product (product-level + all variant-level). */
    public List<Inventory> findAllByProduct(Long productId) {
        return find("product.id", productId).list();
    }
}
