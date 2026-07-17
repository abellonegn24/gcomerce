package com.gadaaistar.admin.coupon;

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

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/admin/v1/coupons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Catalog — Coupons", description = "CRUD for discount coupon codes.")
public class CouponsResource {

    private static final String[] WRITE_ROLES = { "SUPERADMIN", "PRODUCT_MANAGER" };

    @Inject CouponsService       service;
    @Inject CouponReportScheduler reportScheduler;
    @Inject SecurityContextHolder security;

    @GET
    @Operation(summary = "List all coupons (paginated).")
    public CouponDtos.CouponListResponse list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        security.requireAdmin();
        return service.list(page, size);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a single coupon by id.")
    public CouponDtos.CouponDto get(@PathParam("id") long id) {
        security.requireAdmin();
        return service.get(id);
    }

    @POST
    @Operation(summary = "Create a new coupon.")
    public Response create(@Valid CouponDtos.CreateCouponRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var created = service.create(req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Partially update a coupon.")
    public CouponDtos.CouponDto update(@PathParam("id") long id,
                                        @Valid CouponDtos.UpdateCouponRequest req) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.update(id, req);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a coupon. Only coupons with zero redemptions can be deleted.")
    public Response delete(@PathParam("id") long id) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        service.delete(id);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------

    @POST
    @Path("/reports/expiry-check")
    @Operation(summary = "Manually trigger a coupon expiry check and send warning email if needed.")
    public Response triggerExpiryCheck() {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        int count = reportScheduler.runExpiryCheck();
        return Response.ok(java.util.Map.of(
                "expiringCoupons", count,
                "message", count > 0
                        ? count + " coupon(s) expiring — warning email sent."
                        : "No coupons expiring within the warning window."
        )).build();
    }

    @POST
    @Path("/reports/usage-report")
    @Operation(summary = "Manually trigger a coupon usage report email.")
    public Response triggerUsageReport() {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        reportScheduler.runUsageReport();
        return Response.ok(java.util.Map.of(
                "message", "Coupon usage report email sent."
        )).build();
    }
}
