package com.gadaaistar.admin.quotes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.common.QuoteStatus;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.customer.CustomerAddress;
import com.gadaaistar.persistence.customer.CustomerAddressRepository;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.OrderItem;
import com.gadaaistar.persistence.order.OrderStatusEvent;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;
import com.gadaaistar.persistence.order.InvoiceNumberService;
import com.gadaaistar.persistence.order.OrderStatusEventRepository;
import com.gadaaistar.persistence.quote.Quote;
import com.gadaaistar.persistence.quote.QuoteItem;
import com.gadaaistar.persistence.quote.QuoteRepository;
import com.gadaaistar.persistence.shipping.ShippingMethod;
import com.gadaaistar.persistence.shipping.ShippingMethodRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Admin-side quote management: review, edit pricing, send to customer,
 * reject, and convert an accepted quote into an order.
 */
@ApplicationScoped
public class AdminQuoteService {

    private static final Logger LOG = Logger.getLogger(AdminQuoteService.class);

    @Inject QuoteRepository             quotes;
    @Inject CheckoutOrderRepository     orders;
    @Inject OrderStatusEventRepository  statusEvents;
    @Inject CustomerAddressRepository   addresses;
    @Inject ShippingMethodRepository    shippingMethods;
    @Inject InvoiceNumberService        invoiceNumbers;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    public List<Quote> list(QuoteStatus status, int page, int size) {
        if (status != null) {
            return quotes.listByStatus(status, page, size);
        }
        return quotes.listAll(page, size);
    }

    public long count(QuoteStatus status) {
        if (status != null) return quotes.countByStatus(status);
        return quotes.count();
    }

    public Quote get(String uuid) {
        return quotes.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Quote", uuid));
    }

    // -------------------------------------------------------------------------
    // Review — transition SUBMITTED → UNDER_REVIEW
    // -------------------------------------------------------------------------

    @Transactional
    public Quote startReview(String uuid) {
        var q = get(uuid);
        if (!q.status.canTransitionTo(QuoteStatus.UNDER_REVIEW)) {
            throw new ConflictException("QUOTE_NOT_REVIEWABLE",
                    "Quote must be in SUBMITTED status to start review.",
                    Map.of("status", q.status.name()));
        }
        q.status = QuoteStatus.UNDER_REVIEW;
        LOG.infof("Admin started review of quote %s", uuid);
        return q;
    }

    // -------------------------------------------------------------------------
    // Edit line — update proposed unit price
    // -------------------------------------------------------------------------

