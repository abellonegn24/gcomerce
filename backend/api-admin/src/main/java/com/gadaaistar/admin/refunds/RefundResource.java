package com.gadaaistar.admin.refunds;

import java.util.List;

import com.gadaaistar.admin.orders.AdminOrderMapper;
import com.gadaaistar.admin.orders.AdminOrderService;
import com.gadaaistar.persistence.order.OrderRefundRepository;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin endpoints for refunding an order.
 *
 * <p>Placed under {@code /orders/{uuid}/refunds} rather than a top-level
 * {@code /refunds} route because a refund is always contextual to its
 * order — there is no admin workflow that starts "find a refund" without
 * first finding its order.
 *
 * <p>The refund history list is duplicated on the {@code AdminOrderDetail}
 * response too, so the UI never has to make two round-trips to render
 * the order page. This endpoint exists for the (occasional) case where a
 * client wants the raw list — audit exports, dashboards, etc.
 */
@Path("/api/admin/v1/orders/{uuid}/refunds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Refunds", description = "Issue partial or full refunds against a paid order.")
public class RefundResource {

    /** Only ORDER_MANAGER + SUPERADMIN can move real money. */
    private static final String[] WRITE_ROLES = { "SUPERADMIN", "ORDER_MANAGER" };

    @Inject RefundService          service;
    @Inject AdminOrderService      adminOrders;
    @Inject AdminOrderMapper       mapper;
    @Inject OrderRefundRepository  refunds;
    @Inject SecurityContextHolder  security;

    @GET
    @Operation(summary = "List every refund attempt (success + failure) recorded for an order.")
    public List<RefundDtos.RefundEntry> list(@PathParam("uuid") String uuid) {
        security.requireAdmin();
        var order = adminOrders.get(uuid);
        return refunds.listForOrder(order.id).stream().map(mapper::toRefundEntry).toList();
    }

    @POST
    @Operation(summary = "Issue a refund. Calls CyberSource when creds are configured, else records a MANUAL row.")
    public Response create(@PathParam("uuid") String uuid,
                            @Valid RefundDtos.CreateRefundRequest req) {
        var adminId = security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);
        var refund = service.refund(uuid, req, adminId);
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toRefundEntry(refund))
                .build();
    }
}
