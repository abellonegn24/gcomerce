package com.gadaaistar.storefront.address;

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
 * Signed-in customer's address book. Mounted under {@code /me/addresses} —
 * consistent with the existing {@code /me/*} auth endpoints.
 */
@Path("/api/storefront/v1/me/addresses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Addresses (Storefront)", description = "Ship-to / bill-to addresses saved by the signed-in customer.")
public class AddressResource {

    @Inject AddressService        service;
    @Inject SecurityContextHolder security;

    @GET
    @Operation(summary = "List every address saved by the signed-in customer, defaults first.")
    public List<AddressDtos.AddressDto> list() {
        return service.list(security.requireCustomer());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Fetch a single address by id (owned by the caller).")
    public AddressDtos.AddressDto get(@PathParam("id") long id) {
        return service.get(security.requireCustomer(), id);
    }

    @POST
    @Operation(summary = "Save a new address. The first address a customer creates is auto-flagged as default.")
    public Response create(@Valid AddressDtos.CreateAddressRequest req) {
        var dto = service.create(security.requireCustomer(), req);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Partial update — null fields are ignored, blank strings clear the field.")
    public AddressDtos.AddressDto update(@PathParam("id") long id,
                                         @Valid AddressDtos.UpdateAddressRequest req) {
        return service.update(security.requireCustomer(), id, req);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Remove an address. Any orders that still reference it keep working via ON DELETE SET NULL.")
    public Response delete(@PathParam("id") long id) {
        service.delete(security.requireCustomer(), id);
        return Response.noContent().build();
    }
}
