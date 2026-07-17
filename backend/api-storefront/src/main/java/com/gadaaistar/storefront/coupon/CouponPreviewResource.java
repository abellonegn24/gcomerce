package com.gadaaistar.storefront.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.persistence.cart.Cart;
import com.gadaaistar.persistence.cart.CartRepository;
import com.gadaaistar.persistence.coupon.Coupon;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.storefront.pricing.PriceResolver;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/storefront/v1/me/coupon-preview")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Coupon Preview (Storefront)", description = "Validate a coupon code against the current cart.")
public class CouponPreviewResource {

    @Inject CouponService       couponService;
    @Inject CartRepository      carts;
    @Inject CustomerRepository  customers;
    @Inject PriceResolver       priceResolver;
    @Inject SecurityContextHolder security;

    @GET
    @Operation(summary = "Validate a coupon code and preview the discount against the current cart subtotal.")
    public CouponPreviewResponse preview(@QueryParam("code") String code) {
        var customerId = security.requireCustomer();
        if (code == null || code.isBlank()) {
            throw new ConflictException("COUPON_CODE_REQUIRED",
                    "Please enter a coupon code.", Map.of());
        }

        var customer = customers.findByIdOptional(customerId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));
        var cart = carts.findByCustomer(customerId)
                .orElseThrow(() -> new ConflictException("CART_EMPTY",
                        "Your cart is empty.", Map.of()));
        if (cart.items.isEmpty()) {
            throw new ConflictException("CART_EMPTY",
                    "Your cart is empty.", Map.of());
        }

        // Compute subtotal from cart lines (re-resolved prices, variant overrides).
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var line : cart.items) {
            var resolved = priceResolver.resolve(customer, line.product, line.quantity);
            if (line.variant != null && line.variant.priceOverride != null) {
                resolved = line.variant.priceOverride;
            }
            subtotal = subtotal.add(resolved.multiply(line.quantity));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        var result = couponService.validateAndCompute(
                code.trim().toUpperCase(), customer, subtotal, cart.currency);
        var coupon = result.coupon();

        return new CouponPreviewResponse(
                coupon.code,
                coupon.description,
                coupon.discountType,
                coupon.discountValue,
                coupon.currency,
                result.discount(),
                Boolean.TRUE.equals(coupon.freeShipping),
                subtotal,
                subtotal.subtract(result.discount()).max(BigDecimal.ZERO)
        );
    }

    public record CouponPreviewResponse(
            String code,
            String description,
            String discountType,
            BigDecimal discountValue,
            String currency,
            BigDecimal discountAmount,
            Boolean freeShipping,
            BigDecimal subtotal,
            BigDecimal discountedSubtotal
    ) {}
}
