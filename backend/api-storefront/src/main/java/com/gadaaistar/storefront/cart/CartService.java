package com.gadaaistar.storefront.cart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gadaaistar.common.ProductStatus;
import com.gadaaistar.common.exceptions.BadRequestException;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.cart.Cart;
import com.gadaaistar.persistence.cart.CartItem;
import com.gadaaistar.persistence.cart.CartItemRepository;
import com.gadaaistar.persistence.cart.CartRepository;
import com.gadaaistar.persistence.catalog.Inventory;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.catalog.Product;
import com.gadaaistar.persistence.catalog.ProductRepository;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.storefront.pricing.PriceResolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Customer-scoped cart use-cases.
 *
 * <p>Business rules enforced here:
 * <ul>
 *   <li>Products must be {@link ProductStatus#ACTIVE} to be added. Attempts to
 *       add DRAFT / ARCHIVED products get 409 {@code PRODUCT_NOT_PURCHASABLE}.</li>
 *   <li>Requested quantity must be &ge; the product's MOQ. Failing that returns
 *       400 {@code MIN_ORDER_QUANTITY}.</li>
 *   <li>Requested quantity may not exceed available stock (quantity − reserved).
 *       Failing that returns 409 {@code INSUFFICIENT_STOCK}.</li>
 *   <li>Cart currency is locked to the first product added — mixing currencies
 *       is rejected with 409 {@code CURRENCY_MISMATCH}.</li>
 * </ul>
 *
 * <p>Prices are snapshotted at add-time; a fresh resolution runs on every read
 * so callers can surface a "price changed since you added this" indicator.
 */
@ApplicationScoped
public class CartService {

    private static final Logger LOG = Logger.getLogger(CartService.class);

    @Inject CartRepository            carts;
    @Inject CartItemRepository        cartItems;
    @Inject CustomerRepository        customers;
    @Inject ProductRepository         products;
    @Inject InventoryRepository       inventories;
    @Inject PriceResolver             priceResolver;
    @Inject CartMapper                mapper;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    /** Returns the caller's cart (creating an empty one on first read). */
    @Transactional
    public CartDtos.CartResponse getForCustomer(long customerId) {
        var cart = getOrCreate(customerId);
        return toResponse(cart);
    }

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    @Transactional
    public CartDtos.CartResponse addItem(long customerId, CartDtos.AddItemRequest req) {
        var cart = getOrCreate(customerId);
        var product = requirePurchasable(req.productId());
        // Reuses the cart's already-fetched customer to avoid a second SELECT.
        var customer = cart.customer;

        // Resolve variant if specified.
        com.gadaaistar.persistence.catalog.ProductVariant variant = null;
        if (req.variantId() != null) {
            variant = product.variants.stream()
                    .filter(v -> v.id.equals(req.variantId()))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("ProductVariant", req.variantId()));
            if (!Boolean.TRUE.equals(variant.active)) {
                throw new ConflictException("PRODUCT_NOT_PURCHASABLE",
                        "This variant is not currently available for purchase.",
                        Map.of("variantId", req.variantId()));
            }
        }

        // Cart currency is fixed after the first successful add. Mixed carts
        // are rejected here rather than silently converting on the fly.
        if (!cart.items.isEmpty() && !cart.currency.equalsIgnoreCase(product.baseCurrency)) {
            throw new ConflictException("CURRENCY_MISMATCH",
                    "Your cart is in " + cart.currency + " — this product is priced in " + product.baseCurrency + ".",
                    Map.of("cartCurrency", cart.currency, "productCurrency", product.baseCurrency));
        }
        cart.currency = product.baseCurrency;

        var existing = cartItems.findByCartProductAndVariant(cart.id, product.id,
                variant == null ? null : variant.id);
        var newQty = existing.map(l -> l.quantity.add(req.quantity())).orElse(req.quantity());

        enforceMinOrderQuantity(customer, product, newQty);
        enforceStock(product, variant, newQty);

        var resolvedPrice = resolvePriceFor(customerId, product, newQty);
        if (variant != null && variant.priceOverride != null) {
            resolvedPrice = variant.priceOverride;
        }

        if (existing.isPresent()) {
            var line = existing.get();
            line.quantity = newQty;
            line.unitPriceSnap = resolvedPrice;
            line.currency = product.baseCurrency;
        } else {
            var line = new CartItem();
            line.cart          = cart;
            line.product       = product;
            line.variant       = variant;
            line.quantity      = newQty;
            line.unitPriceSnap = resolvedPrice;
            line.currency      = product.baseCurrency;
            cart.items.add(line);
            cartItems.persist(line);
        }

        LOG.infof("Customer %d cart add: product=%d variant=%s qty=%s @ %s %s",
                customerId, product.id,
                variant == null ? "none" : String.valueOf(variant.id),
                newQty, product.baseCurrency, resolvedPrice);
        return toResponse(cart);
    }

    @Transactional
    public CartDtos.CartResponse updateItem(long customerId, long productId, Long variantId,
                                             CartDtos.UpdateItemRequest req) {
        var cart = getOrCreate(customerId);
        var line = cartItems.findByCartProductAndVariant(cart.id, productId, variantId)
                .orElseThrow(() -> new NotFoundException("CartItem", productId));

        if (req.quantity().signum() == 0) {
            // Zero-qty update is delete-in-disguise. Same 200 response shape.
            cart.items.remove(line);
            cartItems.delete(line);
            return toResponse(cart);
        }

        var product = line.product;
        var variant = line.variant;
        enforceMinOrderQuantity(cart.customer, product, req.quantity());
        enforceStock(product, variant, req.quantity());

        line.quantity = req.quantity();
        var resolvedPrice = resolvePriceFor(customerId, product, req.quantity());
        if (variant != null && variant.priceOverride != null) {
            resolvedPrice = variant.priceOverride;
        }
        line.unitPriceSnap = resolvedPrice;
        return toResponse(cart);
    }

    @Transactional
    public CartDtos.CartResponse removeItem(long customerId, long productId, Long variantId) {
        var cart = getOrCreate(customerId);
        var line = cartItems.findByCartProductAndVariant(cart.id, productId, variantId)
                .orElseThrow(() -> new NotFoundException("CartItem", productId));
        cart.items.remove(line);
        return toResponse(cart);
    }

    @Transactional
    public CartDtos.CartResponse clear(long customerId) {
        var cart = getOrCreate(customerId);
        cart.items.clear();
        return toResponse(cart);
    }

    // -------------------------------------------------------------------------
    // Guest cart (session-keyed, no auth required)
    // -------------------------------------------------------------------------

    @Transactional
    public CartDtos.CartResponse getForSession(String sessionKey) {
        var cart = getOrCreateForSession(sessionKey);
        return toResponse(cart);
    }

    @Transactional
    public CartDtos.CartResponse addItemForSession(String sessionKey, CartDtos.AddItemRequest req) {
        var cart = getOrCreateForSession(sessionKey);
        var product = requirePurchasable(req.productId());

        com.gadaaistar.persistence.catalog.ProductVariant variant = null;
        if (req.variantId() != null) {
            variant = product.variants.stream()
                    .filter(v -> v.id.equals(req.variantId()))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("ProductVariant", req.variantId()));
            if (!Boolean.TRUE.equals(variant.active)) {
                throw new ConflictException("PRODUCT_NOT_PURCHASABLE",
                        "This variant is not currently available for purchase.",
                        Map.of("variantId", req.variantId()));
            }
        }

        if (!cart.items.isEmpty() && !cart.currency.equalsIgnoreCase(product.baseCurrency)) {
            throw new ConflictException("CURRENCY_MISMATCH",
                    "Your cart is in " + cart.currency + " — this product is priced in " + product.baseCurrency + ".",
                    Map.of("cartCurrency", cart.currency, "productCurrency", product.baseCurrency));
        }
        cart.currency = product.baseCurrency;

        var existing = cartItems.findByCartProductAndVariant(cart.id, product.id,
                variant == null ? null : variant.id);
        var newQty = existing.map(l -> l.quantity.add(req.quantity())).orElse(req.quantity());

        // Guest carts use retail MOQ (no group multiplier).
        enforceMinOrderQuantity(null, product, newQty);
        enforceStock(product, variant, newQty);

        // Guest carts always get base price (no tier pricing).
        var resolvedPrice = product.basePrice;
        if (variant != null && variant.priceOverride != null) {
            resolvedPrice = variant.priceOverride;
        }

        if (existing.isPresent()) {
            var line = existing.get();
            line.quantity = newQty;
            line.unitPriceSnap = resolvedPrice;
            line.currency = product.baseCurrency;
        } else {
            var line = new CartItem();
            line.cart          = cart;
            line.product       = product;
            line.variant       = variant;
            line.quantity      = newQty;
            line.unitPriceSnap = resolvedPrice;
            line.currency      = product.baseCurrency;
            cart.items.add(line);
            cartItems.persist(line);
        }

        LOG.infof("Guest cart %s add: product=%d variant=%s qty=%s @ %s %s",
                sessionKey, product.id,
                variant == null ? "none" : String.valueOf(variant.id),
                newQty, product.baseCurrency, resolvedPrice);
        return toResponse(cart);
    }

    @Transactional
    public CartDtos.CartResponse updateItemForSession(String sessionKey, long productId, Long variantId,
                                                       CartDtos.UpdateItemRequest req) {
        var cart = getOrCreateForSession(sessionKey);
        var line = cartItems.findByCartProductAndVariant(cart.id, productId, variantId)
                .orElseThrow(() -> new NotFoundException("CartItem", productId));

        if (req.quantity().signum() == 0) {
            cart.items.remove(line);
            cartItems.delete(line);
            return toResponse(cart);
        }

        var product = line.product;
        var variant = line.variant;
        enforceMinOrderQuantity(null, product, req.quantity());
        enforceStock(product, variant, req.quantity());

        line.quantity = req.quantity();
        var resolvedPrice = product.basePrice;
        if (variant != null && variant.priceOverride != null) {
            resolvedPrice = variant.priceOverride;
        }
        line.unitPriceSnap = resolvedPrice;
        return toResponse(cart);
    }

    @Transactional
    public CartDtos.CartResponse removeItemForSession(String sessionKey, long productId, Long variantId) {
        var cart = getOrCreateForSession(sessionKey);
        var line = cartItems.findByCartProductAndVariant(cart.id, productId, variantId)
                .orElseThrow(() -> new NotFoundException("CartItem", productId));
        cart.items.remove(line);
        return toResponse(cart);
    }

    @Transactional
    public CartDtos.CartResponse clearForSession(String sessionKey) {
        var cart = getOrCreateForSession(sessionKey);
        cart.items.clear();
        return toResponse(cart);
    }

    /**
     * Merge a guest session cart into the customer's cart on login.
     * Guest lines are moved into the customer cart (combining quantities for
     * same product+variant), then the guest cart is deleted.
     */
    @Transactional
    public CartDtos.CartResponse mergeGuestCart(long customerId, String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return getForCustomer(customerId);
        }

        var guestCart = carts.findBySessionKey(sessionKey).orElse(null);
        if (guestCart == null || guestCart.items.isEmpty()) {
            // Nothing to merge — maybe delete the empty guest cart if it exists.
            if (guestCart != null) carts.delete(guestCart);
            return getForCustomer(customerId);
        }

        var customerCart = getOrCreate(customerId);

        for (var guestLine : List.copyOf(guestCart.items)) {
            var existing = cartItems.findByCartProductAndVariant(
                    customerCart.id, guestLine.product.id,
                    guestLine.variant == null ? null : guestLine.variant.id);

            if (existing.isPresent()) {
                var line = existing.get();
                line.quantity = line.quantity.add(guestLine.quantity);
                // Re-resolve price for the merged quantity using customer's tier.
                var resolvedPrice = resolvePriceFor(customerId, line.product, line.quantity);
                if (line.variant != null && line.variant.priceOverride != null) {
                    resolvedPrice = line.variant.priceOverride;
                }
                line.unitPriceSnap = resolvedPrice;
            } else {
                // Move the line to the customer cart.
                guestLine.cart = customerCart;
                var resolvedPrice = resolvePriceFor(customerId, guestLine.product, guestLine.quantity);
                if (guestLine.variant != null && guestLine.variant.priceOverride != null) {
                    resolvedPrice = guestLine.variant.priceOverride;
                }
                guestLine.unitPriceSnap = resolvedPrice;
                customerCart.items.add(guestLine);
            }
        }

        // Delete the guest cart entirely.
        guestCart.items.clear();
        carts.delete(guestCart);

        LOG.infof("Merged guest cart %s into customer %d cart", sessionKey, customerId);
        return toResponse(customerCart);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    @Transactional
    Cart getOrCreateForSession(String sessionKey) {
        return carts.findBySessionKey(sessionKey).orElseGet(() -> {
            var cart = new Cart();
            cart.sessionKey = sessionKey;
            cart.currency = "USD";
            carts.persist(cart);
            return cart;
        });
    }

    @Transactional
    Cart getOrCreate(long customerId) {
        return carts.findByCustomer(customerId).orElseGet(() -> {
            var customer = customers.findByIdOptional(customerId)
                    .orElseThrow(() -> new NotFoundException("Customer", customerId));
            var cart = new Cart();
            cart.customer = customer;
            cart.currency = customer.preferredCurrency == null ? "USD" : customer.preferredCurrency;
            carts.persist(cart);
            return cart;
        });
    }

    /** Build a fully-populated cart response with current prices + stock hints. */
    private CartDtos.CartResponse toResponse(Cart cart) {
        Map<Long, BigDecimal> currentPrices = new HashMap<>();
        Map<Long, Inventory>  invByKey  = new HashMap<>();
        Long customerId = cart.customer == null ? null : cart.customer.id;
        for (var line : cart.items) {
            currentPrices.put(line.product.id,
                    resolvePriceFor(customerId, line.product, line.quantity));
            var key = line.variant == null ? line.product.id : -line.variant.id;
            if (line.variant != null) {
                inventories.findByVariant(line.variant.id)
                        .ifPresent(inv -> invByKey.put(key, inv));
            } else {
                inventories.findByProduct(line.product.id)
                        .ifPresent(inv -> invByKey.put(key, inv));
            }
        }
        return mapper.toResponse(cart, currentPrices, invByKey);
    }

    private Product requirePurchasable(long productId) {
        var product = products.findByIdOptional(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        if (product.status != ProductStatus.ACTIVE) {
            throw new ConflictException("PRODUCT_NOT_PURCHASABLE",
                    "This product is not currently available for purchase.",
                    Map.of("productId", productId, "status", product.status.name()));
        }
        return product;
    }

    private void enforceMinOrderQuantity(Customer customer, Product product, BigDecimal quantity) {
        var productMoq  = product.minOrderQuantity == null ? BigDecimal.ONE : product.minOrderQuantity;
        var multiplier  = customer != null && customer.group != null
                                && customer.group.moqMultiplier != null
                          ? customer.group.moqMultiplier
                          : BigDecimal.ONE;

        // Rounded UP to product-MOQ scale (3) so we never leak below the
        // configured minimum through a rounding accident. E.g. product MOQ
        // 1.000 × multiplier 2.500 = 2.500 exactly, but 1.000 × 2.333 rounds
        // to 2.333 and any qty >= 2.333 is accepted.
        BigDecimal effectiveMoq = productMoq.multiply(multiplier)
                                            .setScale(3, RoundingMode.CEILING);

        if (quantity.compareTo(effectiveMoq) < 0) {
            String msg;
            if (multiplier.compareTo(BigDecimal.ONE) > 0) {
                msg = "Minimum order quantity for this product is " + effectiveMoq
                        + " (retail MOQ " + productMoq + " × your tier multiplier " + multiplier + ").";
            } else {
                msg = "Minimum order quantity for this product is " + effectiveMoq + ".";
            }
            throw new BadRequestException("MIN_ORDER_QUANTITY", msg,
                    Map.of("min",          effectiveMoq,
                           "productMin",   productMoq,
                           "groupMultiplier", multiplier,
                           "requested",    quantity));
        }
    }

    private void enforceStock(Product product, BigDecimal quantity) {
        enforceStock(product, null, quantity);
    }

    private void enforceStock(Product product,
                              com.gadaaistar.persistence.catalog.ProductVariant variant,
                              BigDecimal quantity) {
        BigDecimal available;
        if (variant != null) {
            var inv = inventories.findByVariant(variant.id).orElse(null);
            available = inv == null ? BigDecimal.ZERO : inv.available();
        } else {
            var inv = inventories.findByProduct(product.id).orElse(null);
            available = inv == null ? BigDecimal.ZERO : inv.available();
        }
        if (available.compareTo(quantity) < 0) {
            throw new ConflictException("INSUFFICIENT_STOCK",
                    "Only " + available + " unit(s) of this product are currently in stock.",
                    Map.of("available", available, "requested", quantity));
        }
    }

    /** Delegates to the shared {@link PriceResolver}. */
    private BigDecimal resolvePriceFor(long customerId, Product product, BigDecimal quantity) {
        return priceResolver.resolve(customerId, product, quantity);
    }

    /** Overload for nullable customerId (guest carts). */
    private BigDecimal resolvePriceFor(Long customerId, Product product, BigDecimal quantity) {
        return priceResolver.resolve(customerId, product, quantity);
    }
}
