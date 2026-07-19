package com.gadaaistar.storefront.order;

import java.util.List;

import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.OrderItem;
import com.gadaaistar.persistence.order.OrderStatusEvent;
import com.gadaaistar.storefront.address.AddressMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderMapper {

    @Inject AddressMapper addressMapper;

    public OrderDtos.OrderSummary toSummary(CheckoutOrder o) {
        return new OrderDtos.OrderSummary(
                o.id,
                o.uuid,
                o.invoiceNumber,
                o.status,
                o.currency,
                o.grandTotal,
                o.items == null ? 0 : o.items.size(),
                o.placedAt,
                o.paidAt
        );
    }

    public OrderDtos.OrderDetail toDetail(CheckoutOrder o) {
        return toDetail(o, o.items == null ? List.of() : o.items.stream().map(this::toLine).toList(),
                        o.statusEvents == null ? List.of() : o.statusEvents.stream().map(this::toEvent).toList());
    }

    public OrderDtos.OrderDetail toDetail(CheckoutOrder o,
                                          List<OrderDtos.OrderLineDto> items,
                                          List<OrderDtos.OrderStatusEventDto> history) {
        return new OrderDtos.OrderDetail(
                o.id,
                o.uuid,
                o.invoiceNumber,
                o.status,
                o.currency,
                o.subtotal,
                o.taxTotal,
                o.shippingTotal,
                o.discountTotal,
                o.grandTotal,
                o.shipToAddress == null ? null : addressMapper.toDto(o.shipToAddress),
                o.billToAddress == null ? null : addressMapper.toDto(o.billToAddress),
                o.shippingMethodCode,
                o.shippingMethodName,
                o.customerNotes,
                o.couponCode,
                o.couponDescription,
                o.couponDiscount,
                o.paymentReference,
                o.paymentProvider,
                o.paymentMethod,
                o.placedAt,
                o.paidAt,
                o.cancelledAt,
                items,
                history
        );
    }

    public OrderDtos.OrderLineDto toLine(OrderItem it) {
        return new OrderDtos.OrderLineDto(
                it.id,
                it.product == null ? null : it.product.id,
                it.sku,
                it.name,
                it.variantSku,
                it.variantName,
                it.primaryImageUrl,
                it.unitPrice,
                it.quantity,
                it.taxRate,
                it.taxAmount,
                it.lineTotal
        );
    }

    public OrderDtos.OrderStatusEventDto toEvent(OrderStatusEvent e) {
        return new OrderDtos.OrderStatusEventDto(
                e.fromStatus,
                e.toStatus,
                e.source == null ? null : e.source.name(),
                e.note,
                e.occurredAt
        );
    }
}
