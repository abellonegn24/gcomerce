package com.gadaaistar.storefront.payment;

import java.math.BigDecimal;
import java.util.Optional;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;
import com.gadaaistar.storefront.order.OrderService;

import jakarta.ws.rs.core.MultivaluedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link YagoutPayReturnResource} — Mockito stubs for the
 * service, repository and order service; no Quarkus runtime or database.
 *
 * <p>The return endpoints always answer with a 303 browser redirect — the
 * gateway POSTs form-urlencoded data and the customer's browser must end up
 * on an Angular page. These tests assert the redirect target plus which
 * order-service mutation (if any) happened for each outcome:
 * <ul>
 *   <li>Successful result on /return → markPaid + /checkout/return</li>
 *   <li>Declined result on /return, or anything on /failure → markPaymentFailed + /checkout/cancel</li>
 *   <li>Undecryptable / unknown order / field mismatch → cancel + paymentError, no mutation</li>
 *   <li>Already-processed order → return-page redirect, no mutation (idempotent)</li>
 *   <li>Non-YagoutPay order → cancel + paymentError, no mutation (replay guard)</li>
 * </ul>
 */
class YagoutPayReturnResourceTest {

    private static final String STOREFRONT = "https://gedaaistar.et";
    private static final String UUID = "order-uuid-1";
    private static final String INVOICE = "GI-2026-00001";

    private YagoutPayReturnResource resource;
    private YagoutPayService yagout;
    private YagoutPayConfig config;
    private CheckoutOrderRepository orders;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        resource = new YagoutPayReturnResource();
        yagout = mock(YagoutPayService.class);
        config = mock(YagoutPayConfig.class);
        orders = mock(CheckoutOrderRepository.class);
        orderService = mock(OrderService.class);

        resource.yagout = yagout;
        resource.config = config;
        resource.orders = orders;
        resource.orderService = orderService;

