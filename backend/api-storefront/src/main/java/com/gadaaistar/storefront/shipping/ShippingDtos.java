package com.gadaaistar.storefront.shipping;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTOs for the storefront shipping quote endpoint. The customer
 * never posts to this API — they just read the list and pick one — so
 * request DTOs are not needed.
 */
public final class ShippingDtos {

    private ShippingDtos() {}

    /**
     * One row on the checkout picker. {@code cost} is already computed for
     * the caller's current cart (weight-aware). The client should NOT
     * recompute it — the server is authoritative and will re-derive the
     * exact same value at placement time.
     */
    public record ShippingQuoteDto(
            Long id,
            String code,
            String name,
            String description,
            String currency,
            BigDecimal cost,
            Integer minDays,
            Integer maxDays,
            int sortOrder
    ) {}

    /**
     * Aggregated response — the quote list plus the total cart weight the
     * server used to compute it, so the UI can transparently show the
     * customer where the number came from ("Cost calculated for 3.20 kg").
     */
    public record ShippingQuoteListResponse(
            String currency,
            BigDecimal totalWeightKg,
            List<ShippingQuoteDto> quotes
    ) {}
}
