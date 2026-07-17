package com.gadaaistar.admin.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.gadaaistar.common.OrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin-facing order DTOs — richer than the customer-facing ones. */
public final class AdminOrderDtos {

    private AdminOrderDtos() {}

    // -------------------------------------------------------------------------
    // Requests
    // -------------------------------------------------------------------------

    /** Move an order to a new state. Reason is required for cancels + refunds. */
    public record TransitionRequest(
            @NotNull OrderStatus next,
            @Size(max = 500) String note
    ) {}

    // -------------------------------------------------------------------------
    // Responses
    // -------------------------------------------------------------------------

    /** Compact list-view row. */
    public record OrderRow(
            Long id,
            String uuid,
            String invoiceNumber,
            OrderStatus status,
            String currency,
            BigDecimal grandTotal,
            String customerEmail,
            String customerName,
            int itemCount,
            Instant placedAt,
            Instant paidAt
    ) {}

    public record AddressSnapshot(
            String fullName,
            String company,
            String streetLine1,
            String streetLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone
    ) {}

    public record CustomerSnapshot(
            Long id,
            String email,
            String firstName,
            String lastName,
            String companyName,
            String phone
    ) {}

    public record LineItem(
            Long id,
            Long productId,
            String sku,
            String name,
            String variantSku,
            String variantName,
            BigDecimal unitPrice,
            BigDecimal quantity,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {}

    public record StatusEvent(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String source,
            Long actorId,
            String note,
            Instant occurredAt
    ) {}

    public record OrderDetail(
            Long id,
            String uuid,
            String invoiceNumber,
            OrderStatus status,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxTotal,
            BigDecimal shippingTotal,
            BigDecimal discountTotal,
            /** Cumulative amount already refunded across all SUCCEEDED refund attempts. */
            BigDecimal refundedTotal,
            BigDecimal grandTotal,
            CustomerSnapshot customer,
            AddressSnapshot shipTo,
            AddressSnapshot billTo,
            /** Snapshot of the chosen shipping method at placement time (may be null). */
            String shippingMethodCode,
            String shippingMethodName,
            String customerNotes,
            String couponCode,
            String couponDescription,
            BigDecimal couponDiscount,
            String internalNotes,
            String paymentReference,
            String paymentProvider,
            String paymentMethod,
            Instant placedAt,
            Instant paidAt,
            Instant cancelledAt,
            List<LineItem> items,
            List<StatusEvent> statusHistory,
            /** Full refund history — success and failed attempts, oldest first. */
            List<com.gadaaistar.admin.refunds.RefundDtos.RefundEntry> refunds
    ) {}
}
