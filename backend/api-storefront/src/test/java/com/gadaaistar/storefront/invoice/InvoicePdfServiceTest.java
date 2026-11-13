package com.gadaaistar.storefront.invoice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerAddress;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.OrderItem;

import org.junit.jupiter.api.Test;

/**
 * Plain unit test — no container. The entities are public-field classes so a
 * fully-populated order can be assembled without a database.
 */
class InvoicePdfServiceTest {

    @Test
    void rendersInvoicePdfForPaidOrder() throws Exception {
        var service = new InvoicePdfService();
        service.companyLegalName  = "Gadaa Istar Business Group S.C.";
        service.companyTin        = java.util.Optional.of("0012345678");
        service.companyVat        = java.util.Optional.empty();
        service.storefrontBaseUrl = "http://localhost:4200";

        var customer = new Customer();
        customer.email      = "abel@example.com";
        customer.firstName  = "Abel";
        customer.lastName   = "Tadesse";
        customer.companyName = "Tadesse Trading PLC";
        customer.phone      = "+251911223344";

        var billTo = new CustomerAddress();
        billTo.fullName   = "Abel Tadesse";
        billTo.company    = "Tadesse Trading PLC";
        billTo.streetLine1 = "Bole Road, 123";
        billTo.city        = "Addis Ababa";
        billTo.region      = "Addis Ababa";
        billTo.postalCode  = "1000";
        billTo.countryCode = "ET";

        var order = new CheckoutOrder();
        order.uuid           = "543c1008-d6af-48f6-93cf-d83f195a24dd";
        order.invoiceNumber  = "GI-2026-00043";
        order.customer       = customer;
        order.status         = OrderStatus.PAID;
        order.currency       = "USD";
        order.subtotal       = new BigDecimal("390.0000");
        order.taxTotal       = new BigDecimal("0.0000");
        order.shippingTotal  = new BigDecimal("12.5000");
        order.discountTotal  = BigDecimal.ZERO;
        order.grandTotal     = new BigDecimal("402.5000");
        order.billToAddress  = billTo;
        order.shipToAddress  = billTo;
        order.shippingMethodName = "Sea freight — Djibouti FOB";
        order.paymentMethod    = "MPGS";
        order.paymentProvider  = "MPGS";
        order.paymentReference = "GI-2026-00043-TXN-1";
        order.placedAt       = Instant.parse("2026-11-11T07:30:00Z");
        order.paidAt         = Instant.parse("2026-11-11T07:47:14Z");

        var item = new OrderItem();
        item.sku       = "GI-RC-YRG-WG1-MT";
        item.name      = "Yirgacheffe Washed Grade 1";
        item.unitPrice = new BigDecimal("390.0000");
        item.quantity  = new BigDecimal("1.000");
        item.lineTotal = new BigDecimal("390.0000");
        order.items.add(item);

        var pdf = service.render(order);

        assertTrue(pdf.length > 2_000, "PDF too small — likely empty");
        assertArrayEquals(new byte[]{'%', 'P', 'D', 'F'},
                new byte[]{pdf[0], pdf[1], pdf[2], pdf[3]});

        // Leave a copy for manual inspection during development.
        var out = Path.of("build", "invoice-test.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
    }
}
