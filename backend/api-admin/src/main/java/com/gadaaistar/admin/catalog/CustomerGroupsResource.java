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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Full CRUD on customer groups. Groups drive B2B pricing tiers (via the
 * {@code priceTier} enum) and the per-tier minimum-order-quantity multiplier
 * applied to product MOQ at cart-add time.
 *
 * <p>The GET verb is available to any authenticated admin — the price-list
 * editor and customer-detail page both need to render the list as a dropdown.
 * Writes are restricted to {@code SUPERADMIN} + {@code CUSTOMER_MANAGER}
 * because a wrong {@code moqMultiplier} or {@code priceTier} can hurt every
 * B2B customer in the group.
 */
@Path("/api/admin/v1/customer-groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Customer Groups (Admin)", description = "CRUD for the B2B pricing-tier groups.")
public class CustomerGroupsResource {

    private static final String[] WRITE_ROLES = { "SUPERADMIN", "CUSTOMER_MANAGER" };

    @Inject CustomerGroupsService   service;
    @Inject SecurityContextHolder   security;

    @GET
    @Operation(summary = "List every customer group with its customer count. Read for any admin.")
    public List<CustomerGroupsDtos.CustomerGroupDto> list() {
        security.requireAdmin();
        return service.list();
    }

    @POST
    @Operation(summary = "Create a new customer group. Code is globally unique (case-insensitive).")
    public Response create(@Valid CustomerGroupsDtos.CreateCustomerGroupRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var created = service.create(req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Partially update a customer group. Fields not present are left unchanged.")
    public CustomerGroupsDtos.CustomerGroupDto update(@PathParam("id") long id,
                                                      @Valid CustomerGroupsDtos.UpdateCustomerGroupRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.update(id, req);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a customer group. Rejects with 409 if any customer or price list still references it.")
    public Response delete(@PathParam("id") long id) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        service.delete(id);
        return Response.noContent().build();
    }
}
