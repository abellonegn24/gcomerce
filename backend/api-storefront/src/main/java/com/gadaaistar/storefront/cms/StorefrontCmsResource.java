package com.gadaaistar.storefront.cms;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Public storefront CMS endpoints. No auth required — these serve
 * published content to all visitors.
 */
@Path("/api/storefront/v1/cms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "CMS (Storefront)",
     description = "Public content pages and homepage banners.")
public class StorefrontCmsResource {

    @Inject StorefrontCmsService service;

    @GET
    @Path("/pages")
    @Operation(summary = "List all published CMS pages.")
    public List<StorefrontCmsDtos.PageDto> listPages() {
        return service.listPublishedPages();
    }

    @GET
    @Path("/pages/{slug}")
    @Operation(summary = "Fetch a single published page by slug.")
    public StorefrontCmsDtos.PageDto getPage(@PathParam("slug") String slug) {
        return service.getPageBySlug(slug);
    }

    @GET
    @Path("/banners")
    @Operation(summary = "List active banners for a placement (defaults to HOMEPAGE_HERO).")
    public StorefrontCmsDtos.BannerListResponse banners(
            @QueryParam("placement") String placement) {
        var p = placement != null && !placement.isBlank() ? placement : "HOMEPAGE_HERO";
        return service.getActiveBanners(p);
    }
}
