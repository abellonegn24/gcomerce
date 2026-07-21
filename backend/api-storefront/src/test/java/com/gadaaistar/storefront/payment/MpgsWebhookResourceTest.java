package com.gadaaistar.storefront.payment;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;
import com.gadaaistar.storefront.order.OrderService;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MpgsWebhookResource}. We use Mockito to stub
 * {@link MpgsService}, {@link CheckoutOrderRepository}, and
 * {@link OrderService} — no Quarkus runtime or database required.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Invalid JSON body → 400 INVALID_JSON</li>
 *   <li>Missing/wrong notification secret → 400 INVALID_SECRET</li>
 *   <li>Missing order.id → 400 MISSING_ORDER_ID</li>
 *   <li>Unknown invoice number → 200 unknown_order</li>
 *   <li>Idempotency guard — already-processed order → 200 already_processed</li>
 *   <li>CAPTURED + SUCCESS → markPaid called</li>
 *   <li>FAILED → markPaymentFailed called</li>
 *   <li>CANCELLED → markPaymentFailed called</li>
 *   <li>AUTHORIZED → pending_review (no state mutation)</li>
 * </ul>
 */
class MpgsWebhookResourceTest {

    private MpgsWebhookResource resource;
    private MpgsService mpgs;
    private CheckoutOrderRepository orders;
    private OrderService orderService;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VALID_SECRET = "test-notification-secret";
    private static final String INVOICE_NUMBER = "GI-2026-00001";

    @BeforeEach
    void setUp() throws Exception {
        resource = new MpgsWebhookResource();
        mpgs = mock(MpgsService.class);
        orders = mock(CheckoutOrderRepository.class);
        orderService = mock(OrderService.class);

        // Inject mocks via reflection.
        inject("mpgs", mpgs);
        inject("orders", orders);
        inject("orderService", orderService);

        // Default: secret verification passes.
        when(mpgs.verifyNotificationSecret(VALID_SECRET)).thenReturn(true);
        when(mpgs.verifyNotificationSecret(anyString())).thenAnswer(inv ->
                VALID_SECRET.equals(inv.getArgument(0)));
    }

    private void inject(String fieldName, Object value) throws Exception {
        var field = MpgsWebhookResource.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(resource, value);
    }

