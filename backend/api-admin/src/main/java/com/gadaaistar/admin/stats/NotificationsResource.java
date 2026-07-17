package com.gadaaistar.admin.stats;

import java.time.Instant;
import java.util.List;

import com.gadaaistar.common.CustomerStatus;
import com.gadaaistar.common.CustomerType;
import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Lightweight notification summary for the admin topbar bell icon.
 * Returns counts of actionable items: new orders, low-stock products,
 * and pending B2B applications. Designed to be polled every 30-60s.
 */
@Path("/api/admin/v1/notifications")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Notifications", description = "In-app notification center for admin users")
public class NotificationsResource {

    @Inject SecurityContextHolder       security;
    @Inject CheckoutOrderRepository     orders;
    @Inject CustomerRepository          customers;
    @Inject InventoryRepository         inventories;
    @Inject EntityManager               em;

    @GET
    @Operation(summary = "Return actionable notification counts for the admin bell icon.")
    public NotificationsDto notifications() {
        security.requireAdmin();

        long newOrders = orders.count("status in ?1", List.of(
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.FULFILLING));

        long pendingB2b = customers.count("customerType = ?1 AND status = ?2",
                CustomerType.B2B, CustomerStatus.PENDING_APPROVAL);

        var lowStockQuery = em.createQuery(
                "SELECT COUNT(i) FROM Inventory i WHERE i.restockThreshold IS NOT NULL " +
                "AND (i.quantity - i.reservedQuantity) <= i.restockThreshold",
                Long.class);
        long lowStock = lowStockQuery.getSingleResult();

        long total = newOrders + pendingB2b + lowStock;

        return new NotificationsDto(newOrders, pendingB2b, lowStock, total, Instant.now());
    }

    public record NotificationsDto(
            long newOrders,
            long pendingB2bApprovals,
            long lowStockItems,
            long total,
            Instant asOf
    ) {}
}