        when(config.storefrontBaseUrl()).thenReturn(STOREFRONT);
    }

    private CheckoutOrder pendingYagoutOrder() {
        var order = new CheckoutOrder();
        order.id = 7L;
        order.uuid = UUID;
        order.invoiceNumber = INVOICE;
        order.status = OrderStatus.PENDING_PAYMENT;
        order.paymentMethod = "YAGOUTPAY";
        order.currency = "USD";
        order.grandTotal = new BigDecimal("250.00");
        return order;
    }

    private YagoutPayService.YagoutResult successResult() {
        return new YagoutPayService.YagoutResult(
                UUID, INVOICE, "250.00", "USD",
                "Successful", "0", "Successful", "AG-123", "PG-456");
    }

    private YagoutPayService.YagoutResult failedResult() {
        return new YagoutPayService.YagoutResult(
                UUID, INVOICE, "250.00", "USD",
                "Failed", "100", "Declined by issuer", "AG-123", "PG-456");
    }

    private MultivaluedHashMap<String, String> form() {
        var form = new MultivaluedHashMap<String, String>();
        form.add("txn_response", "encrypted");
        return form;
    }

    private void stubOrder(CheckoutOrder order) {
        when(yagout.parseReturn(anyMap())).thenReturn(successResult());
        when(orders.findByUuid(UUID)).thenReturn(Optional.ofNullable(order));
        when(orders.findByInvoiceNumber(INVOICE)).thenReturn(Optional.ofNullable(order));
    }

    private static String location(jakarta.ws.rs.core.Response response) {
        return response.getLocation() == null ? "" : response.getLocation().toString();
    }

    // ---------------------------------------------------------------------
    // Happy paths
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Payment outcomes")
    class Outcomes {

        @Test
        @DisplayName("Successful on /return → markPaid + 303 to checkout/return")
        void success() {
            var order = pendingYagoutOrder();
            stubOrder(order);
            var response = resource.paymentReturn(form());

            assertEquals(303, response.getStatus());
            assertTrue(location(response).startsWith(
                    STOREFRONT + "/checkout/return?order=" + UUID));
            assertTrue(location(response).contains("provider=YAGOUTPAY"));
            verify(orderService).markPaid(order, "AG-123/PG-456", "YAGOUTPAY");
            verify(orderService, never()).markPaymentFailed(any(), any());
        }

        @Test
        @DisplayName("Declined on /return → markPaymentFailed + 303 to checkout/cancel")
        void declined() {
            var order = pendingYagoutOrder();
            when(yagout.parseReturn(anyMap())).thenReturn(failedResult());
            when(orders.findByUuid(UUID)).thenReturn(Optional.of(order));
            var response = resource.paymentReturn(form());

            assertEquals(303, response.getStatus());
            assertTrue(location(response).startsWith(
                    STOREFRONT + "/checkout/cancel?"));
            assertTrue(location(response).contains("order=" + UUID));
            verify(orderService).markPaymentFailed(eq(order), eq("Declined by issuer"));
            verify(orderService, never()).markPaid(any(), any(), any());
        }

        @Test
        @DisplayName("/failure endpoint → markPaymentFailed + cancel, even for a 'Successful' body")
        void failureEndpoint() {
            var order = pendingYagoutOrder();
            stubOrder(order); // parseReturn returns a *successful* result here
            var response = resource.paymentFailure(form());

            assertEquals(303, response.getStatus());
            assertTrue(location(response).startsWith(STOREFRONT + "/checkout/cancel?"));
            verify(orderService).markPaymentFailed(eq(order), anyString());
            // A "Successful" result arriving on the failure URL must never mark paid.
            verify(orderService, never()).markPaid(any(), any(), any());
        }
    }

    // ---------------------------------------------------------------------
    // Idempotency + integrity guards
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Guards")
    class Guards {

        @Test
        @DisplayName("undecryptable payload → cancel + paymentError, no mutation")
        void undecryptable() {
            when(yagout.parseReturn(anyMap())).thenReturn(null);
            var response = resource.paymentReturn(form());

            assertEquals(303, response.getStatus());
            assertTrue(location(response).contains("/checkout/cancel?"));
            assertTrue(location(response).contains("paymentError=1"));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("unknown order → cancel + paymentError, no mutation")
        void unknownOrder() {
            when(yagout.parseReturn(anyMap())).thenReturn(successResult());
            when(orders.findByUuid(UUID)).thenReturn(Optional.empty());
            when(orders.findByInvoiceNumber(INVOICE)).thenReturn(Optional.empty());
            var response = resource.paymentReturn(form());

            assertTrue(location(response).contains("paymentError=1"));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("already-PAID order → redirect to return page, no mutation (idempotent replay)")
        void alreadyPaid() {
            var order = pendingYagoutOrder();
            order.status = OrderStatus.PAID;
            stubOrder(order);
            var response = resource.paymentReturn(form());

            assertTrue(location(response).startsWith(STOREFRONT + "/checkout/return?"));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("amount mismatch → cancel + paymentError, never mark paid")
        void amountMismatch() {
            var order = pendingYagoutOrder();
            var tampered = new YagoutPayService.YagoutResult(
                    UUID, INVOICE, "1.00", "USD",
                    "Successful", "0", "Successful", "AG-1", "PG-1");
            when(yagout.parseReturn(anyMap())).thenReturn(tampered);
            when(orders.findByUuid(UUID)).thenReturn(Optional.of(order));
            var response = resource.paymentReturn(form());

            assertTrue(location(response).contains("paymentError=1"));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("currency mismatch → cancel + paymentError, never mark paid")
        void currencyMismatch() {
            var order = pendingYagoutOrder();
            var tampered = new YagoutPayService.YagoutResult(
                    UUID, INVOICE, "250.00", "ETB",
                    "Successful", "0", "Successful", "AG-1", "PG-1");
            when(yagout.parseReturn(anyMap())).thenReturn(tampered);
            when(orders.findByUuid(UUID)).thenReturn(Optional.of(order));
            var response = resource.paymentReturn(form());

            assertTrue(location(response).contains("paymentError=1"));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("order placed via another gateway → cancel + paymentError (replay guard)")
        void wrongProvider() {
            var order = pendingYagoutOrder();
            order.paymentMethod = "MPGS";
            stubOrder(order);
            var response = resource.paymentReturn(form());

            assertTrue(location(response).contains("paymentError=1"));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("falls back to invoice lookup when udf_1 uuid doesn't resolve")
        void invoiceFallback() {
            var order = pendingYagoutOrder();
            // order_no carries attempt suffix "-3"; uuid lookup misses.
            var result = new YagoutPayService.YagoutResult(
                    "stale-uuid", INVOICE + "-3", "250.00", "USD",
                    "Successful", "0", "Successful", "AG-1", "PG-1");
            when(yagout.parseReturn(anyMap())).thenReturn(result);
            when(orders.findByUuid("stale-uuid")).thenReturn(Optional.empty());
            when(orders.findByInvoiceNumber(INVOICE)).thenReturn(Optional.of(order));
            var response = resource.paymentReturn(form());

            assertTrue(location(response).startsWith(STOREFRONT + "/checkout/return?"));
            verify(orderService).markPaid(order, "AG-1/PG-1", "YAGOUTPAY");
        }
    }
}
