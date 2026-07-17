package com.gadaaistar.admin.quotes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.gadaaistar.common.QuoteStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin-side DTOs for quote management. */
public final class AdminQuoteDtos {
    private AdminQuoteDtos() {}

    // -------------------------------------------------------------------------
    // Requests
    // -------------------------------------------------------------------------

    /** Admin updates a single quote line's proposed price. */
    public record UpdateQuoteItemRequest(
            @NotNull BigDecimal proposedUnitPrice
    ) {}

    /** Admin sends the quote back to the customer with pricing + validity. */
    public record SendQuoteRequest(
            @Size(max = 2000) String adminNotes,
            /** Validity period in days from now (e.g. 30). */
            Integer validForDays,
            BigDecimal shippingTotal,
            BigDecimal taxTotal
    ) {}

    /** Admin rejects the quote. */
    public record RejectQuoteRequest(
            @Size(max = 2000) String adminNotes
    ) {}

    /** Admin converts an ACCEPTED quote into an order. */
    public record ConvertQuoteRequest(
            @NotNull Long shipToAddressId,
            Long billToAddressId,
            Long shippingMethodId,
            @Size(max = 1000) String internalNotes
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
            Instant validUntil,
            String customerEmail,
            String customerName
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
            Long customerId,
            String customerEmail,
            String customerName,
            List<QuoteLineDto> items
    ) {}
}
