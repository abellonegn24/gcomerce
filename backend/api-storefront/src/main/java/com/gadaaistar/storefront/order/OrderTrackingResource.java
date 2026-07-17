package com.gadaaistar.storefront.order;

import java.util.List;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.storefront.ratelimit.RateLimit;

import jakarta.inject.Inject;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Public order tracking — no authentication required. The caller supplies
 * the order UUID and the email on file; the endpoint returns a slimmed-down
 * view (no addresses, no payment references) suitable for a tracking page.
 *
 * <p>Rate-limited to blunt brute-force UUID/email guessing.
 */
@Path("/api/storefront/v1/track-order")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Order Tracking (Storefront)", description = "Public order lookup by UUID + email.")
public class OrderTrackingResource {

    @Inject OrderService orderService;
    @Inject OrderMapper  mapper;

    @GET
    @RateLimit(bucket = "order-track", max = 20, windowSeconds = 60)
    @Operation(summary = "Look up an order by UUID + email for public tracking. Returns a slimmed-down view.")
    public TrackingResponse track(
            @QueryParam("uuid") @NotBlank String uuid,
            @QueryParam("email") @NotBlank @Email String email) {

        var order = orderService.getForPublicTracking(uuid, email);

        var lines = order.items == null ? List.<OrderDtos.OrderLineDto>of()
                : order.items.stream().map(mapper::toLine).toList();
        var history = order.statusEvents == null ? List.<OrderDtos.OrderStatusEventDto>of()
                : order.statusEvents.stream().map(mapper::toEvent).toList();

        return new TrackingResponse(
                order.uuid,
                order.invoiceNumber,
                order.status,
                order.currency,
                order.grandTotal,
                order.items == null ? 0 : order.items.size(),
                order.placedAt,
                order.paidAt,
                order.shippingMethodName,
                lines,
                history
        );
    }

    // -------------------------------------------------------------------------

    public record TrackingResponse(
            String uuid,
            String invoiceNumber,
            OrderStatus status,
            String currency,
            java.math.BigDecimal grandTotal,
            int itemCount,
            java.time.Instant placedAt,
            java.time.Instant paidAt,
            String shippingMethodName,
            List<OrderDtos.OrderLineDto> items,
            List<OrderDtos.OrderStatusEventDto> statusHistory
    ) {}
}
