package com.gadaaistar.admin.coupon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CouponDtos {

    private CouponDtos() {}

    // -------------------------------------------------------------------------
    // Read model
    // -------------------------------------------------------------------------

    public record CouponDto(
            Long id,
            String code,
            String description,
            String discountType,
            BigDecimal discountValue,
            String currency,
            BigDecimal minSubtotal,
            Boolean freeShipping,
            Integer maxRedemptions,
            Integer maxPerCustomer,
            Integer redeemedCount,
            Boolean active,
            Instant startsAt,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CouponListResponse(
            List<CouponDto> items,
            long total,
            int page,
            int size
    ) {}

    // -------------------------------------------------------------------------
    // Write models
    // -------------------------------------------------------------------------

    public record CreateCouponRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Uppercase letters, digits, dash, underscore only.")
            String code,

            @Size(max = 500) String description,

            @NotBlank @Pattern(regexp = "PERCENT|FIXED_AMOUNT", message = "Must be PERCENT or FIXED_AMOUNT.")
            String discountType,

            @NotNull
            @DecimalMin(value = "0.0000") @DecimalMax(value = "100000.0000")
            BigDecimal discountValue,

            @NotBlank @Size(max = 3) String currency,

            @DecimalMin(value = "0.0000") BigDecimal minSubtotal,

            Boolean freeShipping,

            @Min(0) Integer maxRedemptions,

            @Min(0) Integer maxPerCustomer,

            Boolean active,

            Instant startsAt,
            Instant expiresAt
    ) {}

    /**
     * PATCH semantics — only non-null fields are applied.
     */
    public record UpdateCouponRequest(
            @Size(max = 64)
            @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Uppercase letters, digits, dash, underscore only.")
            String code,

            @Size(max = 500) String description,

            @Pattern(regexp = "PERCENT|FIXED_AMOUNT")
            String discountType,

            @DecimalMin(value = "0.0000") @DecimalMax(value = "100000.0000")
            BigDecimal discountValue,

            @Size(max = 3) String currency,

            @DecimalMin(value = "0.0000") BigDecimal minSubtotal,

            Boolean freeShipping,

            @Min(0) Integer maxRedemptions,

            @Min(0) Integer maxPerCustomer,

            Boolean active,

            Instant startsAt,
            Instant expiresAt
    ) {}
}
