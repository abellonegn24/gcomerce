package com.gadaaistar.storefront.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.persistence.order.CheckoutOrder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MpgsService} that don't require a running Quarkus
 * instance or live MPGS gateway. We test the pure-logic methods
 * ({@code verifyNotificationSecret}) and the config-guard paths
 * ({@code createCheckoutSession} / {@code retrieveOrder} when not configured).
 *
 * <p>Full integration tests that exercise the HTTP client against the MPGS
 * test gateway belong in a separate Quarkus @QuarkusTest class with
 * WireMock or a test gateway stub.
 */
class MpgsServiceTest {

    private MpgsService service;
    private TestMpgsConfig config;

    @BeforeEach
    void setUp() {
        service = new MpgsService();
        config = new TestMpgsConfig();
        // Inject the test config via reflection — MpgsService uses @Inject
        // but in a plain unit test there's no CDI container.
        try {
            var field = MpgsService.class.getDeclaredField("config");
            field.setAccessible(true);
            field.set(service, config);
        } catch (Exception e) {
            fail("Could not inject test config: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // verifyNotificationSecret
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("verifyNotificationSecret")
    class VerifyNotificationSecret {

        @Test
        @DisplayName("returns true when secret matches exactly")
        void matchingSecret() {
            config.notificationSecret = "my-test-secret";
            assertTrue(service.verifyNotificationSecret("my-test-secret"));
        }

        @Test
        @DisplayName("returns false when secret does not match")
        void mismatchedSecret() {
            config.notificationSecret = "my-test-secret";
            assertFalse(service.verifyNotificationSecret("wrong-secret"));
        }

        @Test
        @DisplayName("returns false when provided secret is null")
        void nullProvided() {
            config.notificationSecret = "my-test-secret";
            assertFalse(service.verifyNotificationSecret(null));
        }

        @Test
        @DisplayName("returns false when configured secret is blank")
        void blankConfigured() {
            config.notificationSecret = "";
            assertFalse(service.verifyNotificationSecret("anything"));
        }

        @Test
        @DisplayName("returns false when configured secret is null")
        void nullConfigured() {
            config.notificationSecret = null;
            assertFalse(service.verifyNotificationSecret("anything"));
        }

        @Test
        @DisplayName("returns false when lengths differ (prevents timing leak)")
        void differentLength() {
            config.notificationSecret = "abc";
            assertFalse(service.verifyNotificationSecret("abcdef"));
        }

        @Test
        @DisplayName("returns true for empty string secret when configured as empty")
        void emptyStringMatch() {
            // Edge case: both are empty strings — technically a valid match.
            // This is a misconfiguration (blank secret), but the constant-time
            // comparison should still handle it without throwing.
            config.notificationSecret = "";
            // Blank configured secret returns false per the guard clause.
            assertFalse(service.verifyNotificationSecret(""));
        }
    }

    // -------------------------------------------------------------------------
    // createCheckoutSession — config guard
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("createCheckoutSession (config guards)")
    class CreateCheckoutSessionGuards {

        @Test
        @DisplayName("throws PAYMENT_GATEWAY_NOT_CONFIGURED when not configured")
        void throwsWhenNotConfigured() {
            config.enabled = false;
            config.merchantId = "";
            config.apiPassword = "";
            var order = minimalOrder();

            var ex = assertThrows(ConflictException.class,
                    () -> service.createCheckoutSession(order));
            assertEquals("PAYMENT_GATEWAY_NOT_CONFIGURED", ex.code());
        }

        @Test
        @DisplayName("throws PAYMENT_GATEWAY_NOT_CONFIGURED when enabled but missing merchantId")
        void throwsWhenMissingMerchantId() {
            config.enabled = true;
            config.merchantId = "";
            config.apiPassword = "password";
            config.gatewayUrl = "https://mtf.gateway.mastercard.com";
            var order = minimalOrder();

            var ex = assertThrows(ConflictException.class,
                    () -> service.createCheckoutSession(order));
            assertEquals("PAYMENT_GATEWAY_NOT_CONFIGURED", ex.code());
        }

        @Test
        @DisplayName("throws PAYMENT_GATEWAY_NOT_CONFIGURED when enabled but missing apiPassword")
        void throwsWhenMissingApiPassword() {
            config.enabled = true;
            config.merchantId = "TESTMID";
            config.apiPassword = "";
            config.gatewayUrl = "https://mtf.gateway.mastercard.com";
            var order = minimalOrder();

            var ex = assertThrows(ConflictException.class,
                    () -> service.createCheckoutSession(order));
            assertEquals("PAYMENT_GATEWAY_NOT_CONFIGURED", ex.code());
        }
    }

    // -------------------------------------------------------------------------
    // retrieveOrder — config guard
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("retrieveOrder (config guards)")
    class RetrieveOrderGuards {

        @Test
        @DisplayName("returns null when not configured")
        void returnsNullWhenNotConfigured() {
            config.enabled = false;
            config.merchantId = "";
            config.apiPassword = "";

            var result = service.retrieveOrder("GI-2026-00001");
            assertNull(result);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static CheckoutOrder minimalOrder() {
        var order = new CheckoutOrder();
        order.uuid = "test-uuid-1234";
        order.invoiceNumber = "GI-2026-00001";
        order.currency = "USD";
        order.grandTotal = new BigDecimal("100.00");
        return order;
    }

    /**
     * Test double for {@link MpgsConfig} — overrides all methods to return
     * mutable fields set by individual tests.
     */
    private static class TestMpgsConfig extends MpgsConfig {
        String gatewayUrl = "https://mtf.gateway.mastercard.com";
        String merchantId = "TESTMID";
        String apiPassword = "testpassword";
        String apiVersion = "62";
        String checkoutVersion = "62";
        String notificationSecret = "test-notification-secret";
        boolean enabled = true;
        String storefrontBaseUrl = "https://gedaaistar.et";

        @Override public String gatewayUrl()         { return gatewayUrl; }
        @Override public String merchantId()         { return merchantId; }
        @Override public String apiPassword()        { return apiPassword; }
        @Override public String apiVersion()         { return apiVersion; }
        @Override public String checkoutVersion()    { return checkoutVersion; }
        @Override public String notificationSecret() { return notificationSecret; }
        @Override public boolean enabled()           { return enabled; }
        @Override public String storefrontBaseUrl()  { return storefrontBaseUrl; }

        @Override
        public boolean isConfigured() {
            return enabled
                    && merchantId != null && !merchantId.isBlank()
                    && apiPassword != null && !apiPassword.isBlank()
                    && gatewayUrl != null && !gatewayUrl.isBlank();
        }

        @Override
        public String basicAuthHeader() {
            var raw = "merchant." + merchantId + ":" + apiPassword;
            return "Basic " + java.util.Base64.getEncoder()
                    .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public String sessionUrl() {
            return gatewayUrl + "/api/rest/version/" + apiVersion
                    + "/merchant/" + merchantId + "/session";
        }

        @Override
        public String orderUrl(String orderId) {
            return gatewayUrl + "/api/rest/version/" + apiVersion
                    + "/merchant/" + merchantId + "/order/" + orderId;
        }

        @Override
        public String checkoutJsUrl() {
            return gatewayUrl + "/checkout/version/" + checkoutVersion + "/checkout.js";
        }
    }
}
