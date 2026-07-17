package com.gadaaistar.admin.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.gadaaistar.common.PagedResponse;
import com.gadaaistar.common.ProductCategory;
import com.gadaaistar.common.ProductStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request / response DTOs for admin catalog endpoints
 * (/api/admin/v1/products, /price-lists, /inventory).
 */
public final class CatalogDtos {

    private CatalogDtos() {}

    // -------------------------------------------------------------------------
    // Product
    // -------------------------------------------------------------------------

    public record ProductDto(
            Long id,
            String sku,
            String slug,
            String name,
            String shortDescription,
            String description,
            ProductCategory category,
            ProductStatus status,
            BigDecimal weightKg,
            String hsCode,
            String originCountry,
            Long taxClassId,
            String baseCurrency,
            BigDecimal basePrice,
            BigDecimal costPrice,          // admin-only field; not exposed on storefront DTO
            BigDecimal minOrderQuantity,
            String packagingNotes,
            List<ProductMediaDto> media,
            List<VariantDto> variants,
            InventorySummary inventory,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ProductMediaDto(
            Long id,
            String url,
            String altText,
            String mediaType,
            boolean isPrimary,
            int sortOrder,
            String cardThumbnailUrl,
            String detailThumbnailUrl
    ) {}

    public record InventorySummary(
            BigDecimal quantity,
            BigDecimal reserved,
            BigDecimal available,
            BigDecimal restockThreshold,
            boolean low
    ) {}

    public record ProductListItem(
            Long id,
            String sku,
            String slug,
            String name,
            ProductCategory category,
            ProductStatus status,
            String baseCurrency,
            BigDecimal basePrice,
            BigDecimal available,
            String primaryImageUrl,
            Instant updatedAt
    ) {}

    public record ProductListResponse(PagedResponse<ProductListItem> page) {}

    public record CreateProductRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[A-Z0-9._-]+$", message = "SKU may contain only A-Z, 0-9, dot, dash, and underscore.")
            String sku,

            @NotBlank @Size(max = 200)
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must be lowercase kebab-case.")
            String slug,

            @NotBlank @Size(max = 255) String name,
            @Size(max = 500) String shortDescription,
            String description,

            @NotNull ProductCategory category,
            @NotNull ProductStatus status,

            @DecimalMin("0.000") BigDecimal weightKg,
            @Size(max = 32) String hsCode,
            @Size(min = 2, max = 2) String originCountry,
            Long taxClassId,
            @NotBlank @Size(min = 3, max = 3) String baseCurrency,
            @NotNull @DecimalMin("0.0000") BigDecimal basePrice,
            @DecimalMin("0.0000") BigDecimal costPrice,
            @DecimalMin("0.001") BigDecimal minOrderQuantity,
            @Size(max = 500) String packagingNotes
    ) {}

    public record UpdateProductRequest(
            @Size(max = 200)
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must be lowercase kebab-case.")
            String slug,

            @Size(max = 255) String name,
            @Size(max = 500) String shortDescription,
            String description,

            ProductCategory category,
            ProductStatus status,

            @DecimalMin("0.000") BigDecimal weightKg,
            @Size(max = 32) String hsCode,
            @Size(min = 2, max = 2) String originCountry,
            Long taxClassId,
            @Size(min = 3, max = 3) String baseCurrency,
            @DecimalMin("0.0000") BigDecimal basePrice,
            @DecimalMin("0.0000") BigDecimal costPrice,
            @DecimalMin("0.001") BigDecimal minOrderQuantity,
            @Size(max = 500) String packagingNotes
    ) {}

    // -------------------------------------------------------------------------
    // Product media
    // -------------------------------------------------------------------------

    public record CreateMediaRequest(
            @NotBlank @Size(max = 500) String url,
            @Size(max = 255) String altText,
            @Pattern(regexp = "^(IMAGE|VIDEO)$", message = "mediaType must be IMAGE or VIDEO") String mediaType,
            Boolean isPrimary,
            Integer sortOrder,
            @Size(max = 500) String cardThumbnailUrl,
            @Size(max = 500) String detailThumbnailUrl
    ) {}