    private String webhookBody(String secret, String orderId, String status,
                               String txnId, String txnResult) {
        var sb = new StringBuilder("{");
        sb.append("\"notificationSecret\":\"").append(secret == null ? "" : secret).append("\"");
        if (orderId != null) {
            sb.append(",\"order\":{\"id\":\"").append(orderId).append("\"}");
        }
        if (status != null) {
            sb.append(",\"status\":\"").append(status).append("\"");
        }
        if (txnId != null || txnResult != null) {
            sb.append(",\"transaction\":{");
            if (txnId != null) sb.append("\"id\":\"").append(txnId).append("\"");
            if (txnId != null && txnResult != null) sb.append(",");
            if (txnResult != null) sb.append("\"result\":\"").append(txnResult).append("\"");
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    private CheckoutOrder pendingOrder() {
        var order = new CheckoutOrder();
        order.uuid = "order-uuid-1234";
        order.invoiceNumber = INVOICE_NUMBER;
        order.status = OrderStatus.PENDING_PAYMENT;
        order.currency = "USD";
        order.grandTotal = new java.math.BigDecimal("100.00");
        return order;
    }

    // -------------------------------------------------------------------------
    // Body parsing + secret verification
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Body parsing + secret verification")
    class InputValidation {

        @Test
        @DisplayName("returns 400 INVALID_JSON for malformed JSON")
        void invalidJson() {
            var response = resource.handle("not valid json {{{");
            assertEquals(400, response.getStatus());
            var entity = response.getEntity();
            assertNotNull(entity);
            assertTrue(entity.toString().contains("INVALID_JSON"));
        }

        @Test
        @DisplayName("returns 400 INVALID_SECRET when secret is missing")
        void missingSecret() {
            var body = "{\"order\":{\"id\":\"GI-2026-00001\"},\"status\":\"CAPTURED\"}";
            when(mpgs.verifyNotificationSecret(null)).thenReturn(false);
            var response = resource.handle(body);
            assertEquals(400, response.getStatus());
            assertTrue(response.getEntity().toString().contains("INVALID_SECRET"));
        }

        @Test
        @DisplayName("returns 400 INVALID_SECRET when secret is wrong")
        void wrongSecret() {
            when(mpgs.verifyNotificationSecret("wrong-secret")).thenReturn(false);
            var body = webhookBody("wrong-secret", INVOICE_NUMBER, "CAPTURED", "txn-1", "SUCCESS");
            var response = resource.handle(body);
            assertEquals(400, response.getStatus());
            assertTrue(response.getEntity().toString().contains("INVALID_SECRET"));
        }

        @Test
        @DisplayName("returns 400 MISSING_ORDER_ID when order.id is absent")
        void missingOrderId() {
            var body = "{\"notificationSecret\":\"" + VALID_SECRET + "\",\"status\":\"CAPTURED\"}";
            var response = resource.handle(body);
            assertEquals(400, response.getStatus());
            assertTrue(response.getEntity().toString().contains("MISSING_ORDER_ID"));
        }
    }

    // -------------------------------------------------------------------------
    // Order lookup + idempotency
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Order lookup + idempotency")
    class OrderLookup {

        @Test
        @DisplayName("returns 200 unknown_order when invoice not found")
        void unknownOrder() {
            when(orders.findByInvoiceNumber(INVOICE_NUMBER)).thenReturn(Optional.empty());
            var body = webhookBody(VALID_SECRET, INVOICE_NUMBER, "CAPTURED", "txn-1", "SUCCESS");
            var response = resource.handle(body);
            assertEquals(200, response.getStatus());
            assertTrue(response.getEntity().toString().contains("unknown_order"));
            verify(orderService, never()).markPaid(any(), any(), any());
        }

        @Test
        @DisplayName("returns 200 already_processed when order is already PAID")
        void alreadyProcessed() {
            var order = pendingOrder();
            order.status = OrderStatus.PAID;
            when(orders.findByInvoiceNumber(INVOICE_NUMBER)).thenReturn(Optional.of(order));
            var body = webhookBody(VALID_SECRET, INVOICE_NUMBER, "CAPTURED", "txn-1", "SUCCESS");
            var response = resource.handle(body);
            assertEquals(200, response.getStatus());
            assertTrue(response.getEntity().toString().contains("already_processed"));
            verify(orderService, never()).markPaid(any(), any(), any());
            verify(orderService, never()).markPaymentFailed(any(), any());
        }
    }

    // -------------------------------------------------------------------------
    // Payment outcomes
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Payment outcomes")
    class PaymentOutcomes {

        @Test
        @DisplayName("CAPTURED + SUCCESS → calls markPaid with MPGS provider")
        void capturedSuccess() {
            var order = pendingOrder();
            when(orders.findByInvoiceNumber(INVOICE_NUMBER)).thenReturn(Optional.of(order));
            var body = webhookBody(VALID_SECRET, INVOICE_NUMBER, "CAPTURED", "txn-abc", "SUCCESS");
            var response = resource.handle(body);
            assertEquals(200, response.getStatus());
            assertTrue(response.getEntity().toString().contains("paid"));
            verify(orderService).markPaid(order, "txn-abc", "MPGS");
        }

        @Test
        @DisplayName("FAILED → calls markPaymentFailed")
        void failed() {
            var order = pendingOrder();
            when(orders.findByInvoiceNumber(INVOICE_NUMBER)).thenReturn(Optional.of(order));
            var body = webhookBody(VALID_SECRET, INVOICE_NUMBER, "FAILED", "txn-abc", "FAILURE");
            var response = resource.handle(body);
            assertEquals(200, response.getStatus());
            assertTrue(response.getEntity().toString().contains("cancelled"));
            verify(orderService).markPaymentFailed(eq(order), anyString());
        }

        @Test
        @DisplayName("CANCELLED → calls markPaymentFailed")
        void cancelled() {
            var order = pendingOrder();
            when(orders.findByInvoiceNumber(INVOICE_NUMBER)).thenReturn(Optional.of(order));
            var body = webhookBody(VALID_SECRET, INVOICE_NUMBER, "CANCELLED", null, null);
            var response = resource.handle(body);
            assertEquals(200, response.getStatus());
            assertTrue(response.getEntity().toString().contains("cancelled"));
            verify(orderService).markPaymentFailed(eq(order), anyString());
        }

        @Test
        @DisplayName("AUTHORIZED + SUCCESS → calls markPaid with MPGS provider")
        void authorized() {
            var order = pendingOrder();
            when(orders.findByInvoiceNumber(INVOICE_NUMBER)).thenReturn(Optional.of(order));
            var body = webhookBody(VALID_SECRET, INVOICE_NUMBER, "AUTHORIZED", "txn-abc", "SUCCESS");
            var response = resource.handle(body);
            assertEquals(200, response.getStatus());
            assertTrue(response.getEntity().toString().contains("paid"));
            verify(orderService).markPaid(order, "txn-abc", "MPGS");
            verify(orderService, never()).markPaymentFailed(any(), any());
        }

        @Test
        @DisplayName("Unknown status → returns pending_review, no state mutation")
        void unknownStatus() {
            var order = pendingOrder();
            when(orders.findByInvoiceNumber(INVOICE_NUMBER)).thenReturn(Optional.of(order));
            var body = webhookBody(VALID_SECRET, INVOICE_NUMBER, "SOMETHING_NEW", "txn-abc", "SUCCESS");
            var response = resource.handle(body);
            assertEquals(200, response.getStatus());
            assertTrue(response.getEntity().toString().contains("pending_review"));
            verify(orderService, never()).markPaid(any(), any(), any());
            verify(orderService, never()).markPaymentFailed(any(), any());
        }

        @Test
        @DisplayName("CAPTURED but result is FAILURE → calls markPaymentFailed")
        void capturedButFailureResult() {
            var order = pendingOrder();
            when(orders.findByInvoiceNumber(INVOICE_NUMBER)).thenReturn(Optional.of(order));
            var body = webhookBody(VALID_SECRET, INVOICE_NUMBER, "CAPTURED", "txn-abc", "FAILURE");
            var response = resource.handle(body);
            assertEquals(200, response.getStatus());
            // The FAILURE result should trigger markPaymentFailed even though
            // status is CAPTURED (defensive — a CAPTURED with FAILURE result
            // is an inconsistent state from MPGS).
            verify(orderService).markPaymentFailed(eq(order), anyString());
        }
    }
}
