package com.gadaaistar.storefront.catalog;

import com.gadaaistar.common.ProductCategory;
import com.gadaaistar.common.exceptions.ValidationException;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Public catalog endpoints for the storefront.
 * All read-only, all safe for anonymous callers. Authenticated B2B customers
 * transparently see their tiered wholesale prices via
 * {@link StorefrontCatalogService#resolvePriceListForCaller()}.
 */
@Path("/api/storefront/v1/catalog")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Catalog (Storefront)", description = "Public product listings + detail.")
public class StorefrontCatalogResource {

    @Inject StorefrontCatalogService service;

    @GET
    @Path("/products")
    @Operation(summary = "List ACTIVE products. Optional filter by category slug or name.")
    public StorefrontCatalogDtos.ListResponse list(
            @QueryParam("category") String category,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("12") int size) {
        return service.list(parseCategory(category), page, size);
    }

    @GET
    @Path("/products/search")
    @Operation(summary = "Search ACTIVE products by keyword (sku, name, or slug). Optional category filter.")
    public StorefrontCatalogDtos.ListResponse search(
            @QueryParam("q") String q,
            @QueryParam("category") String category,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("12") int size) {
        if (q == null || q.isBlank()) {
            return service.list(parseCategory(category), page, size);
        }
        return service.search(q.trim(), parseCategory(category), page, size);
    }

    @GET
    @Path("/products/{slug}")
    @Operation(summary = "Return full product detail by slug (e.g. yirgacheffe-green-500g).")
    public StorefrontCatalogDtos.ProductDetail get(@PathParam("slug") String slug) {
        return service.getBySlug(slug);
    }

    // -------------------------------------------------------------------------

    private static ProductCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var bySlug = ProductCategory.fromSlug(raw);
        if (bySlug != null) return bySlug;
        try {
            return ProductCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown product category '" + raw + "'.");
        }
    }
}
