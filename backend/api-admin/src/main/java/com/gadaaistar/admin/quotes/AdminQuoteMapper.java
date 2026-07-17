package com.gadaaistar.admin.quotes;

import java.util.List;

import com.gadaaistar.persistence.quote.Quote;
import com.gadaaistar.persistence.quote.QuoteItem;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AdminQuoteMapper {

    public AdminQuoteDtos.QuoteSummary toSummary(Quote q) {
        var name = buildCustomerName(q);
        return new AdminQuoteDtos.QuoteSummary(
                q.id,
                q.uuid,
                q.status,
                q.currency,
                q.grandTotal,
                q.items == null ? 0 : q.items.size(),
                q.createdAt,
                q.validUntil,
                q.customer == null ? null : q.customer.email,
                name
        );
    }

    public AdminQuoteDtos.QuoteDetail toDetail(Quote q) {
        var lines = q.items == null ? List.<AdminQuoteDtos.QuoteLineDto>of()
                : q.items.stream().map(this::toLine).toList();
        var name = buildCustomerName(q);
        return new AdminQuoteDtos.QuoteDetail(
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
                q.customer == null ? null : q.customer.id,
                q.customer == null ? null : q.customer.email,
                name,
                lines
        );
    }

    public AdminQuoteDtos.QuoteLineDto toLine(QuoteItem it) {
        return new AdminQuoteDtos.QuoteLineDto(
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

    private String buildCustomerName(Quote q) {
        if (q.customer == null) return null;
        var first = q.customer.firstName;
        var last  = q.customer.lastName;
        if (first != null && !first.isBlank() && last != null && !last.isBlank())
            return first + " " + last;
        if (first != null && !first.isBlank()) return first;
        if (q.customer.companyName != null && !q.customer.companyName.isBlank())
            return q.customer.companyName;
        return q.customer.email;
    }
}
