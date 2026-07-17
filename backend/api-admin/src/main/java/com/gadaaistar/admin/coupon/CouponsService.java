package com.gadaaistar.admin.coupon;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.gadaaistar.common.exceptions.BadRequestException;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.coupon.Coupon;
import com.gadaaistar.persistence.coupon.CouponRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CouponsService {

    @Inject CouponRepository coupons;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    public CouponDtos.CouponListResponse list(int page, int size) {
        var rows = coupons.listAll(page, size);
        var total = coupons.countAll();
        var dtos = rows.stream().map(this::toDto).toList();
        return new CouponDtos.CouponListResponse(dtos, total, page, size);
    }

    public CouponDtos.CouponDto get(long id) {
        return toDto(findOrThrow(id));
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    public CouponDtos.CouponDto create(CouponDtos.CreateCouponRequest req) {
        if (coupons.codeExists(req.code())) {
            throw new ConflictException("COUPON_CODE_EXISTS",
                    "Coupon code '" + req.code() + "' is already in use.",
                    Map.of("code", req.code()));
        }

        validateDiscountValue(req.discountType(), req.discountValue());

        var c = new Coupon();
        c.code            = req.code();
        c.description     = req.description();
        c.discountType    = req.discountType();
        c.discountValue   = req.discountValue();
        c.currency        = req.currency().toUpperCase();
        c.minSubtotal     = req.minSubtotal();
        c.freeShipping    = req.freeShipping() != null ? req.freeShipping() : Boolean.FALSE;
        c.maxRedemptions  = req.maxRedemptions();
        c.maxPerCustomer  = req.maxPerCustomer();
        c.active          = req.active() != null ? req.active() : Boolean.TRUE;
        c.startsAt        = req.startsAt();
        c.expiresAt       = req.expiresAt();
        c.redeemedCount   = 0;

        coupons.persist(c);
        return toDto(c);
    }

    // -------------------------------------------------------------------------
    // Update (PATCH)
    // -------------------------------------------------------------------------

    @Transactional
    public CouponDtos.CouponDto update(long id, CouponDtos.UpdateCouponRequest req) {
        var c = findOrThrow(id);

        if (req.code() != null && !req.code().equals(c.code)) {
            if (coupons.codeExists(req.code())) {
                throw new ConflictException("COUPON_CODE_EXISTS",
                        "Coupon code '" + req.code() + "' is already in use.",
                        Map.of("code", req.code()));
            }
            c.code = req.code();
        }
        if (req.description() != null) c.description = req.description();
        if (req.discountType() != null) {
            validateDiscountValue(req.discountType(),
                    req.discountValue() != null ? req.discountValue() : c.discountValue);
            c.discountType = req.discountType();
        }
        if (req.discountValue() != null) {
            validateDiscountValue(
                    req.discountType() != null ? req.discountType() : c.discountType,
                    req.discountValue());
            c.discountValue = req.discountValue();
        }
        if (req.currency() != null) c.currency = req.currency().toUpperCase();
        if (req.minSubtotal() != null) c.minSubtotal = req.minSubtotal();
        if (req.freeShipping() != null) c.freeShipping = req.freeShipping();
        if (req.maxRedemptions() != null) c.maxRedemptions = req.maxRedemptions();
        if (req.maxPerCustomer() != null) c.maxPerCustomer = req.maxPerCustomer();
        if (req.active() != null) c.active = req.active();
        if (req.startsAt() != null) c.startsAt = req.startsAt();
        if (req.expiresAt() != null) c.expiresAt = req.expiresAt();

        return toDto(c);
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    @Transactional
    public void delete(long id) {
        var c = findOrThrow(id);
        if (c.redeemedCount > 0) {
            throw new ConflictException("COUPON_IN_USE",
                    "Cannot delete a coupon that has been redeemed. Deactivate it instead.",
                    Map.of("redeemedCount", c.redeemedCount));
        }
        coupons.delete(c);
    }

    // -------------------------------------------------------------------------

    private Coupon findOrThrow(long id) {
        return coupons.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Coupon", id));
    }

    private void validateDiscountValue(String type, BigDecimal value) {
        if ("PERCENT".equals(type)) {
            if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("INVALID_DISCOUNT_VALUE",
                        "Percentage discount cannot be negative.", Map.of());
            }
            if (value != null && value.compareTo(new BigDecimal("100")) > 0) {
                throw new BadRequestException("INVALID_DISCOUNT_VALUE",
                        "Percentage discount cannot exceed 100.", Map.of());
            }
        }
    }

    private CouponDtos.CouponDto toDto(Coupon c) {
        return new CouponDtos.CouponDto(
                c.id, c.code, c.description, c.discountType, c.discountValue,
                c.currency, c.minSubtotal, c.freeShipping, c.maxRedemptions,
                c.maxPerCustomer, c.redeemedCount, c.active,
                c.startsAt, c.expiresAt, c.createdAt, c.updatedAt
        );
    }
}
