package com.gadaaistar.storefront.cms;

import java.time.Instant;
import java.util.List;

import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.cms.CmsBanner;
import com.gadaaistar.persistence.cms.CmsBannerRepository;
import com.gadaaistar.persistence.cms.CmsPage;
import com.gadaaistar.persistence.cms.CmsPageRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Storefront-facing CMS service. Only publishes PUBLISHED pages and
 * active banners within their scheduling window.
 */
@ApplicationScoped
public class StorefrontCmsService {

    @Inject CmsPageRepository   pages;
    @Inject CmsBannerRepository banners;

    public StorefrontCmsDtos.PageDto getPageBySlug(String slug) {
        var page = pages.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException(
                        "Page with slug '" + slug + "' not found."));
        if (!"PUBLISHED".equalsIgnoreCase(page.status)) {
            throw new NotFoundException(
                    "Page with slug '" + slug + "' not found.");
        }
        return new StorefrontCmsDtos.PageDto(
                page.slug, page.title, page.bodyHtml,
                page.seoTitle, page.seoDescription
        );
    }

    public List<StorefrontCmsDtos.PageDto> listPublishedPages() {
        return pages.listPublished().stream()
                .map(p -> new StorefrontCmsDtos.PageDto(
                        p.slug, p.title, p.bodyHtml,
                        p.seoTitle, p.seoDescription))
                .toList();
    }

    public StorefrontCmsDtos.BannerListResponse getActiveBanners(String placement) {
        var now = Instant.now();
        var dtos = banners.listActiveByPlacement(placement.toUpperCase(), now).stream()
                .map(b -> new StorefrontCmsDtos.BannerDto(
                        b.placement, b.title, b.imageUrl, b.altText,
                        b.linkUrl, b.sortOrder))
                .toList();
        return new StorefrontCmsDtos.BannerListResponse(dtos, now);
    }
}
