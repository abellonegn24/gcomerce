package com.gadaaistar.admin.catalog;

import java.util.List;

import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/admin/v1/products/{productId}/variants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Catalog — Product Variants", description = "CRUD for product variants and variant-level inventory.")
public class VariantsResource {

    private static final String[] WRITE_ROLES = { "SUPERADMIN", "PRODUCT_MANAGER" };

    @Inject VariantsService        service;
    @Inject SecurityContextHolder  security;

    @GET
    @Operation(summary = "List all variants for a product.")
    public List<CatalogDtos.VariantDto> list(@PathParam("productId") long productId) {
        security.requireAdmin();
        return service.list(productId);
    }

    @POST
    @Operation(summary = "Create a new variant. Also seeds a zero-qty variant inventory row.")
    public Response create(@PathParam("productId") long productId,
                           @Valid CatalogDtos.CreateVariantRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var created = service.create(productId, req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PATCH
    @Path("/{variantId}")
    @Operation(summary = "Partially update a variant.")
    public CatalogDtos.VariantDto update(@PathParam("productId") long productId,
                                          @PathParam("variantId") long variantId,
                                          @Valid CatalogDtos.UpdateVariantRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.update(productId, variantId, req);
    }

    @DELETE
    @Path("/{variantId}")
    @Operation(summary = "Delete a variant and its inventory row.")
    public Response delete(@PathParam("productId") long productId,
                           @PathParam("variantId") long variantId) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        service.delete(productId, variantId);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Variant-level inventory
    // -------------------------------------------------------------------------

    @GET
    @Path("/inventory")
    @Operation(summary = "List variant-level inventory for a product.")
    public List<CatalogDtos.VariantInventoryDto> listVariantInventory(@PathParam("productId") long productId) {
        security.requireAdmin();
        return service.listVariantInventory(productId);
    }

    @POST
    @Path("/{variantId}/inventory/adjust")
    @Operation(summary = "Apply a signed delta to a variant's quantity.")
    public CatalogDtos.VariantInventoryDto adjustVariantInventory(
            @PathParam("productId") long productId,
            @PathParam("variantId") long variantId,
            @Valid CatalogDtos.AdjustInventoryRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.adjustVariantInventory(productId, variantId, req);
    }

    @PUT
    @Path("/{variantId}/inventory")
    @Operation(summary = "Overwrite quantity + optional restock threshold for a variant.")
    public CatalogDtos.VariantInventoryDto setVariantInventory(
            @PathParam("productId") long productId,
            @PathParam("variantId") long variantId,
            @Valid CatalogDtos.SetInventoryRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.setVariantInventory(productId, variantId, req);
    }
}
