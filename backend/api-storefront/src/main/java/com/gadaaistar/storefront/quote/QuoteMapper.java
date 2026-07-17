package com.gadaaistar.storefront.quote;

import java.util.List;

import com.gadaaistar.persistence.quote.Quote;
import com.gadaaistar.persistence.quote.QuoteItem;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuoteMapper {

    public QuoteDtos.QuoteSummary toSummary(Quote q) {
        return new QuoteDtos.QuoteSummary(
                q.id,
                q.uuid,
                q.status,
                q.currency,
                q.grandTotal,
                q.items == null ? 0 : q.items.size(),
                q.createdAt,
                q.validUntil
        );
    }

    public QuoteDtos.QuoteDetail toDetail(Quote q) {
        var lines = q.items == null ? List.<QuoteDtos.QuoteLineDto>of()
                : q.items.stream().map(this::toLine).toList();
        return new QuoteDtos.QuoteDetail(
                q.id,
                q.uuid,
                q.status,
                q.currency,
                q.subtotal,
                q.taxTotal,
                q.shippingTotal,
                q.grandTotal,
                q.customerNotes,
                q.adminNotes,
                q.validUntil,
                q.createdAt,
                q.updatedAt,
                lines
        );
    }

    public QuoteDtos.QuoteLineDto toLine(QuoteItem it) {
        return new QuoteDtos.QuoteLineDto(
                it.id,
                it.product == null ? null : it.product.id,
                it.sku,
                it.name,
                it.variantSku,
                it.variantName,
                it.quantity,
                it.proposedUnitPrice,
                it.lineTotal
        );
    }
}
