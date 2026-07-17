package com.gadaaistar.admin.catalog;

import java.util.List;

import com.gadaaistar.common.ProductCategory;
import com.gadaaistar.common.ProductStatus;
import com.gadaaistar.common.exceptions.ValidationException;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.ws.rs.core.StreamingOutput;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin product-catalog endpoints. All under {@code /api/admin/v1/products}.
 *
 * <p>Every endpoint requires an authenticated admin JWT. Write endpoints
 * additionally require the SUPERADMIN or PRODUCT_MANAGER role.
 */
@Path("/api/admin/v1/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Catalog — Products", description = "CRUD for the product catalog + media management.")
public class ProductsResource {

    /** Roles allowed to mutate the catalog. Read-only endpoints allow any admin. */
    private static final String[] WRITE_ROLES = { "SUPERADMIN", "PRODUCT_MANAGER" };

    @Inject ProductsService        service;
    @Inject ProductCsvService      csvService;
    @Inject SecurityContextHolder  security;

    // -------------------------------------------------------------------------
    // List + get
    // -------------------------------------------------------------------------

    @GET
    @Operation(summary = "List products with optional text/status/category filters.")
    public CatalogDtos.ProductListResponse list(
            @QueryParam("q") String q,
            @QueryParam("status") String status,
            @QueryParam("category") String category,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {

        security.requireAdmin();
        return service.list(q, parseStatus(status), parseCategory(category), page, size);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Fetch a single product by numeric id, including media + inventory.")
    public CatalogDtos.ProductDto get(@PathParam("id") long id) {
        security.requireAdmin();
        return service.get(id);
    }

    // -------------------------------------------------------------------------
    // Create / update / archive
    // -------------------------------------------------------------------------

    @POST
    @Operation(summary = "Create a new product. Also seeds a zero-qty inventory row.")
    public Response create(@Valid CatalogDtos.CreateProductRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var created = service.create(req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Partially update a product. Any field set in the body replaces the current value.")
    public CatalogDtos.ProductDto update(@PathParam("id") long id, @Valid CatalogDtos.UpdateProductRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.update(id, req);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Archive a product (soft-delete). Historical orders keep working.")
    public Response archive(@PathParam("id") long id) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        service.archive(id);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Media
    // -------------------------------------------------------------------------

    @POST
    @Path("/{id}/media")
    @Operation(summary = "Attach a new image or video URL to a product.")
    public List<CatalogDtos.ProductMediaDto> addMedia(@PathParam("id") long productId,
                                                       @Valid CatalogDtos.CreateMediaRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.addMedia(productId, req);
    }

    @DELETE
    @Path("/{id}/media/{mediaId}")
    @Operation(summary = "Remove a media entry from a product.")
    public Response removeMedia(@PathParam("id") long productId, @PathParam("mediaId") long mediaId) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        service.removeMedia(productId, mediaId);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Bulk import / export (CSV)
    // -------------------------------------------------------------------------

    @GET
    @Path("/export.csv")
    @Produces("text/csv")
    @Operation(summary = "Export all products as a CSV file. The same file can be re-imported via /import-csv.")
    public Response exportCsv() {
        security.requireAdmin();
        var csv = csvService.exportCsv();
        return Response.ok((StreamingOutput) out -> out.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .header("Content-Disposition", "attachment; filename=products-export.csv")
                .build();
    }

    @POST
    @Path("/import-csv")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Import products from CSV. Existing SKUs are updated; new SKUs are created as DRAFT.")
    public Response importCsv(String csv) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var result = csvService.importCsv(csv);
        var status = result.errors.isEmpty()
                ? Response.Status.OK
                : Response.Status.PARTIAL_CONTENT;
        return Response.status(status)
                .entity(java.util.Map.of(
                        "created", result.created,
                        "updated", result.updated,
                        "errors", result.errors))
                .build();
    }

    // -------------------------------------------------------------------------

    private static ProductStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ProductStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid product status '" + raw + "'. Expected DRAFT / ACTIVE / ARCHIVED.");
        }
    }

    private static ProductCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ProductCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            var bySlug = ProductCategory.fromSlug(raw);
            if (bySlug != null) return bySlug;
            throw new ValidationException("Invalid product category '" + raw + "'.");
        }
    }
}
