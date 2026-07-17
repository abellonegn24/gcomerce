package com.gadaaistar.storefront.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.common.ProductStatus;
import com.gadaaistar.common.exceptions.BadRequestException;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.cart.CartItemRepository;
import com.gadaaistar.persistence.cart.CartRepository;
import com.gadaaistar.persistence.catalog.Inventory;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.coupon.Coupon;
import com.gadaaistar.persistence.coupon.CouponRedemption;
import com.gadaaistar.persistence.coupon.CouponRepository;
import com.gadaaistar.persistence.customer.CustomerAddress;
import com.gadaaistar.persistence.customer.CustomerAddressRepository;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;
import com.gadaaistar.persistence.order.OrderItem;
import com.gadaaistar.persistence.order.InvoiceNumberService;
import com.gadaaistar.persistence.order.OrderStatusEvent;
import com.gadaaistar.persistence.order.OrderStatusEventRepository;
import com.gadaaistar.persistence.shipping.ShippingMethod;
import com.gadaaistar.persistence.shipping.ShippingMethodRepository;
import com.gadaaistar.persistence.shipping.ShippingRateZone;
import com.gadaaistar.persistence.shipping.ShippingRateZoneRepository;
import com.gadaaistar.storefront.coupon.CouponService;
import com.gadaaistar.storefront.mail.MailService;
import com.gadaaistar.storefront.payment.MpgsService;
import com.gadaaistar.storefront.pricing.PriceResolver;
import com.gadaaistar.storefront.shipping.ShippingQuoteService;
import com.gadaaistar.storefront.tax.TaxService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Core checkout use-case. Turns a customer's cart into a placed
 * {@link CheckoutOrder} with snapshotted lines, tax computed from the ship-to
 * country, an atomically-allocated invoice number, and an initial status
 * event. Payment initiation is a separate step handled by
 * {@code CyberSourceService} — this service leaves the order in
 * {@link OrderStatus#PENDING_PAYMENT}.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>All lines are re-priced at checkout via {@link PriceResolver} — the
 *       cart's snapshot prices are ignored (they are UI-hint only).</li>
 *   <li>Stock is re-validated per line at checkout time; if any line is
 *       over-committed the transaction rolls back with 409 {@code INSUFFICIENT_STOCK}.</li>
 *   <li>Inventory is <em>reserved</em> (not decremented) on placement — the
 *       actual decrement happens on payment confirmation in Phase 3b's
 *       CyberSource webhook. If the customer abandons payment the reservation
 *       is released by a scheduled sweeper (Phase 3c/4).</li>
 *   <li>Invoice numbers are gap-free (see {@link InvoiceNumberService}).</li>
 * </ul>
 */
@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    @Inject CartRepository              carts;
    @Inject CartItemRepository          cartItems;
    @Inject CustomerRepository          customers;
    @Inject CustomerAddressRepository   addresses;
    @Inject InventoryRepository         inventories;
    @Inject CheckoutOrderRepository     orders;
    @Inject OrderStatusEventRepository  statusEvents;
    @Inject ShippingMethodRepository    shippingMethods;
    @Inject ShippingRateZoneRepository   shippingZones;
    @Inject PriceResolver               priceResolver;
    @Inject TaxService                  taxService;
    @Inject InvoiceNumberService        invoiceNumbers;
    @Inject CouponService               couponService;
    @Inject CouponRepository            couponRepo;
    @Inject MailService                 mailService;
    @Inject MpgsService                 mpgsService;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    public List<CheckoutOrder> listForCustomer(long customerId, int page, int size) {
        return orders.listForCustomer(customerId, page, size);
    }

    public long countForCustomer(long customerId) {
        return orders.countForCustomer(customerId);
    }

    public CheckoutOrder getForCustomer(long customerId, String uuid) {
        var order = orders.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Order", uuid));
        if (order.customer == null || order.customer.id == null
            || order.customer.id.longValue() != customerId) {
            // Same shape as 404 to avoid leaking that a foreign order exists.
            throw new NotFoundException("Order", uuid);
        }
        return order;
    }

    /**
     * Public order lookup by UUID + email — used by the no-auth order tracking
     * page. Returns the order only if the email matches the customer's email
     * on file. Throws 404 (same shape as unknown) on mismatch to prevent
     * enumeration.
     */
    public CheckoutOrder getForPublicTracking(String uuid, String email) {
        var order = orders.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Order", uuid));
        if (order.customer == null || order.customer.email == null
            || !order.customer.email.equalsIgnoreCase(email.trim())) {
            throw new NotFoundException("Order", uuid);
        }
        return order;
    }

    // -------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------

    /**
     * Materialise the cart as a placed order. Runs entirely inside one
     * transaction: any failure rolls back inventory reservations too.
     */
    @Transactional
    public CheckoutOrder createFromCart(long customerId, OrderDtos.PlaceOrderRequest req) {
        var customer = customers.findByIdOptional(customerId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));

        var cart = carts.findByCustomer(customerId)
                .orElseThrow(() -> new ConflictException("CART_EMPTY",
                        "Your cart is empty.", Map.of()));
        if (cart.items.isEmpty()) {
            throw new ConflictException("CART_EMPTY", "Your cart is empty.", Map.of());
        }

        // Addresses — must belong to the caller.
        var shipTo = addresses.findForCustomer(customerId, req.shipToAddressId())
                .orElseThrow(() -> new NotFoundException("CustomerAddress", req.shipToAddressId()));
        CustomerAddress billTo = req.billToAddressId() == null
                ? shipTo
                : addresses.findForCustomer(customerId, req.billToAddressId())
                        .orElseThrow(() -> new NotFoundException("CustomerAddress", req.billToAddressId()));

        // Tax rule for the ship-to country. Zero-rated for exports, 15% for ET.
        var taxDecision = taxService.resolveFor(shipTo.countryCode);
        var taxRateFraction = taxDecision.ratePercent()
                .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);

        // Shipping method (optional). Resolve up-front so a bad id fails the
        // request before we start touching inventory reservations.
        ShippingMethod shippingMethod = null;
        if (req.shippingMethodId() != null) {
            shippingMethod = shippingMethods.findByIdOptional(req.shippingMethodId())
                    .orElseThrow(() -> new NotFoundException("ShippingMethod", req.shippingMethodId()));
            if (!Boolean.TRUE.equals(shippingMethod.active)) {
                throw new ConflictException("SHIPPING_METHOD_INACTIVE",
                        "The selected shipping method is no longer available.",
                        Map.of("shippingMethodId", req.shippingMethodId()));
            }
            if (!shippingMethod.currency.equalsIgnoreCase(cart.currency)) {
                throw new ConflictException("SHIPPING_METHOD_CURRENCY_MISMATCH",
                        "The selected shipping method is priced in " + shippingMethod.currency
                                + " but your cart is in " + cart.currency + ".",
                        Map.of("shippingMethodId", req.shippingMethodId(),
                               "methodCurrency", shippingMethod.currency,
                               "cartCurrency", cart.currency));
            }
        }

        var order = new CheckoutOrder();
        order.customer      = customer;
        order.status        = OrderStatus.PENDING_PAYMENT;
        order.currency      = cart.currency;
        order.shipToAddress = shipTo;
        order.billToAddress = billTo;
        order.customerNotes = req.customerNotes();
        order.placedAt      = Instant.now();
        // Store the customer's chosen payment method (defaults to CYBERSOURCE).
        order.paymentMethod = req.paymentMethod() == null || req.paymentMethod().isBlank()
                ? "CYBERSOURCE" : req.paymentMethod().toUpperCase();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        for (var line : cart.items) {
            var product = line.product;
            if (product.status != ProductStatus.ACTIVE) {
                throw new ConflictException("PRODUCT_NOT_PURCHASABLE",
                        "Product '" + product.name + "' is no longer available.",
                        Map.of("productId", product.id, "status", product.status.name()));
            }

            // Re-resolve price at checkout — the cart snapshot is advisory only.
            var resolvedUnit = priceResolver.resolve(customer, product, line.quantity);
            var variant = line.variant;
            if (variant != null && variant.priceOverride != null) {
                resolvedUnit = variant.priceOverride;
            }
            var lineNet      = resolvedUnit.multiply(line.quantity);
            var lineTax      = lineNet.multiply(taxRateFraction).setScale(2, RoundingMode.HALF_UP);
            lineNet          = lineNet.setScale(2, RoundingMode.HALF_UP);
            var lineTotal    = lineNet.add(lineTax);

            // Re-validate + reserve stock inside the same tx so a concurrent
            // reservation can't oversell. Use variant-level inventory when a
            // variant is selected, product-level otherwise.
            Inventory inv;
            if (variant != null) {
                inv = inventories.findByVariant(variant.id)
                        .orElseThrow(() -> new ConflictException("INSUFFICIENT_STOCK",
                                "Inventory record missing for variant '" + variant.name + "'.",
                                Map.of("variantId", variant.id)));
            } else {
                inv = inventories.findByProduct(product.id)
                        .orElseThrow(() -> new ConflictException("INSUFFICIENT_STOCK",
                                "Inventory record missing for product '" + product.name + "'.",
                                Map.of("productId", product.id)));
            }
            if (inv.available().compareTo(line.quantity) < 0) {
                throw new ConflictException("INSUFFICIENT_STOCK",
                        "Only " + inv.available() + " unit(s) of '" + product.name + "' are in stock.",
                        Map.of("productId", product.id, "available", inv.available(),
                               "requested", line.quantity));
            }
            inv.reservedQuantity = inv.reservedQuantity.add(line.quantity);

            var item = new OrderItem();
            item.order      = order;
            item.product    = product;
            item.variant    = variant;
            item.sku        = product.sku;
            item.name       = product.name;
            item.variantSku  = variant == null ? null : variant.sku;
            item.variantName = variant == null ? null : variant.name;
            item.unitPrice  = resolvedUnit;
            item.quantity   = line.quantity;
            item.taxRate    = taxRateFraction;
            item.taxAmount  = lineTax;
            item.lineTotal  = lineTotal;
            order.items.add(item);

            subtotal = subtotal.add(lineNet);
            taxTotal = taxTotal.add(lineTax);
        }

        // Shipping cost — server-authoritative. Uses the same weight formula
        // as the /shipping/quotes endpoint so the customer never sees a
        // different number at placement than they picked from the picker.
        BigDecimal shippingTotal = BigDecimal.ZERO;
        if (shippingMethod != null) {
            var weight = ShippingQuoteService.totalWeightKg(cart);
            var zone = shippingZones.findActiveZone(shippingMethod.id, shipTo.countryCode).orElse(null);
            shippingTotal = ShippingQuoteService.computeCost(shippingMethod, weight, zone);
            order.shippingMethod     = shippingMethod;
            order.shippingMethodCode = shippingMethod.code;
            order.shippingMethodName = shippingMethod.name;
        }

        // Coupon — validate + compute discount against subtotal.
        BigDecimal couponDiscount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;
        if (req.couponCode() != null && !req.couponCode().isBlank()) {
            var result = couponService.validateAndCompute(
                    req.couponCode().trim().toUpperCase(), customer, subtotal, cart.currency);
            appliedCoupon  = result.coupon();
            couponDiscount = result.discount();

            // Free shipping override.
            if (Boolean.TRUE.equals(appliedCoupon.freeShipping)) {
                shippingTotal = BigDecimal.ZERO;
            }

            order.couponCode        = appliedCoupon.code;
            order.couponDescription = appliedCoupon.description;
            order.couponDiscount    = couponDiscount;
        }

        order.subtotal       = subtotal;
        order.taxTotal       = taxTotal;
        order.shippingTotal  = shippingTotal;
        order.discountTotal  = couponDiscount;
        order.grandTotal     = subtotal.add(taxTotal).add(shippingTotal).subtract(couponDiscount);
        if (order.grandTotal.signum() < 0) order.grandTotal = BigDecimal.ZERO;

        // Allocate an invoice number under FOR UPDATE — inside this same tx.
        order.invoiceNumber = invoiceNumbers.allocate();

        orders.persist(order);

        // Initial status event — recorded so the order history shows placement.
        var event = new OrderStatusEvent();
        event.order      = order;
        event.fromStatus = null;
        event.toStatus   = order.status;
        event.source     = OrderStatusEvent.Source.CUSTOMER;
        event.actorId    = customerId;
        event.note       = "Order placed.";
        statusEvents.persist(event);

        // Coupon redemption — increment counter + create audit row.
        if (appliedCoupon != null) {
            appliedCoupon.redeemedCount = (appliedCoupon.redeemedCount == null ? 0 : appliedCoupon.redeemedCount) + 1;
            couponRepo.persist(appliedCoupon);

            var redemption = new CouponRedemption();
            redemption.coupon         = appliedCoupon;
            redemption.order          = order;
            redemption.customer       = customer;
            redemption.code           = appliedCoupon.code;
            redemption.discountAmount = couponDiscount;
            couponRepo.getEntityManager().persist(redemption);
        }

        // Cart is emptied — a fresh empty one is auto-created next request.
        cartItems.deleteByCart(cart.id);
        cart.items.clear();

        LOG.infof("Customer %d placed order %s (invoice %s, %s %s, tax %s, ship %s via %s, ship-to %s)",
                customerId, order.uuid, order.invoiceNumber,
                order.grandTotal, order.currency,
                taxTotal,
                shippingTotal,
                shippingMethod == null ? "NONE" : shippingMethod.code,
                shipTo.countryCode);

        // If the customer chose MPGS, create the checkout session now.
        // The session id + success indicator are stored on the order so the
        // payment-payload endpoint can hand them to the frontend and the
        // webhook handler can verify the resultIndicator match.
        if ("MPGS".equals(order.paymentMethod)) {
            var session = mpgsService.createCheckoutSession(order);
            order.mpgsSessionId       = session.sessionId();
            order.mpgsSuccessIndicator = session.successIndicator();
        }

        // Fire-and-forget order confirmation email. Failures are logged but
        // never propagate — the order is already placed.
        var displayName = customer.firstName != null && !customer.firstName.isBlank()
                ? customer.firstName
                : (customer.companyName != null ? customer.companyName : "there");
        var grandTotalStr = order.grandTotal.setScale(2, RoundingMode.HALF_UP).toPlainString();
        mailService.sendOrderConfirmation(customer.email, displayName,
                order.invoiceNumber, order.currency, grandTotalStr, order.uuid)
                .exceptionally(err -> { LOG.errorf(err, "Failed to send order confirmation to %s", customer.email); return null; });

        return order;
    }

    // -------------------------------------------------------------------------
    // Cancellation (customer-initiated)
    // -------------------------------------------------------------------------

    /**
     * Customer cancels a PENDING_PAYMENT order. Later phases will allow
     * cancellation of PAID / FULFILLING orders too, gated by admin approval —
     * for MVP the customer can only self-cancel before payment lands.
     */
    @Transactional
    public CheckoutOrder cancelByCustomer(long customerId, String uuid, String reason) {
        var order = getForCustomer(customerId, uuid);
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("CANNOT_CANCEL",
                    "Only orders awaiting payment can be cancelled from your account.",
                    Map.of("status", order.status.name()));
        }
        transition(order, OrderStatus.CANCELLED, OrderStatusEvent.Source.CUSTOMER, customerId,
                reason == null || reason.isBlank() ? "Cancelled by customer." : reason);
        releaseReservedStock(order);
        order.cancelledAt = Instant.now();

        sendCancellationEmail(order);
        return order;
    }

    // -------------------------------------------------------------------------
    // System-initiated cancellation (abandoned-order sweeper)
    // -------------------------------------------------------------------------

    /**
     * Return the row-ids of {@code PENDING_PAYMENT} orders that were placed
     * strictly before {@code olderThan}. The sweeper calls this in its own tx
     * and then iterates the id list, giving each id its own
     * {@code REQUIRES_NEW} write tx via {@link #cancelAbandoned(long, String)}.
     * We deliberately return ids (not entities) so nothing lazy is dragged
     * across the outer tx boundary.
     */
    @Transactional
    public List<Long> findAbandonedIds(Instant olderThan, int limit) {
        return orders.listStuckSince(OrderStatus.PENDING_PAYMENT, olderThan, limit)
                .stream()
                .map(o -> o.id)
                .toList();
    }

    /**
     * Cancel a PENDING_PAYMENT order on behalf of the system because the
     * customer never completed the CyberSource redirect. Releases the
     * reservation and emits a SYSTEM-source event so the timeline shows the
     * auto-cancel with an audit trail distinct from a customer-initiated one.
     *
     * <p>Runs in its own transaction so a single failure inside the sweeper's
     * loop doesn't roll back everyone else's cancellations.
     *
     * @return {@code true} if the order was actually cancelled by this call,
     *         {@code false} if it had already moved on (e.g. a payment webhook
     *         won the race) or if the id no longer exists.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean cancelAbandoned(long orderId, String reason) {
        var order = orders.findByIdOptional(orderId).orElse(null);
        if (order == null) return false;
        // Re-check status inside the tx — a webhook could have flipped this to
        // PAID between the sweeper's SELECT and now.
        if (order.status != OrderStatus.PENDING_PAYMENT) return false;
        transition(order, OrderStatus.CANCELLED, OrderStatusEvent.Source.SYSTEM, null,
                reason == null || reason.isBlank() ? "Auto-cancelled: payment not completed." : reason);
        releaseReservedStock(order);
        order.cancelledAt = Instant.now();

        sendCancellationEmail(order);
        return true;
    }

    // -------------------------------------------------------------------------
    // Transitions (also called by CyberSource webhook + admin)
    // -------------------------------------------------------------------------

    /**
     * Emit a status transition + audit event. Callers must have already opened
     * a transaction. Enforces the state machine defined on {@link OrderStatus}.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public void transition(CheckoutOrder order, OrderStatus next,
                            OrderStatusEvent.Source source, Long actorId, String note) {
        if (!order.status.canTransitionTo(next)) {
            throw new ConflictException("ILLEGAL_ORDER_TRANSITION",
                    "Cannot move order from " + order.status + " to " + next + ".",
                    Map.of("from", order.status.name(), "to", next.name()));
        }
        var evt = new OrderStatusEvent();
        evt.order      = order;
        evt.fromStatus = order.status;
        evt.toStatus   = next;
        evt.source     = source;
        evt.actorId    = actorId;
        evt.note       = note;
        statusEvents.persist(evt);
        order.status = next;
    }

    /** Fire-and-forget cancellation email. Failures are logged, never propagated. */
    private void sendCancellationEmail(CheckoutOrder order) {
        var c = order.customer;
        var displayName = c.firstName != null && !c.firstName.isBlank()
                ? c.firstName
                : (c.companyName != null ? c.companyName : "there");
        mailService.sendOrderCancelled(c.email, displayName, order.invoiceNumber)
                .exceptionally(err -> { LOG.errorf(err, "Failed to send cancellation email to %s", c.email); return null; });
    }

    /** Release any inventory this order still has reserved. */
    private void releaseReservedStock(CheckoutOrder order) {
        for (var item : order.items) {
            Inventory inv;
            if (item.variant != null) {
                inv = inventories.findByVariant(item.variant.id).orElse(null);
            } else {
                inv = inventories.findByProduct(item.product.id).orElse(null);
            }
            if (inv == null) continue;
            inv.reservedQuantity = inv.reservedQuantity.subtract(item.quantity);
            if (inv.reservedQuantity.signum() < 0) inv.reservedQuantity = BigDecimal.ZERO;
        }
    }

    /**
     * Called by {@code CyberSourceWebhookResource} when the gateway confirms
     * payment: convert reservation into a real decrement, mark the order PAID.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public void markPaid(CheckoutOrder order, String paymentReference, String paymentProvider) {
        for (var item : order.items) {
            Inventory inv;
            if (item.variant != null) {
                inv = inventories.findByVariant(item.variant.id).orElse(null);
            } else {
                inv = inventories.findByProduct(item.product.id).orElse(null);
            }
            if (inv == null) continue;
            inv.reservedQuantity = inv.reservedQuantity.subtract(item.quantity);
            inv.quantity         = inv.quantity.subtract(item.quantity);
            if (inv.reservedQuantity.signum() < 0) inv.reservedQuantity = BigDecimal.ZERO;
            if (inv.quantity.signum() < 0)         inv.quantity         = BigDecimal.ZERO;
        }
        order.paymentReference = paymentReference;
        order.paymentProvider  = paymentProvider;
        order.paidAt           = Instant.now();
        transition(order, OrderStatus.PAID, OrderStatusEvent.Source.PAYMENT, null,
                "Payment confirmed by " + paymentProvider + ".");
    }

    /** Called by the webhook when the gateway rejects / cancels the payment. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void markPaymentFailed(CheckoutOrder order, String reason) {
        releaseReservedStock(order);
        order.cancelledAt = Instant.now();
        transition(order, OrderStatus.CANCELLED, OrderStatusEvent.Source.PAYMENT, null,
                reason == null || reason.isBlank() ? "Payment declined." : reason);
    }

}
