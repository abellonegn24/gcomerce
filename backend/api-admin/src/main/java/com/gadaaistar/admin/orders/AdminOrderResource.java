package com.gadaaistar.admin.orders;

import java.util.List;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
 * Admin-facing orders API. All routes require admin auth (any role) — RBAC
 * refinements per action land in Phase 4 (e.g. FINANCE can view but not
 * cancel).
 */
@Path("/api/admin/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
@Tag(name = "Orders (Admin)", description = "Operator view of every placed order.")
public class AdminOrderResource {

    @Inject AdminOrderService     service;
    @Inject AdminOrderMapper      mapper;
    @Inject SecurityContextHolder security;

    @GET
    @Operation(summary = "Paged list of orders with optional status + search filters.")
    public PagedOrders list(@QueryParam("status") OrderStatus status,
                             @QueryParam("q") String search,
                             @QueryParam("page") @DefaultValue("0") int page,
                             @QueryParam("size") @DefaultValue("20") int size) {
        security.requireAdmin();
        var rows = service.list(status, search, page, size).stream().map(mapper::toRow).toList();
        var total = service.count(status, search);
        return new PagedOrders(rows, page, size, total);
    }

    @GET
    @Path("/{uuid}")
    @Operation(summary = "Full detail on a single order — lines, addresses, customer, status history.")
    public AdminOrderDtos.OrderDetail get(@PathParam("uuid") String uuid) {
        security.requireAdmin();
        return mapper.toDetail(service.get(uuid));
    }

    @POST
    @Path("/{uuid}/transition")
    @Operation(summary = "Move an order to a new state (mark shipped, delivered, cancelled, refunded).")
    public AdminOrderDtos.OrderDetail transition(@PathParam("uuid") String uuid,
                                                  @Valid AdminOrderDtos.TransitionRequest req) {
        var adminId = security.requireAdmin();
        var order   = service.transition(uuid, req.next(), req.note(), adminId);
        return mapper.toDetail(order);
    }

    // -------------------------------------------------------------------------
    // Inline response
    // -------------------------------------------------------------------------
    public record PagedOrders(List<AdminOrderDtos.OrderRow> items, int page, int size, long total) {}
}
