package com.gadaaistar.storefront.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.gadaaistar.common.OrderStatus;
import com.gadaaistar.common.ProductCategory;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.persistence.catalog.Product;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerAddress;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.OrderItem;
import com.gadaaistar.persistence.shipping.ShippingMethod;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link YagoutPayService} — plain JUnit, no Quarkus runtime.
 * The service's {@code config} field is package-private so the test (same
 * package) wires a hand-built {@link YagoutPayConfig} directly.
 *
 * <p>Covers: payload section layout + encryption, order_no attempt
 * suffixing, delimiter sanitization, currency gating, and the parseReturn
 * validation path (decrypt / me_id check / field extraction / udf_1).
 */
class YagoutPayServiceTest {

    private static final String KEY = "SjgTPThS1TWrD06ElFjRC3BoIgOXEh/xJ2IC+k+tVVM=";
    private static final String ME_ID = "202505060003";

    private YagoutPayService service;
    private YagoutPayConfig config;

    @BeforeEach
    void setUp() {
        config = new YagoutPayConfig();
        config.enabled = true;
        config.merchantId = ME_ID;
        config.encryptionKey = KEY;
        config.aggregatorId = "yagout";
        config.gatewayUrl = "https://uatcheckout.yagoutpay.com/gw";
        config.callbackBaseUrl = "https://api.gedaaistar.et";
        config.country = "ETH";
        config.supportedCurrencies = "";
        config.storefrontBaseUrl = "https://gedaaistar.et";

        service = new YagoutPayService();
        service.config = config;
    }

    // ---------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------

    private CheckoutOrder order() {
        var customer = new Customer();
        customer.id = 42L;
        customer.firstName = "Abel";
        customer.lastName = "Bekele";
        customer.email = "abel@example.com";
        customer.phone = "+251911000000";

        var address = new CustomerAddress();
        address.fullName = "Abel Bekele";
        address.streetLine1 = "Bole Road 12";
        address.streetLine2 = "Floor 3";
        address.city = "Addis Ababa";
        address.region = "AA";
        address.postalCode = "1000";
        address.countryCode = "ET";
        address.phone = "+251911111111";

        var product = new Product();
        product.category = ProductCategory.RAW_COFFEE;

        var item = new OrderItem();
        item.product = product;
        item.lineTotal = new BigDecimal("250.00");

        var shipping = new ShippingMethod();
        shipping.maxDays = 7;

        var order = new CheckoutOrder();
        order.uuid = "order-uuid-1";
        order.invoiceNumber = "GI-2026-00001";
        order.status = OrderStatus.PENDING_PAYMENT;
        order.currency = "USD";
        order.grandTotal = new BigDecimal("250.00");
        order.customer = customer;
        order.billToAddress = address;
        order.shipToAddress = address;
        order.shippingMethod = shipping;
        order.items = List.of(item);
        return order;
    }

    private String decryptMessage(RedirectPayloadHolder holder) {
        return YagoutPayCrypto.decrypt(holder.merchantRequest, KEY);
    }

    private record RedirectPayloadHolder(String merchantRequest, String hash) {}

    private RedirectPayloadHolder build(CheckoutOrder order, int attempt) {
        var payload = service.buildRedirectPayload(order, attempt);
        assertEquals("https://uatcheckout.yagoutpay.com/gw", payload.endpoint());
        var fields = payload.fields();
        assertEquals(ME_ID, fields.get("me_id"));
        assertNotNull(fields.get("merchant_request"));
        assertNotNull(fields.get("hash"));
        return new RedirectPayloadHolder(fields.get("merchant_request"), fields.get("hash"));
    }

    // ---------------------------------------------------------------------
    // buildRedirectPayload
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("buildRedirectPayload")
    class BuildPayload {

