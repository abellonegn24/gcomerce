package com.gadaaistar.admin.stats;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gadaaistar.common.CustomerStatus;
import com.gadaaistar.common.CustomerType;
import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.common.ProductStatus;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.catalog.ProductRepository;
import com.gadaaistar.persistence.coupon.CouponRepository;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.persistence.order.CheckoutOrder;
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

@Path("/api/admin/v1/stats")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Dashboard Stats", description = "KPI tiles for the admin dashboard.")
public class StatsResource {

    @Inject SecurityContextHolder       security;
    @Inject CheckoutOrderRepository     orders;
    @Inject CustomerRepository          customers;
    @Inject ProductRepository           products;
    @Inject InventoryRepository         inventories;
    @Inject CouponRepository            coupons;
    @Inject EntityManager               em;

    private static final List<OrderStatus> PAID_STATUSES = List.of(
            OrderStatus.PAID, OrderStatus.FULFILLING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    private static final int RECENT_ORDER_LIMIT = 8;
    private static final int LOW_STOCK_LIMIT   = 10;

    @GET
    @Operation(summary = "Aggregate KPI tiles + trend data for the dashboard.")
    public StatsDto stats() {
        security.requireAdmin();

        long openOrders = orders.count("status in ?1", List.of(
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.FULFILLING));
        long pendingB2b = customers.count("customerType = ?1 AND status = ?2",
                CustomerType.B2B, CustomerStatus.PENDING_APPROVAL);
        long productsLive = products.count("status", ProductStatus.ACTIVE);

        // Revenue periods
        var now = Instant.now();
        var todayStart = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        var yesterdayStart = todayStart.minusSeconds(86400);
        var monthStart = LocalDate.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        var lastMonthStart = monthStart.minusSeconds(30 * 86400L);

        BigDecimal todayRevenue   = sumRevenue(todayStart, null);
        BigDecimal yesterdayRevenue = sumRevenue(yesterdayStart, todayStart);
        BigDecimal monthRevenue   = sumRevenue(monthStart, null);
        BigDecimal lastMonthRevenue = sumRevenue(lastMonthStart, monthStart);

        long todayOrderCount   = countOrders(todayStart, null);
        long yesterdayOrderCount = countOrders(yesterdayStart, todayStart);

        // Low-stock count
        var lowStockQuery = em.createQuery(
                "SELECT COUNT(i) FROM Inventory i WHERE i.restockThreshold IS NOT NULL " +
                "AND (i.quantity - i.reservedQuantity) <= i.restockThreshold",
                Long.class);
        long lowStock = lowStockQuery.getSingleResult();

        // Active coupons count
        long activeCoupons = coupons.count("active = true");

        // Abandoned (PENDING_PAYMENT older than 1 hour)
        var abandonedCutoff = Instant.now().minusSeconds(3600);
        long abandoned = orders.count("status = ?1 AND placedAt < ?2",
                OrderStatus.PENDING_PAYMENT, abandonedCutoff);

        // 7-day revenue series for sparkline
        var dailySeries = buildDailyRevenueSeries(7);

        // Order status breakdown
        var statusBreakdown = buildStatusBreakdown();

        // Recent orders
        var recentOrders = buildRecentOrders();

        // Low-stock items list
        var lowStockItems = buildLowStockItems();

        return new StatsDto(
                openOrders,
                pendingB2b,
                productsLive,
                monthRevenue,
                lowStock,
                activeCoupons,
                abandoned,
                now,
                new RevenueTrend(
                        todayRevenue, yesterdayRevenue,
                        monthRevenue, lastMonthRevenue,
                        todayOrderCount, yesterdayOrderCount,
                        dailySeries),
                statusBreakdown,
                recentOrders,
                lowStockItems
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BigDecimal sumRevenue(Instant from, Instant to) {
        var q = em.createQuery(
                "SELECT COALESCE(SUM(o.grandTotal), 0) FROM CheckoutOrder o " +
                "WHERE o.status IN :paidStatuses AND o.placedAt >= :from" +
                (to != null ? " AND o.placedAt < :to" : ""),
                BigDecimal.class);
        q.setParameter("paidStatuses", PAID_STATUSES);
        q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);
        return q.getSingleResult();
    }

    private long countOrders(Instant from, Instant to) {
        var q = em.createQuery(
                "SELECT COUNT(o) FROM CheckoutOrder o " +
                "WHERE o.placedAt >= :from" +
                (to != null ? " AND o.placedAt < :to" : ""),
                Long.class);
        q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);
        return q.getSingleResult();
    }

    private List<DayRevenuePoint> buildDailyRevenueSeries(int days) {
        var result = new ArrayList<DayRevenuePoint>(days);
        var today = LocalDate.now(ZoneOffset.UTC);
        for (int i = days - 1; i >= 0; i--) {
            var day = today.minusDays(i);
            var dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant();
            var dayEnd = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            BigDecimal revenue = sumRevenue(dayStart, dayEnd);
            long orderCount = countOrders(dayStart, dayEnd);
            result.add(new DayRevenuePoint(day.toString(), revenue, orderCount));
        }
        return result;
    }

    private Map<String, Long> buildStatusBreakdown() {
        var map = new LinkedHashMap<String, Long>();
        for (var s : OrderStatus.values()) {
            long c = orders.count("status", s);
            if (c > 0) map.put(s.name(), c);
        }
        return map;
    }

    private List<RecentOrder> buildRecentOrders() {
        var q = em.createQuery(
                "SELECT o FROM CheckoutOrder o ORDER BY o.placedAt DESC",
                CheckoutOrder.class)
                .setMaxResults(RECENT_ORDER_LIMIT);
        var entities = q.getResultList();
        var result = new ArrayList<RecentOrder>(entities.size());
        for (var o : entities) {
            String customerName = o.customer != null ? o.customer.displayName() : "—";
            int itemCount = o.items != null ? o.items.size() : 0;
            result.add(new RecentOrder(
                    o.uuid,
                    o.invoiceNumber,
                    o.status.name(),
                    o.grandTotal,
                    o.currency,
                    customerName,
                    itemCount,
                    o.placedAt
            ));
        }
        return result;
    }

    private List<LowStockItem> buildLowStockItems() {
        var q = em.createQuery(
                "SELECT i FROM Inventory i " +
                "WHERE i.restockThreshold IS NOT NULL " +
                "AND (i.quantity - i.reservedQuantity) <= i.restockThreshold " +
                "ORDER BY (i.quantity - i.reservedQuantity) ASC",
                com.gadaaistar.persistence.catalog.Inventory.class)
                .setMaxResults(LOW_STOCK_LIMIT);
        var entities = q.getResultList();
        var result = new ArrayList<LowStockItem>(entities.size());
        for (var i : entities) {
            var available = i.quantity.subtract(i.reservedQuantity);
            String productName = i.product != null ? i.product.name : "—";
            String productSku  = i.product != null ? i.product.sku  : "";
            String variantName = i.variant != null ? i.variant.name : null;
            result.add(new LowStockItem(
                    productSku, productName, variantName,
                    available, i.restockThreshold
            ));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // DTO records
    // -------------------------------------------------------------------------

    public record StatsDto(
            long openOrders,
            long pendingB2bApprovals,
            long productsLive,
            BigDecimal monthRevenue,
            long lowStockItems,
            long activeCoupons,
            long abandonedOrders,
            Instant asOf,
            RevenueTrend revenueTrend,
            Map<String, Long> statusBreakdown,
            List<RecentOrder> recentOrders,
            List<LowStockItem> lowStockItemsList
    ) {}

    public record RevenueTrend(
            BigDecimal todayRevenue,
            BigDecimal yesterdayRevenue,
            BigDecimal monthRevenue,
            BigDecimal lastMonthRevenue,
            long todayOrderCount,
            long yesterdayOrderCount,
            List<DayRevenuePoint> dailySeries
    ) {}

    public record DayRevenuePoint(
            String date,
            BigDecimal revenue,
            long orderCount
    ) {}

    public record RecentOrder(
            String uuid,
            String invoiceNumber,
            String status,
            BigDecimal grandTotal,
            String currency,
            String customerName,
            int itemCount,
            Instant placedAt
    ) {}

    public record LowStockItem(
            String productSku,
            String productName,
            String variantName,
            BigDecimal available,
            BigDecimal restockThreshold
    ) {}
}
