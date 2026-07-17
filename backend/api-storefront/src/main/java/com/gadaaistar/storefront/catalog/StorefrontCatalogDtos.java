package com.gadaaistar.storefront.catalog;

import java.math.BigDecimal;
import java.util.List;

import com.gadaaistar.common.PagedResponse;
import com.gadaaistar.common.ProductCategory;

/**
 * Public catalog DTOs — the shape returned to unauthenticated visitors and
 * signed-in customers alike. No cost prices or admin-internal fields leak out.
 */
public final class StorefrontCatalogDtos {

    private StorefrontCatalogDtos() {}

    public record ProductCard(
            Long id,
            String sku,
            String slug,
            String name,
            String shortDescription,
            ProductCategory category,
            String currency,
            /** Base retail price — for anonymous visitors. Signed-in B2B users see their price via {@link #displayPrice()}. */
            BigDecimal basePrice,
            /** Effective price for the caller (retail or per-tier B2B price). */
            BigDecimal displayPrice,
            /**
             * Discount vs. base price, expressed as a positive percent (0-100).
             * 0 for anonymous / retail; non-zero for B2B tiers.
             */
            BigDecimal discountPercent,
            BigDecimal minOrderQuantity,
            boolean inStock,
            String primaryImageUrl,
            /** 300px card thumbnail — falls back to primaryImageUrl for legacy media. */
            String cardThumbnailUrl
    ) {}

    public record ProductDetail(
            Long id,
            String sku,
            String slug,
            String name,
            String shortDescription,
            String description,
            ProductCategory category,
            String currency,
            BigDecimal basePrice,
            BigDecimal displayPrice,
            BigDecimal discountPercent,
            /** Wholesale tiers visible to this caller — empty for anonymous / retail. */
            List<TieredPrice> tiers,
            BigDecimal weightKg,
            String originCountry,
            String hsCode,
            BigDecimal minOrderQuantity,
            String packagingNotes,
            List<String> mediaUrls,
            String primaryImageUrl,
            /** 800px detail thumbnail — falls back to primaryImageUrl for legacy media. */
            String detailThumbnailUrl,
            boolean inStock,
            BigDecimal availableQuantity,
            List<VariantSummary> variants
    ) {}

    public record VariantSummary(
            Long id,
            String sku,
            String name,
            BigDecimal priceOverride,
            BigDecimal weightKgOverride,
            boolean active,
            int sortOrder,
            List<VariantAttribute> attributes,
            boolean inStock,
            BigDecimal availableQuantity
    ) {}

    public record VariantAttribute(
            String key,
            String value
    ) {}

    public record TieredPrice(
            BigDecimal minQuantity,
            BigDecimal unitPrice
    ) {}

    public record ListResponse(PagedResponse<ProductCard> page) {}
}
