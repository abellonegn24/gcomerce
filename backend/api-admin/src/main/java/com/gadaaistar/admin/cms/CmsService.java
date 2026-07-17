package com.gadaaistar.admin.cms;

import java.util.List;
import java.util.Map;

import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.cms.CmsBanner;
import com.gadaaistar.persistence.cms.CmsBannerRepository;
import com.gadaaistar.persistence.cms.CmsPage;
import com.gadaaistar.persistence.cms.CmsPageRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Admin CRUD for CMS pages and banners.
 */
@ApplicationScoped
public class CmsService {

    private static final Logger LOG = Logger.getLogger(CmsService.class);

    @Inject CmsPageRepository   pages;
    @Inject CmsBannerRepository banners;

    // -------------------------------------------------------------------------
    // Pages
    // -------------------------------------------------------------------------

    public List<CmsDtos.PageDto> listPages() {
        return pages.listAllOrdered().stream()
                .map(CmsService::toPageDto)
                .toList();
    }

    public CmsDtos.PageDto getPage(long id) {
        return toPageDto(resolvePage(id));
    }

    @Transactional
    public CmsDtos.PageDto createPage(CmsDtos.CreatePageRequest req) {
        var slug = req.slug().trim().toLowerCase();
        if (pages.slugExists(slug)) {
            throw new ConflictException("PAGE_SLUG_DUPLICATE",
                    "A page with slug '" + slug + "' already exists.",
                    Map.of("slug", slug));
        }
        var page = new CmsPage();
        page.slug            = slug;
        page.title           = req.title().trim();
        page.bodyHtml        = req.bodyHtml();
        page.seoTitle        = req.seoTitle();
        page.seoDescription  = req.seoDescription();
        page.status          = req.status() != null ? req.status().toUpperCase() : "DRAFT";
        page.sortOrder       = req.sortOrder() != null ? req.sortOrder() : 0;
        pages.persist(page);
        LOG.infof("Created CMS page id=%d slug=%s", page.id, slug);
        return toPageDto(page);
    }

    @Transactional
    public CmsDtos.PageDto updatePage(long id, CmsDtos.UpdatePageRequest req) {
        var page = resolvePage(id);

        if (req.slug() != null && !req.slug().isBlank()) {
            var slug = req.slug().trim().toLowerCase();
            if (pages.slugExistsExcluding(slug, id)) {
                throw new ConflictException("PAGE_SLUG_DUPLICATE",
                        "A page with slug '" + slug + "' already exists.",
                        Map.of("slug", slug));
            }
            page.slug = slug;
        }
        if (req.title() != null && !req.title().isBlank()) page.title = req.title().trim();
        if (req.bodyHtml() != null) page.bodyHtml = req.bodyHtml();
        if (Boolean.TRUE.equals(req.clearBody())) page.bodyHtml = null;
        if (req.seoTitle() != null) page.seoTitle = req.seoTitle();
        if (Boolean.TRUE.equals(req.clearSeoTitle())) page.seoTitle = null;
        if (req.seoDescription() != null) page.seoDescription = req.seoDescription();
        if (Boolean.TRUE.equals(req.clearSeoDescription())) page.seoDescription = null;
        if (req.status() != null && !req.status().isBlank()) page.status = req.status().toUpperCase();
        if (req.sortOrder() != null) page.sortOrder = req.sortOrder();

        LOG.infof("Updated CMS page id=%d slug=%s", id, page.slug);
        return toPageDto(page);
    }

    @Transactional
    public void deletePage(long id) {
        var page = resolvePage(id);
        pages.delete(page);
        LOG.infof("Deleted CMS page id=%d slug=%s", id, page.slug);
    }

    // -------------------------------------------------------------------------
    // Banners
    // -------------------------------------------------------------------------

    public List<CmsDtos.BannerDto> listBanners() {
        return banners.listAllOrdered().stream()
                .map(CmsService::toBannerDto)
                .toList();
    }

    public CmsDtos.BannerDto getBanner(long id) {
        return toBannerDto(resolveBanner(id));
    }

    @Transactional
    public CmsDtos.BannerDto createBanner(CmsDtos.CreateBannerRequest req) {
        var banner = new CmsBanner();
        banner.placement  = req.placement().trim().toUpperCase();
        banner.title      = req.title();
        banner.imageUrl   = req.imageUrl().trim();
        banner.altText    = req.altText();
        banner.linkUrl    = req.linkUrl();
        banner.activeFrom = req.activeFrom();
        banner.activeTo   = req.activeTo();
        banner.sortOrder  = req.sortOrder() != null ? req.sortOrder() : 0;
        banner.active     = req.active() == null ? Boolean.TRUE : req.active();
        banners.persist(banner);
        LOG.infof("Created CMS banner id=%d placement=%s", banner.id, banner.placement);
        return toBannerDto(banner);
    }

    @Transactional
    public CmsDtos.BannerDto updateBanner(long id, CmsDtos.UpdateBannerRequest req) {
        var banner = resolveBanner(id);

        if (req.placement() != null && !req.placement().isBlank())
            banner.placement = req.placement().trim().toUpperCase();
        if (req.title() != null) banner.title = req.title();
        if (Boolean.TRUE.equals(req.clearTitle())) banner.title = null;
        if (req.imageUrl() != null && !req.imageUrl().isBlank()) banner.imageUrl = req.imageUrl().trim();
        if (req.altText() != null) banner.altText = req.altText();
        if (req.linkUrl() != null) banner.linkUrl = req.linkUrl();
        if (Boolean.TRUE.equals(req.clearLinkUrl())) banner.linkUrl = null;
        if (Boolean.TRUE.equals(req.clearSchedule())) {
            banner.activeFrom = null;
            banner.activeTo   = null;
        } else {
            if (req.activeFrom() != null) banner.activeFrom = req.activeFrom();
            if (req.activeTo() != null) banner.activeTo = req.activeTo();
        }
        if (req.sortOrder() != null) banner.sortOrder = req.sortOrder();
        if (req.active() != null) banner.active = req.active();

        LOG.infof("Updated CMS banner id=%d placement=%s", id, banner.placement);
        return toBannerDto(banner);
    }

    @Transactional
    public void deleteBanner(long id) {
        var banner = resolveBanner(id);
        banners.delete(banner);
        LOG.infof("Deleted CMS banner id=%d placement=%s", id, banner.placement);
    }

    // -------------------------------------------------------------------------

    private CmsPage resolvePage(long id) {
        return pages.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("CmsPage", id));
    }

    private CmsBanner resolveBanner(long id) {
        return banners.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("CmsBanner", id));
    }

    static CmsDtos.PageDto toPageDto(CmsPage p) {
        return new CmsDtos.PageDto(
                p.id, p.slug, p.title, p.bodyHtml,
                p.seoTitle, p.seoDescription, p.status, p.sortOrder,
                p.createdAt, p.updatedAt
        );
    }

    static CmsDtos.BannerDto toBannerDto(CmsBanner b) {
        return new CmsDtos.BannerDto(
                b.id, b.placement, b.title, b.imageUrl, b.altText, b.linkUrl,
                b.activeFrom, b.activeTo, b.sortOrder,
                Boolean.TRUE.equals(b.active), b.createdAt, b.updatedAt
        );
    }
}
