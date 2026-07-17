package com.gadaaistar.storefront.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.storefront.address.AddressDtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request / response DTOs for {@code /me/orders}. */
public final class OrderDtos {

    private OrderDtos() {}

    // -------------------------------------------------------------------------
    // Requests
    // -------------------------------------------------------------------------

    /**
     * Place an order from the caller's current cart.
     *
     * <p>Ship-to and (optionally) bill-to address ids must already be in the
     * caller's address book. If {@code billToAddressId} is null the ship-to
     * doubles as the billing address.
     */
    public record PlaceOrderRequest(
            @NotNull Long shipToAddressId,
            Long billToAddressId,
            /**
             * Optional. Null means "no shipping method — pickup / FCA / etc.,
             * shipping total 0". A non-null id must resolve to an
             * {@code active=true} shipping_method whose currency matches the
             * cart's currency, else placement fails with a 409.
             */
            Long shippingMethodId,
            /**
             * Optional coupon code. If provided, the coupon is validated and
             * its discount applied to the order subtotal. Invalid/expired
             * coupons cause a 409. Null means no coupon.
             */
            @Size(max = 64) String couponCode,
            @Size(max = 1000) String customerNotes,
            /**
             * Payment method chosen by the customer at checkout:
             * {@code "CYBERSOURCE"} or {@code "MPGS"}. Null defaults to
             * CyberSource for backward compatibility.
             */
            @Size(max = 20) String paymentMethod
    ) {}

    /** Optional customer note when cancelling. */
    public record CancelOrderRequest(
            @Size(max = 500) String reason
    ) {}

    // -------------------------------------------------------------------------
    // Response — line + event + summary + detail
    // -------------------------------------------------------------------------

    public record OrderLineDto(
            Long id,
            Long productId,
            String sku,
            String name,
            String variantSku,
            String variantName,
            String primaryImageUrl,
            BigDecimal unitPrice,
            BigDecimal quantity,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {}

    public record OrderStatusEventDto(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String source,
            String note,
            Instant occurredAt
    ) {}

    /** Compact list-view row — omits line items + history. */
    public record OrderSummary(
            Long id,
            String uuid,
            String invoiceNumber,
            OrderStatus status,
            String currency,
            BigDecimal grandTotal,
            int itemCount,
            Instant placedAt,
            Instant paidAt
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
            BigDecimal grandTotal,
            AddressDtos.AddressDto shipTo,
            AddressDtos.AddressDto billTo,
            /** Snapshot of the chosen shipping method (code + name). Null when the order used no method. */
            String shippingMethodCode,
            String shippingMethodName,
            String customerNotes,
            String couponCode,
            String couponDescription,
            BigDecimal couponDiscount,
            String paymentReference,
            String paymentProvider,
            String paymentMethod,
            Instant placedAt,
            Instant paidAt,
            Instant cancelledAt,
            List<OrderLineDto> items,
            List<OrderStatusEventDto> statusHistory
    ) {}

    /**
     * Response returned by {@code POST /me/orders}. Includes everything the
     * customer needs to (a) show a confirmation and (b) redirect to the payment
     * gateway. The {@code payment} block is populated on-demand by
     * {@code /me/orders/{uuid}/payment-payload}.
     */
    public record PlaceOrderResponse(
            OrderDetail order,
            /** URL to POST the signed CyberSource fields to (endpoint URL). */
            String paymentGatewayUrl,
            /**
             * The signed key/value pairs the browser should POST as a form.
             * Order matters for CyberSource — the client can iterate as-is.
             */
            java.util.LinkedHashMap<String, String> paymentFields
    ) {}
}
