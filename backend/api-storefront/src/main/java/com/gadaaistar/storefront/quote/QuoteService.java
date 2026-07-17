package com.gadaaistar.storefront.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import com.gadaaistar.common.CustomerType;
import com.gadaaistar.common.QuoteStatus;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.cart.Cart;
import com.gadaaistar.persistence.cart.CartRepository;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.quote.Quote;
import com.gadaaistar.persistence.quote.QuoteItem;
import com.gadaaistar.persistence.quote.QuoteRepository;
import com.gadaaistar.storefront.pricing.PriceResolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Storefront-side quote use-cases.
 *
 * <ul>
 *   <li>{@link #createFromCart} — snapshots the caller's cart as a DRAFT quote,
 *       then immediately transitions to SUBMITTED.</li>
 *   <li>{@link #listForCustomer} / {@link #getForCustomer} — read access.</li>
 *   <li>{@link #accept} — customer accepts a SENT quote; converts to an order
 *       via {@code OrderService.createFromQuote} (wired in the next step).</li>
 * </ul>
 */
@ApplicationScoped
public class QuoteService {

    private static final Logger LOG = Logger.getLogger(QuoteService.class);

    @Inject QuoteRepository     quotes;
    @Inject CartRepository      carts;
    @Inject CustomerRepository  customers;
    @Inject PriceResolver       priceResolver;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Create a quote request from the caller's current cart. The cart must be
     * non-empty and the caller must be a B2B customer.
     */
    @Transactional
    public Quote createFromCart(long customerId, QuoteDtos.RequestQuoteRequest req) {
        var customer = customers.findByIdOptional(customerId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));

        if (customer.customerType != CustomerType.B2B) {
            throw new ConflictException("QUOTES_B2B_ONLY",
                    "Quote requests are only available for business accounts.",
                    java.util.Map.of("customerType", customer.customerType.name()));
        }

        var cart = carts.findByCustomer(customerId)
                .orElseThrow(() -> new ConflictException("CART_EMPTY",
                        "Your cart is empty.", java.util.Map.of()));
        if (cart.items.isEmpty()) {
            throw new ConflictException("CART_EMPTY", "Your cart is empty.", java.util.Map.of());
        }

        var quote = new Quote();
        quote.customer      = customer;
        quote.status        = QuoteStatus.SUBMITTED;
        quote.currency      = cart.currency;
        quote.customerNotes = req == null ? null : req.customerNotes();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (var line : cart.items) {
            var product = line.product;
            var variant = line.variant;

            // Seed the proposed price from the current resolved price.
            var resolvedPrice = priceResolver.resolve(customer, product, line.quantity);
            if (variant != null && variant.priceOverride != null) {
                resolvedPrice = variant.priceOverride;
            }

            var lineTotal = resolvedPrice.multiply(line.quantity)
                    .setScale(2, RoundingMode.HALF_UP);

            var item = new QuoteItem();
            item.quote             = quote;
            item.product           = product;
            item.variant           = variant;
            item.sku               = product.sku;
            item.name              = product.name;
            item.variantSku        = variant == null ? null : variant.sku;
            item.variantName       = variant == null ? null : variant.name;
            item.quantity          = line.quantity;
            item.proposedUnitPrice = resolvedPrice;
            item.lineTotal         = lineTotal;
            quote.items.add(item);

            subtotal = subtotal.add(lineTotal);
        }

        quote.subtotal   = subtotal;
        quote.grandTotal = subtotal; // tax + shipping computed by admin on send

        quotes.persist(quote);

        LOG.infof("Customer %d submitted quote %s with %d line(s), subtotal %s %s",
                customerId, quote.uuid, quote.items.size(), quote.subtotal, quote.currency);

        return quote;
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    public List<Quote> listForCustomer(long customerId, int page, int size) {
        return quotes.listForCustomer(customerId, page, size);
    }

    public long countForCustomer(long customerId) {
        return quotes.countForCustomer(customerId);
    }

    public Quote getForCustomer(long customerId, String uuid) {
        var q = quotes.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Quote", uuid));
        if (!q.customer.id.equals(customerId)) {
            throw new NotFoundException("Quote", uuid);
        }
        return q;
    }

    // -------------------------------------------------------------------------
    // Accept
    // -------------------------------------------------------------------------

    /**
     * Customer accepts a SENT quote. Transitions to ACCEPTED.
     * The actual order conversion is handled by the admin API calling
     * {@code convertToOrder} so the admin can set shipping + addresses.
     *
     * <p>If the quote has expired (validUntil in the past), throws 409.
     */
    @Transactional
    public Quote accept(long customerId, String uuid) {
        var q = getForCustomer(customerId, uuid);

        if (q.status != QuoteStatus.SENT) {
            throw new ConflictException("QUOTE_NOT_ACCEPTABLE",
                    "Only quotes in SENT status can be accepted.",
                    java.util.Map.of("status", q.status.name()));
        }

        if (q.validUntil != null && q.validUntil.isBefore(Instant.now())) {
            q.status = QuoteStatus.EXPIRED;
            throw new ConflictException("QUOTE_EXPIRED",
                    "This quote has expired.",
                    java.util.Map.of("validUntil", q.validUntil.toString()));
        }

        q.status = QuoteStatus.ACCEPTED;
        LOG.infof("Customer %d accepted quote %s", customerId, q.uuid);
        return q;
    }
}
