package com.gadaaistar.admin.shipping;

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
 * Admin CRUD for shipping methods. Guarded by SUPERADMIN + ORDER_MANAGER —
 * shipping is an order-side concern (a mis-configured rate lands directly on
 * the customer invoice), so ORDER_MANAGER is a better fit than
 * PRODUCT_MANAGER here.
 */
@Path("/api/admin/v1/shipping-methods")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Shipping Methods", description = "Manage the rate card the storefront picker exposes at checkout.")
public class ShippingMethodsResource {

    private static final String[] WRITE_ROLES = { "SUPERADMIN", "ORDER_MANAGER" };

    @Inject ShippingMethodsService   service;
    @Inject ShippingRateZonesService zoneService;
    @Inject SecurityContextHolder    security;

    @GET
    @Operation(summary = "List every shipping method (active + inactive).")
    public List<ShippingDtos.ShippingMethodDto> list() {
        security.requireAdmin();
        return service.list();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Fetch one shipping method.")
    public ShippingDtos.ShippingMethodDto get(@PathParam("id") long id) {
        security.requireAdmin();
        return service.get(id);
    }

    @POST
    @Operation(summary = "Create a new shipping method. Code is unique per currency.")
    public Response create(@Valid ShippingDtos.CreateShippingMethodRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var created = service.create(req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Partially update a shipping method. Only fields present in the body are touched.")
    public ShippingDtos.ShippingMethodDto update(@PathParam("id") long id,
                                                  @Valid ShippingDtos.UpdateShippingMethodRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.update(id, req);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a shipping method. Historical orders keep their snapshot code + name; only the live FK is cleared.")
    public Response delete(@PathParam("id") long id) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        service.delete(id);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Per-country rate zones
    // -------------------------------------------------------------------------

    @GET
    @Path("/{id}/zones")
    @Operation(summary = "List all per-country rate zones for a shipping method.")
    public List<ShippingDtos.ShippingRateZoneDto> listZones(@PathParam("id") long methodId) {
        security.requireAdmin();
        return zoneService.listByMethod(methodId);
    }

    @POST
    @Path("/{id}/zones")
    @Operation(summary = "Create a per-country rate override for this shipping method.")
    public Response createZone(@PathParam("id") long methodId,
                                @Valid ShippingDtos.CreateShippingRateZoneRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var created = zoneService.create(req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PATCH
    @Path("/zones/{zoneId}")
    @Operation(summary = "Partially update a shipping rate zone.")
    public ShippingDtos.ShippingRateZoneDto updateZone(@PathParam("zoneId") long zoneId,
                                                        @Valid ShippingDtos.UpdateShippingRateZoneRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return zoneService.update(zoneId, req);
    }

    @DELETE
    @Path("/zones/{zoneId}")
    @Operation(summary = "Delete a shipping rate zone. The method's default rates will apply again.")
    public Response deleteZone(@PathParam("zoneId") long zoneId) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        zoneService.delete(zoneId);
        return Response.noContent().build();
    }
}
