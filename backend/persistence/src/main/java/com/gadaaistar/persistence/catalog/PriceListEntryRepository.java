package com.gadaaistar.persistence.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PriceListEntryRepository implements PanacheRepository<PriceListEntry> {

    public List<PriceListEntry> findByPriceList(Long priceListId) {
        return list("priceList.id", Sort.by("product.name").and("minQuantity"), priceListId);
    }

    public List<PriceListEntry> findByProduct(Long productId) {
        return list("product.id = ?1", productId);
    }

    /**
     * Given a product + price list + desired quantity, return the entry whose
     * {@code minQuantity} is the largest value still &lt;= the requested qty.
     * Used by the storefront to resolve tiered wholesale prices.
     */
    public Optional<PriceListEntry> resolvePrice(Long priceListId, Long productId, BigDecimal quantity) {
        return find("priceList.id = ?1 AND product.id = ?2 AND minQuantity <= ?3",
                    Sort.by("minQuantity").descending(),
                    priceListId, productId, quantity)
                .firstResultOptional();
    }

    public long deleteByPriceList(Long priceListId) {
        return delete("priceList.id", priceListId);
    }
}
