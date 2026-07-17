package com.gadaaistar.storefront.shipping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.persistence.cart.Cart;
import com.gadaaistar.persistence.cart.CartRepository;
import com.gadaaistar.persistence.shipping.ShippingMethod;
import com.gadaaistar.persistence.shipping.ShippingMethodRepository;
import com.gadaaistar.persistence.shipping.ShippingRateZone;
import com.gadaaistar.persistence.shipping.ShippingRateZoneRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Storefront-side shipping quote calculator.
 *
 * <p>Given the caller's active cart, this returns the list of active
 * shipping methods that apply to the cart's currency, with each method's
 * cost already computed against the cart's total weight:
 *
 * <pre>
 *     cost = baseCost + (perKgRate * totalWeightKg)
 * </pre>
 *
 * <p>Design notes:
 * <ul>
 *   <li>Products with a {@code null} {@code weightKg} contribute 0 to the
 *       weight total. This is safer than defaulting to some fictional
 *       average — a product genuinely without a weight (e.g. a digital
 *       gift certificate, once we add them) should be shipped at flat
 *       {@code baseCost} rather than have a made-up weight tacked on.</li>
 *   <li>Weight math is done in kilograms with 3 decimals of precision
 *       (matching {@code product.weight_kg}'s DECIMAL(10,3)), then the
 *       final cost is rounded to 2 dp with HALF_UP for display + storage
 *       parity with the other money columns.</li>
 *   <li>{@link #computeCost} is exposed as a static helper so
 *       {@code OrderService.createFromCart} can re-derive the exact same
 *       cost at placement time without re-implementing the formula.</li>
 * </ul>
 */
@ApplicationScoped
public class ShippingQuoteService {

    @Inject CartRepository            carts;
    @Inject ShippingMethodRepository  methods;
    @Inject ShippingRateZoneRepository zones;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Return every active shipping option in the cart's currency, priced
     * against the cart's total weight. Throws {@link ConflictException} with
     * code {@code CART_EMPTY} if the customer has no live cart yet.
     */
    public ShippingDtos.ShippingQuoteListResponse quotesForCustomer(long customerId) {
        return quotesForCustomer(customerId, null);
    }

    /**
     * Overload that accepts a destination country code. When a per-country
     * rate zone exists for a method, its baseCost / perKgRate override the
     * method defaults. Transit ETA (minDays / maxDays) can also be overridden.
     *
     * @param customerId  the signed-in customer
     * @param countryCode optional ISO-3166 alpha-2 destination country
     */
    public ShippingDtos.ShippingQuoteListResponse quotesForCustomer(long customerId, String countryCode) {
        var cart = carts.findByCustomer(customerId)
                .orElseThrow(() -> new ConflictException("CART_EMPTY",
                        "Your cart is empty.", java.util.Map.of()));
        if (cart.items.isEmpty()) {
            throw new ConflictException("CART_EMPTY",
                    "Your cart is empty.", java.util.Map.of());
        }

        var totalWeight = totalWeightKg(cart);
        var currency    = cart.currency == null ? "USD" : cart.currency.toUpperCase();
        var pool        = methods.listActiveForCurrency(currency);

        var quotes = pool.stream().map(m -> {
            var zone = zones.findActiveZone(m.id, countryCode).orElse(null);
            return new ShippingDtos.ShippingQuoteDto(
                    m.id,
                    m.code,
                    m.name,
                    m.description,
                    m.currency,
                    computeCost(m, totalWeight, zone),
                    zone != null && zone.minDays != null ? zone.minDays : m.minDays,
                    zone != null && zone.maxDays != null ? zone.maxDays : m.maxDays,
                    m.sortOrder == null ? 100 : m.sortOrder
            );
        }).toList();

        return new ShippingDtos.ShippingQuoteListResponse(currency, totalWeight, quotes);
    }

    /**
     * Sum of {@code product.weightKg * quantity} across the cart, treating a
     * null product weight as 0. Rounded to 3 decimals (matching the DB).
     */
    public static BigDecimal totalWeightKg(Cart cart) {
        BigDecimal total = BigDecimal.ZERO;
        for (var line : cart.items) {
            var w = line.product == null ? null : line.product.weightKg;
            if (line.variant != null && line.variant.weightKgOverride != null) {
                w = line.variant.weightKgOverride;
            }
            if (w == null) continue;
            total = total.add(w.multiply(line.quantity));
        }
        return total.setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Compute {@code baseCost + perKgRate * weight}, rounded HALF_UP to 2 dp.
     * Static so both the quote endpoint and the order placement path can
     * call it without instance injection.
     */
    public static BigDecimal computeCost(ShippingMethod method, BigDecimal totalWeightKg) {
        return computeCost(method, totalWeightKg, null);
    }

    /**
     * Compute cost with an optional per-country zone override. Zone fields
     * that are {@code null} inherit the method's default.
     */
    public static BigDecimal computeCost(ShippingMethod method, BigDecimal totalWeightKg,
                                          ShippingRateZone zone) {
        if (method == null) return BigDecimal.ZERO;
        var base   = method.baseCost  == null ? BigDecimal.ZERO : method.baseCost;
        var perKg  = method.perKgRate == null ? BigDecimal.ZERO : method.perKgRate;
        if (zone != null) {
            if (zone.baseCost  != null) base  = zone.baseCost;
            if (zone.perKgRate != null) perKg = zone.perKgRate;
        }
        var weight = totalWeightKg    == null ? BigDecimal.ZERO : totalWeightKg;
        return base.add(perKg.multiply(weight)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Convenience — {@link #quotesForCustomer} but only the quote rows. */
    public List<ShippingDtos.ShippingQuoteDto> quoteRowsForCustomer(long customerId) {
        return quotesForCustomer(customerId, null).quotes();
    }
}
