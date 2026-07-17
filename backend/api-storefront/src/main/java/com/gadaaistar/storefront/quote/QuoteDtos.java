package com.gadaaistar.storefront.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.gadaaistar.common.QuoteStatus;

import jakarta.validation.constraints.Size;

/** Request / response DTOs for the storefront quote API. */
public final class QuoteDtos {
    private QuoteDtos() {}

    // -------------------------------------------------------------------------
    // Requests
    // -------------------------------------------------------------------------

    /** Request a quote from the caller's current cart. */
    public record RequestQuoteRequest(
            @Size(max = 2000) String customerNotes
    ) {}

    // -------------------------------------------------------------------------
    // Response
    // -------------------------------------------------------------------------

    public record QuoteLineDto(
            Long id,
            Long productId,
            String sku,
            String name,
            String variantSku,
            String variantName,
            BigDecimal quantity,
            BigDecimal proposedUnitPrice,
            BigDecimal lineTotal
    ) {}

    public record QuoteSummary(
            Long id,
            String uuid,
            QuoteStatus status,
            String currency,
            BigDecimal grandTotal,
            int itemCount,
            Instant createdAt,
            Instant validUntil
    ) {}

    public record QuoteDetail(
            Long id,
            String uuid,
            QuoteStatus status,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxTotal,
            BigDecimal shippingTotal,
            BigDecimal grandTotal,
            String customerNotes,
            String adminNotes,
            Instant validUntil,
            Instant createdAt,
            Instant updatedAt,
            List<QuoteLineDto> items
    ) {}
}
