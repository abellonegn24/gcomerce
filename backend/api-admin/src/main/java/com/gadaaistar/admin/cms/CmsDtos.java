package com.gadaaistar.admin.cms;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request / response DTOs for admin CMS endpoints
 * ({@code /api/admin/v1/cms/pages}, {@code /api/admin/v1/cms/banners}).
 */
public final class CmsDtos {

    private CmsDtos() {}

    // -------------------------------------------------------------------------
    // Pages
    // -------------------------------------------------------------------------

    public record PageDto(
            Long id,
            String slug,
            String title,
            String bodyHtml,
            String seoTitle,
            String seoDescription,
            String status,
            Integer sortOrder,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreatePageRequest(
            @NotBlank @Size(max = 120)
            @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase letters, digits, and hyphens only.")
            String slug,
            @NotBlank @Size(max = 200) String title,
            String bodyHtml,
            @Size(max = 200) String seoTitle,
            @Size(max = 500) String seoDescription,
            String status,
            Integer sortOrder
    ) {}

    public record UpdatePageRequest(
            @Size(max = 120)
            @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase letters, digits, and hyphens only.")
            String slug,
            @Size(max = 200) String title,
            String bodyHtml,
            @Size(max = 200) String seoTitle,
            @Size(max = 500) String seoDescription,
            String status,
            Integer sortOrder,
            Boolean clearBody,
            Boolean clearSeoTitle,
            Boolean clearSeoDescription
    ) {}

    // -------------------------------------------------------------------------
    // Banners
    // -------------------------------------------------------------------------

    public record BannerDto(
            Long id,
            String placement,
            String title,
            String imageUrl,
            String altText,
            String linkUrl,
            Instant activeFrom,
            Instant activeTo,
            Integer sortOrder,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateBannerRequest(
            @NotBlank @Size(max = 40) String placement,
            @Size(max = 200) String title,
            @NotBlank @Size(max = 500) String imageUrl,
            @Size(max = 300) String altText,
            @Size(max = 500) String linkUrl,
            Instant activeFrom,
            Instant activeTo,
            Integer sortOrder,
            Boolean active
    ) {}

    public record UpdateBannerRequest(
            @Size(max = 40) String placement,
            @Size(max = 200) String title,
            @Size(max = 500) String imageUrl,
            @Size(max = 300) String altText,
            @Size(max = 500) String linkUrl,
            Instant activeFrom,
            Instant activeTo,
            Integer sortOrder,
            Boolean active,
            Boolean clearTitle,
            Boolean clearLinkUrl,
            Boolean clearSchedule
    ) {}
}
