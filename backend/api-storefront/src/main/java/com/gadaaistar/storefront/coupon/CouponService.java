package com.gadaaistar.storefront.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

import com.gadaaistar.common.exceptions.BadRequestException;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.coupon.Coupon;
import com.gadaaistar.persistence.coupon.CouponRepository;
import com.gadaaistar.persistence.customer.Customer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Storefront-side coupon validation and discount calculation.
 *
 * <p>The flow is:
 * <ol>
 *   <li>Customer enters a coupon code at checkout.</li>
 *   <li>{@link #validate} resolves the coupon, checks validity window, active
 *       flag, usage limits, per-customer limits, and min-subtotal threshold.</li>
 *   <li>{@link #computeDiscount} calculates the discount amount based on the
 *       coupon type (PERCENT or FIXED_AMOUNT) and the order subtotal.</li>
 *   <li>{@link #validateAndCompute} does both in one call for convenience.</li>
 * </ol>
 *
 * <p>The actual redemption (creating a {@code CouponRedemption} row and
 * incrementing {@code redeemedCount}) happens inside
 * {@code OrderService.createFromCart} so it's atomic with order placement.
 */
@ApplicationScoped
public class CouponService {

    @Inject CouponRepository coupons;

    /**
     * Validate a coupon code for a given customer and subtotal. Returns the
     * resolved {@link Coupon} if valid; throws otherwise.
     *
     * @param code      the coupon code entered by the customer
     * @param customer  the customer attempting to use the coupon
     * @param subtotal  the order subtotal (sum of line nets, before shipping/tax)
     * @param currency  the cart's currency
     */
    public Coupon validate(String code, Customer customer, BigDecimal subtotal, String currency) {
        var coupon = coupons.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Coupon", code));

        var now = Instant.now();
        if (!coupon.isCurrentlyValid(now)) {
            throw new ConflictException("COUPON_EXPIRED",
                    "This coupon is no longer active or has expired.",
                    Map.of("code", code));
        }

        // Currency match
        if (!coupon.currency.equalsIgnoreCase(currency)) {
            throw new ConflictException("COUPON_CURRENCY_MISMATCH",
                    "This coupon is for " + coupon.currency + " but your cart is in " + currency + ".",
                    Map.of("couponCurrency", coupon.currency, "cartCurrency", currency));
        }

        // Min subtotal
        if (coupon.minSubtotal != null && subtotal.compareTo(coupon.minSubtotal) < 0) {
            throw new ConflictException("COUPON_MIN_SUBTOTAL_NOT_MET",
                    "This coupon requires a minimum subtotal of " + coupon.minSubtotal + " " + currency + ".",
                    Map.of("minSubtotal", coupon.minSubtotal, "subtotal", subtotal));
        }

        // Per-customer limit
        if (coupon.maxPerCustomer != null) {
            long count = coupons.countRedemptionsForCustomer(coupon.id, customer.id);
            if (count >= coupon.maxPerCustomer) {
                throw new ConflictException("COUPON_CUSTOMER_LIMIT_REACHED",
                        "You have already used this coupon the maximum number of times.",
                        Map.of("maxPerCustomer", coupon.maxPerCustomer));
            }
        }

        return coupon;
    }

    /**
     * Compute the discount amount for a validated coupon against a subtotal.
     * Does not account for free shipping — that's handled separately by the
     * caller via {@link Coupon#freeShipping}.
     */
    public BigDecimal computeDiscount(Coupon coupon, BigDecimal subtotal) {
        if (coupon.isPercent()) {
            var pct = coupon.discountValue.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            return subtotal.multiply(pct).setScale(2, RoundingMode.HALF_UP);
        } else if (coupon.isFixedAmount()) {
            // Fixed amount — capped at subtotal so we never go negative.
            return coupon.discountValue.min(subtotal).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Convenience: validate + compute in one call.
     *
     * @return a {@link DiscountResult} with the coupon and discount amount.
     */
    public DiscountResult validateAndCompute(String code, Customer customer,
                                              BigDecimal subtotal, String currency) {
        var coupon = validate(code, customer, subtotal, currency);
        var discount = computeDiscount(coupon, subtotal);
        return new DiscountResult(coupon, discount);
    }

    /**
     * Result of a successful coupon validation + discount calculation.
     *
     * @param coupon   the resolved coupon entity
     * @param discount the computed discount amount (already rounded to 2 dp)
     */
    public record DiscountResult(Coupon coupon, BigDecimal discount) {}
}
