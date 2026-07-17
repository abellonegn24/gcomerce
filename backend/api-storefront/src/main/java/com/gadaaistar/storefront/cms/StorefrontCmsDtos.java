package com.gadaaistar.storefront.cms;

import java.time.Instant;

/**
 * Public CMS DTOs — the shape returned to storefront visitors.
 * No draft pages or inactive banners leak out.
 */
public final class StorefrontCmsDtos {

    private StorefrontCmsDtos() {}

    public record PageDto(
            String slug,
            String title,
            String bodyHtml,
            String seoTitle,
            String seoDescription
    ) {}

    public record BannerDto(
            String placement,
            String title,
            String imageUrl,
            String altText,
            String linkUrl,
            Integer sortOrder
    ) {}

    public record BannerListResponse(
            java.util.List<BannerDto> banners,
            Instant fetchedAt
    ) {}
}
