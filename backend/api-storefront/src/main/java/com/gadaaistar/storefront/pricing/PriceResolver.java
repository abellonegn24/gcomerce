package com.gadaaistar.storefront.pricing;

import java.math.BigDecimal;

import com.gadaaistar.persistence.catalog.PriceList;
import com.gadaaistar.persistence.catalog.PriceListEntryRepository;
import com.gadaaistar.persistence.catalog.PriceListRepository;
import com.gadaaistar.persistence.catalog.Product;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Central price-resolution logic. Given a caller (customer, optional) and a
 * product + requested quantity, returns the applicable unit price.
 *
 * <p>Resolution rules (mirrors the storefront catalog surface):
 * <ol>
 *   <li>If the caller has no customer group, return {@code product.basePrice}.</li>
 *   <li>Otherwise, find the caller's group's active price list for the
 *       product's base currency.</li>
 *   <li>Within that list, pick the entry with the largest
 *       {@code min_quantity ≤ requested quantity}.</li>
 *   <li>Fallback to {@code product.basePrice} on any miss.</li>
 * </ol>
 *
 * <p>Extracted from {@code CartService} so {@code OrderService.createFromCart}
 * can re-resolve prices at checkout with the same logic — this is intentional:
 * cart snapshots are advisory, the checkout re-resolution is authoritative.
 */
@ApplicationScoped
public class PriceResolver {

    @Inject CustomerRepository       customers;
    @Inject PriceListRepository      priceLists;
    @Inject PriceListEntryRepository priceEntries;

    /** Resolve the price for the given customer. {@code null} customer falls back to base. */
    public BigDecimal resolve(Long customerId, Product product, BigDecimal quantity) {
        if (customerId == null) return product.basePrice;
        Customer customer = customers.findByIdOptional(customerId).orElse(null);
        return resolve(customer, product, quantity);
    }

    public BigDecimal resolve(Customer customer, Product product, BigDecimal quantity) {
        if (customer == null || customer.group == null) return product.basePrice;

        PriceList priceList = priceLists
                .findFirstActiveForCustomerGroup(customer.group.id, product.baseCurrency)
                .orElse(null);
        if (priceList == null) return product.basePrice;

        return priceEntries.resolvePrice(priceList.id, product.id, quantity)
                .map(e -> e.price)
                .orElse(product.basePrice);
    }
}
