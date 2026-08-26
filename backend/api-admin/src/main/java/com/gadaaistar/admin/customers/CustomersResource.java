package com.gadaaistar.admin.customers;

import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin customer-management endpoints. All under {@code /api/admin/v1/customers}.
 *
 * <p>Read endpoints are open to any authenticated admin. Write endpoints are
 * restricted to {@code SUPERADMIN} or {@code CUSTOMER_MANAGER} — mirrors the
 * catalog / orders resources.
 */
@Path("/api/admin/v1/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Customers", description = "Manage customer accounts, B2B approvals, groups, and sessions.")
public class CustomersResource {

    private static final String[] WRITE_ROLES = { "SUPERADMIN", "CUSTOMER_MANAGER" };

    @Inject CustomersService            service;
    @Inject AdminCustomerResetService   resetService;
    @Inject SecurityContextHolder       security;

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    @GET
    @Operation(summary = "List customers with optional text / type / status filters. 20 per page by default.")
    public CustomersDtos.PagedCustomers list(
            @QueryParam("q")      String q,
            @QueryParam("type")   String type,
            @QueryParam("status") String status,
            @QueryParam("page")   @DefaultValue("0")  int page,
            @QueryParam("size")   @DefaultValue("20") int size) {
        security.requireAdmin();
        return service.list(q, type, status, page, size);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Fetch a single customer including addresses, business profile, and order count.")
    public CustomersDtos.CustomerDetail get(@PathParam("id") long id) {
        security.requireAdmin();
        return service.get(id);
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    @POST
    @Path("/{id}/status")
    @Operation(summary = "Change customer status. Enforces the admin state machine.")
    public CustomersDtos.ActionResponse changeStatus(@PathParam("id") long id,
                                                     @Valid CustomersDtos.ChangeStatusRequest req) {
        var actorId = security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.changeStatus(id, req.status(), req.reason(), actorId);
    }

    @POST
    @Path("/{id}/approve-b2b")
    @Operation(summary = "Approve a PENDING_APPROVAL B2B customer, flipping them to ACTIVE.")
    public CustomersDtos.ActionResponse approveB2B(@PathParam("id") long id) {
        var actorId = security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.approveB2B(id, actorId);
    }

    @POST
    @Path("/{id}/verify-email")
    @Operation(summary = "Force-mark this customer's email as verified. Use for admin-verified out-of-band accounts.")
    public CustomersDtos.ActionResponse forceVerifyEmail(@PathParam("id") long id) {
        var actorId = security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.forceVerifyEmail(id, actorId);
    }

    @POST
    @Path("/{id}/revoke-sessions")
    @Operation(summary = "Revoke every live refresh token for the customer. Kicks all devices.")
    public CustomersDtos.ActionResponse revokeSessions(@PathParam("id") long id) {
        var actorId = security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.revokeSessions(id, actorId);
    }

    @POST
    @Path("/{id}/unlock")
    @Operation(summary = "Clear a brute-force lockout on the customer account (INSA CWE-307).")
    public CustomersDtos.ActionResponse unlock(@PathParam("id") long id) {
        var actorId = security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.unlock(id, actorId);
    }

    @POST
    @Path("/{id}/reset-password")
    @Operation(summary = "Issue a one-shot password-reset link and email it to the customer. "
                       + "Also revokes any live sessions so a stolen access token cannot race the reset.")
    public CustomersDtos.ActionResponse sendPasswordReset(@PathParam("id") long id) {
        var actorId = security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return resetService.start(id, actorId);
    }

    @POST
    @Path("/{id}/group")
    @Operation(summary = "Assign or clear the customer's price / discount group. Pass {\"groupId\": null} to clear.")
    public CustomersDtos.ActionResponse setGroup(@PathParam("id") long id,
                                                  @Valid CustomersDtos.SetGroupRequest req) {
        var actorId = security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        return service.setGroup(id, req == null ? null : req.groupId(), actorId);
    }
}
