package com.gadaaistar.admin.catalog;

import java.math.BigDecimal;
import java.time.Instant;

import com.gadaaistar.common.PriceTier;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request / response DTOs for the admin customer-group endpoints
 * ({@code /api/admin/v1/customer-groups}).
 *
 * <p>Kept in a dedicated file (rather than folded into {@code CatalogDtos})
 * because customer groups sit between catalog (they parameterise price lists)
 * and customer management (each customer belongs to one) — the natural home
 * is somewhere between the two. The admin write path is gated by
 * {@code CUSTOMER_MANAGER} + {@code SUPERADMIN}, matching /customers.
 */
public final class CustomerGroupsDtos {

    private CustomerGroupsDtos() {}

    /**
     * Full response DTO. {@code customerCount} is filled in by the admin
     * service so the UI can render "3 customers in this group" without a
     * second round-trip. It is 0 for a freshly-created group and refreshes
     * on every {@code list()} call.
     */
    public record CustomerGroupDto(
            Long id,
            String code,
            String name,
            String description,
            PriceTier priceTier,
            BigDecimal moqMultiplier,
            boolean active,
            long customerCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * All required fields on create. Description is optional; if the client
     * omits {@code moqMultiplier} the server defaults to {@code 1.000}
     * (i.e. no B2B minimum-quantity multiplier).
     */
    public record CreateCustomerGroupRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[A-Z0-9_]+$",
                    message = "Code must be UPPER_SNAKE_CASE (letters, digits, underscore only).")
            String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500)           String description,
            @NotNull                   PriceTier priceTier,
            /**
             * Multiplier applied to a product's own {@code minOrderQuantity}
             * at cart-add time. E.g. a value of {@code 5.000} on a wholesale
             * tier means members must buy at least 5× the product's advertised
             * MOQ. Range 1..1000 to prevent typos ("500" instead of "5.00").
             */
            @DecimalMin("1.000") @DecimalMax("1000.000") BigDecimal moqMultiplier,
            Boolean active
    ) {}

    /**
     * Partial update — omitted fields are left untouched. {@code priceTier} is
     * present here (unlike {@code PriceList.customerGroupId} which is
     * immutable) because a group's tier can legitimately change over time
     * (e.g. promoting a former WHOLESALE_T2 group to WHOLESALE_T1). Any
     * bound price lists keep pointing at this group; only the effective
     * price tier changes.
     */
    public record UpdateCustomerGroupRequest(
            @Size(max = 64)
            @Pattern(regexp = "^[A-Z0-9_]+$",
                    message = "Code must be UPPER_SNAKE_CASE (letters, digits, underscore only).")
            String code,
            @Size(max = 200) String name,
            @Size(max = 500) String description,
            PriceTier priceTier,
            @DecimalMin("1.000") @DecimalMax("1000.000") BigDecimal moqMultiplier,
            Boolean active,
            /** Explicit flag to clear description back to null. */
            Boolean clearDescription
    ) {}
}
