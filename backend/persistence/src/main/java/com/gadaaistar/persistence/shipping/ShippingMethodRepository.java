package com.gadaaistar.persistence.shipping;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for {@link ShippingMethod}. The storefront quote-list
 * endpoint hits {@link #listActiveForCurrency}; the admin CRUD screen hits
 * {@link #listAll}. Ordering is stable — {@code sortOrder ASC, id ASC} — so
 * the customer sees the same picker on every page load.
 */
@ApplicationScoped
public class ShippingMethodRepository implements PanacheRepositoryBase<ShippingMethod, Long> {

    /** Admin catalogue view — every method regardless of active flag. */
    public List<ShippingMethod> listAll() {
        return list("ORDER BY sortOrder ASC, id ASC");
    }

    /** Storefront picker view — only currently-active methods in the cart's currency. */
    public List<ShippingMethod> listActiveForCurrency(String currency) {
        return list("active = ?1 AND currency = ?2 ORDER BY sortOrder ASC, id ASC",
                Boolean.TRUE, currency == null ? "" : currency.toUpperCase());
    }

    public Optional<ShippingMethod> findByIdOptional(long id) {
        return Optional.ofNullable(findById(id));
    }
}