        @Test
        @DisplayName("produces the documented 9-section, tilde-joined layout")
        void sectionLayout() {
            var holder = build(order(), 1);
            var message = decryptMessage(holder);
            var sections = message.split("~", -1);
            assertEquals(9, sections.length,
                    "expected 9 tilde-separated sections: " + message);

            // txn_details — 10 fields
            var txn = sections[0].split("\\|", -1);
            assertEquals(10, txn.length);
            assertEquals("yagout", txn[0]);
            assertEquals(ME_ID, txn[1]);
            assertEquals("GI-2026-00001", txn[2]);
            assertEquals("250.00", txn[3]);
            assertEquals("ETH", txn[4]);
            assertEquals("USD", txn[5]);
            assertEquals("SALE", txn[6]);
            assertEquals("https://api.gedaaistar.et/api/storefront/v1/payment/yagoutpay/return", txn[7]);
            assertEquals("https://api.gedaaistar.et/api/storefront/v1/payment/yagoutpay/failure", txn[8]);
            assertEquals("WEB", txn[9]);

            assertEquals("|||", sections[1]);   // pg_details blank
            assertEquals("||||", sections[2]);  // card_details blank

            var cust = sections[3].split("\\|", -1);
            assertEquals(5, cust.length);
            assertEquals("Abel Bekele", cust[0]);
            assertEquals("abel@example.com", cust[1]);
            assertEquals("+251911111111", cust[2]);  // billTo.phone beats customer.phone
            assertEquals("42", cust[3]);
            assertEquals("Y", cust[4]);

            var bill = sections[4].split("\\|", -1);
            assertEquals("Bole Road 12, Floor 3", bill[0]);
            assertEquals("Addis Ababa", bill[1]);
            assertEquals("AA", bill[2]);
            assertEquals("ET", bill[3]);
            assertEquals("1000", bill[4]);

            var ship = sections[5].split("\\|", -1);
            assertEquals(7, ship.length);
            assertEquals("7", ship[5]);      // ship_days from shippingMethod.maxDays

            var item = sections[6].split("\\|", -1);
            assertEquals("1", item[0]);      // distinct line count
            assertEquals("250.00", item[1]); // line values
            assertEquals("RAW_COFFEE", item[2]);

            assertEquals("", sections[7]);   // upi_details

            var other = sections[8].split("\\|", -1);
            assertEquals("order-uuid-1", other[0]); // udf_1 = order uuid
        }

        @Test
        @DisplayName("hash decrypts to sha256(me_id~order_no~amount~country~currency)")
        void hashContents() {
            var holder = build(order(), 1);
            var digest = YagoutPayCrypto.decrypt(holder.hash(), KEY);
            assertEquals(
                    YagoutPayCrypto.sha256Hex(ME_ID + "~GI-2026-00001~250.00~ETH~USD"),
                    digest);
        }

        @Test
        @DisplayName("attempt 2 suffixes order_no for uniqueness")
        void attemptSuffix() {
            var holder = build(order(), 2);
            var txn = decryptMessage(holder).split("~")[0].split("\\|");
            assertEquals("GI-2026-00001-2", txn[2]);

            var digest = YagoutPayCrypto.decrypt(holder.hash(), KEY);
            assertEquals(
                    YagoutPayCrypto.sha256Hex(ME_ID + "~GI-2026-00001-2~250.00~ETH~USD"),
                    digest);
        }

        @Test
        @DisplayName("delimiters in customer data are sanitized out")
        void sanitizesDelimiters() {
            var order = order();
            order.billToAddress.streetLine1 = "12 | Main ~ Street";
            var holder = build(order, 1);
            var bill = decryptMessage(holder).split("~")[4].split("\\|");
            // "|" and "~" became spaces; streetLine2 still appended.
            assertEquals("12   Main   Street, Floor 3", bill[0]);
        }

        @Test
        @DisplayName("throws PAYMENT_GATEWAY_NOT_CONFIGURED when disabled")
        void notConfigured() {
            config.enabled = false;
            var ex = assertThrows(ConflictException.class,
                    () -> service.buildRedirectPayload(order(), 1));
            assertEquals("PAYMENT_GATEWAY_NOT_CONFIGURED", ex.code());
        }

        @Test
        @DisplayName("throws PAYMENT_METHOD_CURRENCY_MISMATCH outside the allowlist")
        void currencyMismatch() {
            config.supportedCurrencies = "ETB";
            var ex = assertThrows(ConflictException.class,
                    () -> service.buildRedirectPayload(order(), 1)); // USD order
            assertEquals("PAYMENT_METHOD_CURRENCY_MISMATCH", ex.code());
        }

        @Test
        @DisplayName("empty allowlist accepts every currency")
        void allowlistEmpty() {
            config.supportedCurrencies = "";
            assertTrue(config.supportsCurrency("USD"));
            assertTrue(config.supportsCurrency("ETB"));
            assertTrue(config.supportsCurrency("EUR"));
        }
    }

    // ---------------------------------------------------------------------
    // orderNo / invoiceFromOrderNo
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("order number helpers")
    class OrderNumbers {

