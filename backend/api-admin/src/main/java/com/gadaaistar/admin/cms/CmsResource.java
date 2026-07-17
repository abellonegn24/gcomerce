package com.gadaaistar.admin.cms;

import java.util.List;

import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin CMS endpoints for managing static pages and homepage banners.
 *
 * <p>Write operations are gated by SUPERADMIN + PRODUCT_MANAGER — content
 * management is a catalog/marketing concern.
 */
@Path("/api/admin/v1/cms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "CMS", description = "Manage static pages and homepage banners.")
public class CmsResource {

    private static final String[] WRITE_ROLES = { "SUPERADMIN", "PRODUCT_MANAGER" };

    @Inject CmsService           service;
    @Inject SecurityContextHolder security;

    // -------------------------------------------------------------------------
    // Pages
    // -------------------------------------------------------------------------

    @GET
    @Path("/pages")
    @Operation(summary = "List all CMS pages (including drafts).")
    public List<CmsDtos.PageDto> listPages() {
        security.requireAdmin();
        return service.listPages();
    }

    @GET
    @Path("/pages/{id}")
    @Operation(summary = "Fetch a single CMS page by ID.")
    public CmsDtos.PageDto getPage(@PathParam("id") long id) {
        security.requireAdmin();
        return service.getPage(id);
    }

    @POST
    @Path("/pages")
    @Operation(summary = "Create a new CMS page.")
    public Response createPage(@Valid CmsDtos.CreatePageRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var created = service.createPage(req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PATCH
    @Path("/pages/{id}")
    @Operation(summary = "Partially update a CMS page.")
    public CmsDtos.PageDto updatePage(@PathParam("id") long id,
                                       @Valid CmsDtos.UpdatePageRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.updatePage(id, req);
    }

    @DELETE
    @Path("/pages/{id}")
    @Operation(summary = "Delete a CMS page.")
    public Response deletePage(@PathParam("id") long id) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        service.deletePage(id);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Banners
    // -------------------------------------------------------------------------

    @GET
    @Path("/banners")
    @Operation(summary = "List all CMS banners (active and inactive).")
    public List<CmsDtos.BannerDto> listBanners() {
        security.requireAdmin();
        return service.listBanners();
    }

    @GET
    @Path("/banners/{id}")
    @Operation(summary = "Fetch a single CMS banner by ID.")
    public CmsDtos.BannerDto getBanner(@PathParam("id") long id) {
        security.requireAdmin();
        return service.getBanner(id);
    }

    @POST
    @Path("/banners")
    @Operation(summary = "Create a new CMS banner.")
    public Response createBanner(@Valid CmsDtos.CreateBannerRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var created = service.createBanner(req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PATCH
    @Path("/banners/{id}")
    @Operation(summary = "Partially update a CMS banner.")
    public CmsDtos.BannerDto updateBanner(@PathParam("id") long id,
                                           @Valid CmsDtos.UpdateBannerRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.updateBanner(id, req);
    }

    @DELETE
    @Path("/banners/{id}")
    @Operation(summary = "Delete a CMS banner.")
    public Response deleteBanner(@PathParam("id") long id) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        service.deleteBanner(id);
        return Response.noContent().build();
    }
}
