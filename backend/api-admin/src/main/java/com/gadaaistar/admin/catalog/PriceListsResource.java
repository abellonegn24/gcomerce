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

@Path("/api/admin/v1/price-lists")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Catalog — Price Lists", description = "Manage per-customer-group price sheets + entries.")
public class PriceListsResource {

    private static final String[] WRITE_ROLES = { "SUPERADMIN", "PRODUCT_MANAGER" };

    @Inject PriceListsService     service;
    @Inject SecurityContextHolder security;

    @GET
    @Operation(summary = "List every price list with entry counts.")
    public List<CatalogDtos.PriceListDto> list() {
        security.requireAdmin();
        return service.list();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Fetch a price list with its full entry set.")
    public CatalogDtos.PriceListDetail get(@PathParam("id") long id) {
        security.requireAdmin();
        return service.get(id);
    }

    @POST
    @Operation(summary = "Create a new price list bound to a customer group.")
    public Response create(@Valid CatalogDtos.CreatePriceListRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var created = service.create(req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Partially update a price list. The bound customer group is immutable — "
                       + "changing it silently would reassign every tier.")
    public CatalogDtos.PriceListDto update(@PathParam("id") long id,
                                            @Valid CatalogDtos.UpdatePriceListRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.update(id, req);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a price list and every entry it contains.")
    public Response delete(@PathParam("id") long id) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        service.delete(id);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Entries — nested under a price list
    // -------------------------------------------------------------------------

    @PUT
    @Path("/{id}/entries")
    @Operation(summary = "Insert or update a (product, min-quantity) entry inside the price list.")
    public CatalogDtos.PriceListEntryDto upsertEntry(@PathParam("id") long priceListId,
                                                      @Valid CatalogDtos.UpsertPriceEntryRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.upsertEntry(priceListId, req);
    }

    @DELETE
    @Path("/{id}/entries/{entryId}")
    @Operation(summary = "Delete a single price entry.")
    public Response deleteEntry(@PathParam("id") long priceListId, @PathParam("entryId") long entryId) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        service.deleteEntry(priceListId, entryId);
        return Response.noContent().build();
    }
}
