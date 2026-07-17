package com.gadaaistar.admin.orders;

import java.util.List;

import com.gadaaistar.admin.refunds.RefundDtos;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerAddress;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.OrderItem;
import com.gadaaistar.persistence.order.OrderRefund;
import com.gadaaistar.persistence.order.OrderStatusEvent;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AdminOrderMapper {

    public AdminOrderDtos.OrderRow toRow(CheckoutOrder o) {
        var c = o.customer;
        return new AdminOrderDtos.OrderRow(
                o.id,
                o.uuid,
                o.invoiceNumber,
                o.status,
                o.currency,
                o.grandTotal,
                c == null ? null : c.email,
                c == null ? null : displayName(c),
                o.items == null ? 0 : o.items.size(),
                o.placedAt,
                o.paidAt
        );
    }

    public AdminOrderDtos.OrderDetail toDetail(CheckoutOrder o) {
        return new AdminOrderDtos.OrderDetail(
                o.id,
                o.uuid,
                o.invoiceNumber,
                o.status,
                o.currency,
                o.subtotal,
                o.taxTotal,
                o.shippingTotal,
                o.discountTotal,
                o.refundedTotal,
                o.grandTotal,
                toCustomer(o.customer),
                toAddress(o.shipToAddress),
                toAddress(o.billToAddress),
                o.shippingMethodCode,
                o.shippingMethodName,
                o.customerNotes,
                o.couponCode,
                o.couponDescription,
                o.couponDiscount,
                o.internalNotes,
                o.paymentReference,
                o.paymentProvider,
                o.paymentMethod,
                o.placedAt,
                o.paidAt,
                o.cancelledAt,
                o.items == null ? List.of() : o.items.stream().map(this::toLine).toList(),
                o.statusEvents == null ? List.of() : o.statusEvents.stream().map(this::toEvent).toList(),
                o.refunds == null ? List.of() : o.refunds.stream().map(this::toRefundEntry).toList()
        );
    }

    /**
     * Map an {@link OrderRefund} to its wire shape. Public because
     * {@code RefundResource} also uses it to serialise a single refund
     * returned by the create endpoint.
     */
    public RefundDtos.RefundEntry toRefundEntry(OrderRefund r) {
        return new RefundDtos.RefundEntry(
                r.uuid,
                r.amount,
                r.reason,
                r.status,
                r.gatewayProvider,
                r.gatewayReference,
                r.gatewayErrorCode,
                r.gatewayErrorMessage,
                r.initiatedByAdminId,
                r.createdAt,
                r.updatedAt
        );
    }

    private AdminOrderDtos.CustomerSnapshot toCustomer(Customer c) {
        if (c == null) return null;
        return new AdminOrderDtos.CustomerSnapshot(c.id, c.email, c.firstName, c.lastName, c.companyName, c.phone);
    }

    private AdminOrderDtos.AddressSnapshot toAddress(CustomerAddress a) {
        if (a == null) return null;
        return new AdminOrderDtos.AddressSnapshot(
                a.fullName, a.company, a.streetLine1, a.streetLine2, a.city,
                a.region, a.postalCode, a.countryCode, a.phone);
    }

    private AdminOrderDtos.LineItem toLine(OrderItem it) {
        return new AdminOrderDtos.LineItem(
                it.id,
                it.product == null ? null : it.product.id,
                it.sku, it.name,
                it.variantSku, it.variantName,
                it.unitPrice, it.quantity,
                it.taxRate, it.taxAmount, it.lineTotal);
    }

    private AdminOrderDtos.StatusEvent toEvent(OrderStatusEvent e) {
        return new AdminOrderDtos.StatusEvent(
                e.fromStatus, e.toStatus,
                e.source == null ? null : e.source.name(),
                e.actorId, e.note, e.occurredAt);
    }

    private static String displayName(Customer c) {
        if (c.companyName != null && !c.companyName.isBlank()) return c.companyName;
        var full = String.join(" ",
                c.firstName == null ? "" : c.firstName,
                c.lastName  == null ? "" : c.lastName).trim();
        return full.isBlank() ? c.email : full;
    }
}
