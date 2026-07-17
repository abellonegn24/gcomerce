package com.gadaaistar.persistence.catalog;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductVariantRepository implements PanacheRepository<ProductVariant> {

    public Optional<ProductVariant> findBySku(String sku) {
        return find("sku", sku).firstResultOptional();
    }

    public List<ProductVariant> listByProduct(long productId) {
        return find("product.id = ?1 ORDER BY sortOrder ASC, id ASC", productId).list();
    }

    public List<ProductVariant> listActiveByProduct(long productId) {
        return find("product.id = ?1 AND active = true ORDER BY sortOrder ASC, id ASC", productId).list();
    }

    public boolean skuExists(String sku) {
        return count("sku", sku) > 0;
    }

    public long countByProduct(long productId) {
        return count("product.id", productId);
    }
}
