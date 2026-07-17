package com.gadaaistar.storefront.cart;

import java.math.BigDecimal;
import java.util.List;

import com.gadaaistar.persistence.cart.Cart;
import com.gadaaistar.persistence.cart.CartItem;
import com.gadaaistar.persistence.catalog.Inventory;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CartMapper {

    /**
     * Build the full cart response. {@code currentPricesByProduct} carries the
     * freshly-resolved unit prices for each line so the response can flag
     * "priceChanged=true" without another DB pass in the mapper.
     */
    public CartDtos.CartResponse toResponse(
            Cart cart,
            java.util.Map<Long, BigDecimal> currentPricesByProduct,
            java.util.Map<Long, Inventory> inventoryByKey) {

        var lines = cart.items.stream()
                .map(it -> {
                    var key = it.variant == null ? it.product.id : -it.variant.id;
                    return toLine(it,
                            currentPricesByProduct.getOrDefault(it.product.id, it.unitPriceSnap),
                            inventoryByKey.get(key));
                })
                .toList();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalUnits = BigDecimal.ZERO;
        for (var l : lines) {
            subtotal = subtotal.add(l.lineTotal());
            totalUnits = totalUnits.add(l.quantity());
        }

        return new CartDtos.CartResponse(
                cart.id,
                cart.currency,
                lines,
                lines.size(),
                totalUnits,
                subtotal,
                cart.createdAt,
                cart.updatedAt
        );
    }

    public CartDtos.CartLine toLine(CartItem it, BigDecimal currentUnitPrice, Inventory inv) {
        var priceChanged = currentUnitPrice != null
                && it.unitPriceSnap != null
                && it.unitPriceSnap.compareTo(currentUnitPrice) != 0;

        var available = inv == null ? BigDecimal.ZERO : inv.available();
        var inStock = available.compareTo(it.quantity) >= 0;

        var variantId   = it.variant == null ? null : it.variant.id;
        var variantSku  = it.variant == null ? null : it.variant.sku;
        var variantName = it.variant == null ? null : it.variant.name;

        return new CartDtos.CartLine(
                it.id,
                it.product.id,
                variantId,
                it.product.sku,
                variantSku,
                variantName,
                it.product.slug,
                it.product.name,
                it.product.primaryImageUrl(),
                it.currency,
                it.unitPriceSnap,
                currentUnitPrice == null ? it.unitPriceSnap : currentUnitPrice,
                priceChanged,
                it.quantity,
                it.lineTotal(),
                available,
                inStock,
                it.addedAt
        );
    }

    /** Convenience for tests / edge cases where we want an empty response. */
    public CartDtos.CartResponse empty(String currency) {
        return CartDtos.CartResponse.empty(currency);
    }

    /** Kept for reference — unused. */
    @SuppressWarnings("unused")
    private static <T> List<T> nullSafe(List<T> in) {
        return in == null ? List.of() : in;
    }
}