    @Transactional
    public Quote updateLine(String uuid, Long lineId,
                            AdminQuoteDtos.UpdateQuoteItemRequest req) {
        var q = get(uuid);
        if (q.status != QuoteStatus.UNDER_REVIEW && q.status != QuoteStatus.SUBMITTED) {
            throw new ConflictException("QUOTE_NOT_EDITABLE",
                    "Quote lines can only be edited while under review.",
                    Map.of("status", q.status.name()));
        }

        QuoteItem item = q.items.stream()
                .filter(i -> i.id.equals(lineId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("QuoteItem", lineId));

        if (req.proposedUnitPrice() == null || req.proposedUnitPrice().signum() < 0) {
            throw new ConflictException("INVALID_PRICE",
                    "Proposed unit price must be a non-negative number.",
                    Map.of());
        }

        item.proposedUnitPrice = req.proposedUnitPrice();
        item.lineTotal = item.proposedUnitPrice.multiply(item.quantity)
                .setScale(2, RoundingMode.HALF_UP);

        recalculateTotals(q);
        return q;
    }

    // -------------------------------------------------------------------------
    // Send — transition UNDER_REVIEW → SENT with pricing + validity
    // -------------------------------------------------------------------------

    @Transactional
    public Quote send(String uuid, AdminQuoteDtos.SendQuoteRequest req) {
        var q = get(uuid);
        if (!q.status.canTransitionTo(QuoteStatus.SENT)) {
            throw new ConflictException("QUOTE_NOT_SENDABLE",
                    "Quote must be in UNDER_REVIEW status to send.",
                    Map.of("status", q.status.name()));
        }

        if (req.adminNotes() != null) q.adminNotes = req.adminNotes();
        if (req.shippingTotal() != null) q.shippingTotal = req.shippingTotal();
        if (req.taxTotal() != null) q.taxTotal = req.taxTotal();

        int days = req.validForDays() == null ? 30 : req.validForDays();
        q.validUntil = Instant.now().plus(java.time.Duration.ofDays(days));

        recalculateTotals(q);
        q.status = QuoteStatus.SENT;

        LOG.infof("Admin sent quote %s, valid until %s, grand total %s %s",
                uuid, q.validUntil, q.grandTotal, q.currency);
        return q;
    }

    // -------------------------------------------------------------------------
    // Reject
    // -------------------------------------------------------------------------

    @Transactional
    public Quote reject(String uuid, AdminQuoteDtos.RejectQuoteRequest req) {
        var q = get(uuid);
        if (!q.status.canTransitionTo(QuoteStatus.REJECTED)) {
            throw new ConflictException("QUOTE_NOT_REJECTABLE",
                    "Quote cannot be rejected from its current state.",
                    Map.of("status", q.status.name()));
        }
        if (req != null && req.adminNotes() != null) q.adminNotes = req.adminNotes();
        q.status = QuoteStatus.REJECTED;
        LOG.infof("Admin rejected quote %s", uuid);
        return q;
    }

    // -------------------------------------------------------------------------
    // Convert ACCEPTED quote → order
    // -------------------------------------------------------------------------

    @Transactional
    public CheckoutOrder convertToOrder(String uuid, AdminQuoteDtos.ConvertQuoteRequest req) {
        var q = get(uuid);
        if (q.status != QuoteStatus.ACCEPTED) {
            throw new ConflictException("QUOTE_NOT_CONVERTIBLE",
                    "Quote must be in ACCEPTED status to convert to an order.",
                    Map.of("status", q.status.name()));
        }

        var customer = q.customer;

        // Resolve addresses
        var shipTo = addresses.findForCustomer(customer.id, req.shipToAddressId())
                .orElseThrow(() -> new NotFoundException("CustomerAddress", req.shipToAddressId()));
        CustomerAddress billTo = req.billToAddressId() == null
                ? shipTo
                : addresses.findForCustomer(customer.id, req.billToAddressId())
                        .orElseThrow(() -> new NotFoundException("CustomerAddress", req.billToAddressId()));

        // Shipping method (optional)
        ShippingMethod shippingMethod = null;
        if (req.shippingMethodId() != null) {
            shippingMethod = shippingMethods.findByIdOptional(req.shippingMethodId())
                    .orElseThrow(() -> new NotFoundException("ShippingMethod", req.shippingMethodId()));
        }

        // Build the order from the quote lines
        var order = new CheckoutOrder();
        order.customer      = customer;
        order.status        = OrderStatus.PENDING_PAYMENT;
        order.currency      = q.currency;
        order.shipToAddress = shipTo;
        order.billToAddress = billTo;
        order.customerNotes = q.customerNotes;
        order.internalNotes = req.internalNotes();
        order.placedAt      = Instant.now();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (QuoteItem qi : q.items) {
            var lineTotal = qi.proposedUnitPrice.multiply(qi.quantity)
                    .setScale(2, RoundingMode.HALF_UP);

            var item = new OrderItem();
            item.order      = order;
            item.product    = qi.product;
            item.variant    = qi.variant;
            item.sku        = qi.sku;
            item.name       = qi.name;
            item.variantSku  = qi.variantSku;
            item.variantName = qi.variantName;
            item.unitPrice  = qi.proposedUnitPrice;
            item.quantity   = qi.quantity;
            item.taxRate    = BigDecimal.ZERO; // admin-set tax is on the quote level
            item.taxAmount  = BigDecimal.ZERO;
            item.lineTotal  = lineTotal;
            order.items.add(item);

            subtotal = subtotal.add(lineTotal);
        }

        order.subtotal      = subtotal;
        order.taxTotal      = q.taxTotal != null ? q.taxTotal : BigDecimal.ZERO;
        order.shippingTotal = q.shippingTotal != null ? q.shippingTotal : BigDecimal.ZERO;
        order.discountTotal = BigDecimal.ZERO;
        order.grandTotal    = subtotal.add(order.taxTotal).add(order.shippingTotal);

        if (shippingMethod != null) {
            order.shippingMethod     = shippingMethod;
            order.shippingMethodCode = shippingMethod.code;
            order.shippingMethodName = shippingMethod.name;
        }

        // Allocate a gap-free invoice number inside this same tx.
        order.invoiceNumber = invoiceNumbers.allocate();
        orders.persist(order);

        // Initial status event
        var event = new OrderStatusEvent();
        event.order      = order;
        event.fromStatus = null;
        event.toStatus   = order.status;
        event.source     = OrderStatusEvent.Source.ADMIN;
        event.note       = "Order created from quote " + q.uuid;
        statusEvents.persist(event);

        // Link the quote to the order
        q.convertedOrder = order;
        q.status         = QuoteStatus.CONVERTED;

        LOG.infof("Admin converted quote %s to order %s (invoice %s, %s %s)",
                uuid, order.uuid, order.invoiceNumber, order.grandTotal, order.currency);

        return order;
    }

    // -------------------------------------------------------------------------

    private void recalculateTotals(Quote q) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (QuoteItem item : q.items) {
            item.lineTotal = item.proposedUnitPrice.multiply(item.quantity)
                    .setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(item.lineTotal);
        }
        q.subtotal   = subtotal;
        q.grandTotal = subtotal
                .add(q.taxTotal != null ? q.taxTotal : BigDecimal.ZERO)
                .add(q.shippingTotal != null ? q.shippingTotal : BigDecimal.ZERO);
    }
}