        @Test
        @DisplayName("attempt 1 = invoice number, later attempts get -N")
        void orderNo() {
            var order = order();
            assertEquals("GI-2026-00001", YagoutPayService.orderNo(order, 1));
            assertEquals("GI-2026-00001-2", YagoutPayService.orderNo(order, 2));
            assertEquals("GI-2026-00001-10", YagoutPayService.orderNo(order, 10));
        }

        @Test
        @DisplayName("invoiceFromOrderNo strips numeric attempt suffixes only")
        void stripSuffix() {
            assertEquals("GI-2026-00001", YagoutPayService.invoiceFromOrderNo("GI-2026-00001-2"));
            // The invoice's own 5-digit tail is NOT an attempt suffix.
            assertEquals("GI-2026-00001", YagoutPayService.invoiceFromOrderNo("GI-2026-00001"));
            // Non-numeric suffix is left alone.
            assertEquals("GI-2026-00001-AB", YagoutPayService.invoiceFromOrderNo("GI-2026-00001-AB"));
            assertNull(YagoutPayService.invoiceFromOrderNo(null));
        }
    }

    // ---------------------------------------------------------------------
    // parseReturn
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("parseReturn")
    class ParseReturn {

        private Map<String, String> form(String txnPlain, String otherPlain) {
            var map = new java.util.HashMap<String, String>();
            map.put("me_id", ME_ID);
            if (txnPlain != null) {
                map.put("txn_response", YagoutPayCrypto.encrypt(txnPlain, KEY));
            }
            if (otherPlain != null) {
                map.put("other_details", YagoutPayCrypto.encrypt(otherPlain, KEY));
            }
            return map;
        }

        private String txnPlain(String status, String code, String msg) {
            return "yagout|" + ME_ID + "|GI-2026-00001|250.00|ETH|USD"
                    + "|2026-01-02|10:11:12|AG-12345|PG-67890|"
                    + status + "|" + code + "|" + msg;
        }

        @Test
        @DisplayName("parses a successful response incl. udf_1 order UUID")
        void success() {
            var result = service.parseReturn(
                    form(txnPlain("Successful", "0", "Successful"), "order-uuid-1||||"));
            assertNotNull(result);
            assertTrue(result.isSuccessful());
            assertEquals("order-uuid-1", result.orderUuid());
            assertEquals("GI-2026-00001", result.orderNo());
            assertEquals("250.00", result.amount());
            assertEquals("USD", result.currency());
            assertEquals("AG-12345", result.agRef());
            assertEquals("PG-67890", result.pgRef());
            assertEquals("Successful", result.resMessage());
        }

        @Test
        @DisplayName("parses a declined response")
        void decline() {
            var result = service.parseReturn(
                    form(txnPlain("Failed", "100", "Declined by issuer"), "order-uuid-1||||"));
            assertNotNull(result);
            assertFalse(result.isSuccessful());
            assertEquals("Failed", result.status());
            assertEquals("100", result.resCode());
            assertEquals("Declined by issuer", result.resMessage());
        }

        @Test
        @DisplayName("returns null when txn_response is absent")
        void missingField() {
            assertNull(service.parseReturn(Map.of("me_id", ME_ID)));
        }

        @Test
        @DisplayName("returns null on undecryptable ciphertext")
        void tamperedCiphertext() {
            assertNull(service.parseReturn(Map.of(
                    "me_id", ME_ID, "txn_response", "not-valid-base64!!!")));
        }

        @Test
        @DisplayName("returns null on me_id mismatch — never trust a foreign merchant response")
        void merchantMismatch() {
            var plain = "yagout|OTHER-MERCHANT|GI-2026-00001|250.00|ETH|USD"
                    + "|2026-01-02|10:11:12|AG-1|PG-1|Successful|0|Successful";
            assertNull(service.parseReturn(form(plain, null)));
        }

        @Test
        @DisplayName("returns null on truncated field count")
        void truncated() {
            assertNull(service.parseReturn(form("yagout|" + ME_ID + "|GI-1", null)));
        }

        @Test
        @DisplayName("still parses when other_details is missing or undecryptable")
        void missingOtherDetails() {
            var form = form(txnPlain("Successful", "0", "OK"), null);
            var result = service.parseReturn(form);
            assertNotNull(result);
            assertNull(result.orderUuid());

            form.put("other_details", "garbage-ciphertext");
            result = service.parseReturn(form);
            assertNotNull(result);
            assertNull(result.orderUuid());
        }

        @Test
        @DisplayName("returns null when the gateway is not configured")
        void notConfigured() {
            config.enabled = false;
            assertNull(service.parseReturn(form(txnPlain("Successful", "0", "OK"), null)));
        }
    }
}
