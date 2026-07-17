package com.gadaaistar.admin.shipping;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request / response DTOs for admin shipping-method endpoints
 * ({@code /api/admin/v1/shipping-methods}).
 *
 * <p>Kept in a dedicated file (rather than folded into {@code CatalogDtos})
 * because shipping is conceptually an order-side concern, not a catalog one.
 * The admin role guarding these endpoints is {@code ORDER_MANAGER} for the
 * same reason.
 */
public final class ShippingDtos {

    private ShippingDtos() {}

    public record ShippingMethodDto(
            Long id,
            String code,
            String name,
            String description,
            String currency,
            BigDecimal baseCost,
            BigDecimal perKgRate,
            Integer minDays,
            Integer maxDays,
            boolean active,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * All fields required on create except description + delivery ETA. The
     * {@code code} is unique per currency (server-enforced via a DB unique
     * constraint — the resource turns the resulting SQL conflict into a
     * 409).
     */
    public record CreateShippingMethodRequest(
            @NotBlank @Size(max = 64)  @Pattern(regexp = "^[A-Z0-9_]+$",
                    message = "Code must be UPPER_SNAKE_CASE (letters, digits, underscore only).")
            String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000)          String description,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull @DecimalMin("0.0000") BigDecimal baseCost,
            @NotNull @DecimalMin("0.0000") BigDecimal perKgRate,
            Integer minDays,
            Integer maxDays,
            Boolean active,
            Integer sortOrder
    ) {}

    /**
     * Partial update. Any omitted field is left untouched. The customer group
     * / bound cart currency is intentionally NOT included here — but we do
     * allow currency to be updated because shipping methods can genuinely be
     * repriced in a different currency, and there is no downstream FK that
     * would silently corrupt (order snapshots carry their own currency).
     */
    public record UpdateShippingMethodRequest(
            @Size(max = 64)  @Pattern(regexp = "^[A-Z0-9_]+$",
                    message = "Code must be UPPER_SNAKE_CASE (letters, digits, underscore only).")
            String code,
            @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Size(min = 3, max = 3) String currency,
            @DecimalMin("0.0000") BigDecimal baseCost,
            @DecimalMin("0.0000") BigDecimal perKgRate,
            Integer minDays,
            Integer maxDays,
            Boolean active,
            Integer sortOrder,
            /** Explicit flag to clear the description back to null. */
            Boolean clearDescription,
            /** Explicit flag to clear the transit-day range back to null. */
            Boolean clearDeliveryDays
    ) {}

    // -------------------------------------------------------------------------
    // Per-country shipping rate zones
    // -------------------------------------------------------------------------

    public record ShippingRateZoneDto(
            Long id,
            Long shippingMethodId,
            String shippingMethodCode,
            String countryCode,
            BigDecimal baseCost,
            BigDecimal perKgRate,
            Integer minDays,
            Integer maxDays,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateShippingRateZoneRequest(
            @NotNull Long shippingMethodId,
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @DecimalMin("0.0000") BigDecimal baseCost,
            @DecimalMin("0.0000") BigDecimal perKgRate,
            Integer minDays,
            Integer maxDays,
            Boolean active
    ) {}

    public record UpdateShippingRateZoneRequest(
            @DecimalMin("0.0000") BigDecimal baseCost,
            @DecimalMin("0.0000") BigDecimal perKgRate,
            Integer minDays,
            Integer maxDays,
            Boolean active,
            Boolean clearBaseCost,
            Boolean clearPerKgRate,
            Boolean clearDeliveryDays
    ) {}
}
