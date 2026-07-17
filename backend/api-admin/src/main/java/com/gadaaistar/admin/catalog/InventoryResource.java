package com.gadaaistar.admin.catalog;

import java.util.List;

import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/admin/v1/inventory")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Catalog — Inventory", description = "On-hand and reserved stock levels per product.")
public class InventoryResource {

    private static final String[] WRITE_ROLES = { "SUPERADMIN", "PRODUCT_MANAGER" };

    @Inject InventoryService      service;
    @Inject SecurityContextHolder security;

    @GET
    @Operation(summary = "List inventory for every product.")
    public List<CatalogDtos.InventoryDto> list() {
        security.requireAdmin();
        return service.list();
    }

    @GET
    @Path("/products/{productId}")
    @Operation(summary = "Fetch inventory for a single product.")
    public CatalogDtos.InventoryDto getForProduct(@PathParam("productId") long productId) {
        security.requireAdmin();
        return service.getForProduct(productId);
    }

    @POST
    @Path("/products/{productId}/adjust")
    @Operation(summary = "Apply a signed delta to a product's quantity (e.g. -3 or +50).")
    public CatalogDtos.InventoryDto adjust(@PathParam("productId") long productId,
                                            @Valid CatalogDtos.AdjustInventoryRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.adjust(productId, req);
    }

    @PUT
    @Path("/products/{productId}")
    @Operation(summary = "Overwrite quantity + optional restock threshold for a product.")
    public CatalogDtos.InventoryDto set(@PathParam("productId") long productId,
                                         @Valid CatalogDtos.SetInventoryRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.set(productId, req);
    }
}
