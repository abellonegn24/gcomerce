package com.gadaaistar.storefront.cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request / response DTOs for {@code /api/storefront/v1/cart}.
 *
 * <p>Every response includes computed totals so the caller doesn't have to
 * reduce line items itself (mirrors the pattern used on the storefront
 * catalog and admin catalog surfaces).
 */
public final class CartDtos {

    private CartDtos() {}

    // -------------------------------------------------------------------------
    // Responses
    // -------------------------------------------------------------------------

    public record CartLine(
            Long id,
            Long productId,
            Long variantId,
            String sku,
            String variantSku,
            String variantName,
            String slug,
            String name,
            String primaryImageUrl,
            String currency,
            BigDecimal unitPrice,        // snapshot at add-time
            BigDecimal currentUnitPrice, // freshly-resolved price for this caller
            /** True when the snapshot differs from the resolved current price. */
            boolean priceChanged,
            BigDecimal quantity,
            BigDecimal lineTotal,
            BigDecimal availableStock,   // for a small "N in stock" hint
            boolean inStock,
            Instant addedAt
    ) {}

    public record CartResponse(
            Long id,
            String currency,
            List<CartLine> items,
            /** Distinct SKUs currently in the cart. */
            int distinctItems,
            /** Sum of quantities across every line. */
            BigDecimal totalUnits,
            /** Sum of line totals — no tax / shipping yet (added at checkout). */
            BigDecimal subtotal,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static CartResponse empty(String currency) {
            return new CartResponse(
                    null, currency, List.of(),
                    0, BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null);
        }
    }

    // -------------------------------------------------------------------------
    // Requests
    // -------------------------------------------------------------------------

    /**
     * Add a product to the cart. If it's already there, the server bumps the
     * quantity by {@link #quantity()} rather than replacing.
     */
    public record AddItemRequest(
            @NotNull Long productId,
            Long variantId,
            @NotNull @DecimalMin(value = "0.001", message = "Quantity must be greater than 0") BigDecimal quantity
    ) {}

    /** Overwrite the quantity of an existing line. Zero deletes the line. */
    public record UpdateItemRequest(
            @NotNull @DecimalMin(value = "0.000", message = "Quantity must be zero or greater") BigDecimal quantity
    ) {}
}