    // -------------------------------------------------------------------------
    // Price lists
    // -------------------------------------------------------------------------

    public record PriceListDto(
            Long id,
            Long customerGroupId,
            String customerGroupCode,
            String name,
            String currency,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            int entryCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record PriceListEntryDto(
            Long id,
            Long productId,
            String productSku,
            String productName,
            BigDecimal price,
            BigDecimal minQuantity
    ) {}

    public record PriceListDetail(PriceListDto priceList, List<PriceListEntryDto> entries) {}

    public record CreatePriceListRequest(
            @NotNull Long customerGroupId,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(min = 3, max = 3) String currency,
            Boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {}

    /**
     * Partial update — every field is optional. Null means "leave as-is".
     * The bound customer group is intentionally immutable (changing it would
     * silently reassign all pricing tiers to a different group).
     */
    public record UpdatePriceListRequest(
            @Size(max = 200) String name,
            @Size(min = 3, max = 3) String currency,
            Boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            /** Explicit flag to clear effectiveFrom (set to null on the row). */
            Boolean clearEffectiveFrom,
            /** Explicit flag to clear effectiveTo (set to null on the row). */
            Boolean clearEffectiveTo
    ) {}

    public record UpsertPriceEntryRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.0000") BigDecimal price,
            @NotNull @DecimalMin("0.001") BigDecimal minQuantity
    ) {}

    // -------------------------------------------------------------------------
    // Inventory
    // -------------------------------------------------------------------------

    public record InventoryDto(
            Long productId,
            String productSku,
            String productName,
            BigDecimal quantity,
            BigDecimal reserved,
            BigDecimal available,
            BigDecimal restockThreshold,
            boolean low,
            Instant updatedAt
    ) {}

    public record AdjustInventoryRequest(
            /** Signed delta — negative to decrement. */
            @NotNull BigDecimal delta,
            @Size(max = 200) String reason
    ) {}

    public record SetInventoryRequest(
            @NotNull @DecimalMin("0.000") BigDecimal quantity,
            @DecimalMin("0.000") BigDecimal restockThreshold
    ) {}

    // -------------------------------------------------------------------------
    // Product variants
    // -------------------------------------------------------------------------

    public record VariantAttributeDto(
            String key,
            String value
    ) {}

    public record VariantDto(
            Long id,
            String sku,
            String name,
            BigDecimal priceOverride,
            BigDecimal weightKgOverride,
            boolean active,
            int sortOrder,
            List<VariantAttributeDto> attributes,
            BigDecimal availableQuantity,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateVariantRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[A-Z0-9._-]+$", message = "SKU may contain only A-Z, 0-9, dot, dash, and underscore.")
            String sku,

            @NotBlank @Size(max = 255) String name,

            @DecimalMin("0.0000") BigDecimal priceOverride,
            @DecimalMin("0.000") BigDecimal weightKgOverride,
            Boolean active,
            Integer sortOrder,
            List<VariantAttributeDto> attributes
    ) {}

    public record UpdateVariantRequest(
            @Size(max = 64)
            @Pattern(regexp = "^[A-Z0-9._-]+$", message = "SKU may contain only A-Z, 0-9, dot, dash, and underscore.")
            String sku,

            @Size(max = 255) String name,
            @DecimalMin("0.0000") BigDecimal priceOverride,
            @DecimalMin("0.000") BigDecimal weightKgOverride,
            Boolean active,
            Integer sortOrder,
            List<VariantAttributeDto> attributes
    ) {}

    public record VariantInventoryDto(
            Long variantId,
            String variantSku,
            String variantName,
            BigDecimal quantity,
            BigDecimal reserved,
            BigDecimal available,
            BigDecimal restockThreshold,
            boolean low,
            Instant updatedAt
    ) {}

    // -------------------------------------------------------------------------
    // Placeholder — silence lint on unused imports (Email is used elsewhere;
    // kept here so the file can grow into a shared DTO home for the module).
    // -------------------------------------------------------------------------
    @SuppressWarnings("unused")
    private record LintKeeper(@Email String reserved) {}
}
