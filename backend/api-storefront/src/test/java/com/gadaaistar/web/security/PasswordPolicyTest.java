package com.gadaaistar.web.security;

import java.lang.reflect.Proxy;

import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.gadaaistar.common.exceptions.ValidationException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PasswordPolicy} (INSA CWE-521 remediation).
 *
 * <p>Uses reflection to inject config values — no CDI/Quarkus runtime needed.
 * Verifies that the policy rejects weak passwords and accepts strong ones
 * across all the rules: length, uppercase, lowercase, digit, special, and
 * the common-password blocklist.
 */
class PasswordPolicyTest {

    private PasswordPolicy policy;

    @BeforeEach
    void setUp() throws Exception {
        policy = new PasswordPolicy();
        setField("minLength", 8);
        setField("maxLength", 128);
        setField("requireUppercase", true);
        setField("requireLowercase", true);
        setField("requireDigit", true);
        setField("requireSpecial", true);
        setField("blockCommon", true);
    }

    private void setField(String name, Object value) throws Exception {
        var f = PasswordPolicy.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(policy, value);
    }

    @Nested
    @DisplayName("rejects weak passwords")
    class Rejects {

        @Test
        @DisplayName("null password")
        void nullPassword() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate(null, "password"));
            assertEquals("VALIDATION_FAILED", ex.code());
            assertEquals(400, ex.status());
        }

        @Test
        @DisplayName("empty password")
        void emptyPassword() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("", "password"));
            assertEquals("VALIDATION_FAILED", ex.code());
        }

        @Test
        @DisplayName("INSA test case: '12345678' — no uppercase, no lowercase, no special")
        void insaTestCase() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("12345678", "password"));
            assertEquals("VALIDATION_FAILED", ex.code());
            assertTrue(ex.getMessage().contains("uppercase"));
            assertTrue(ex.getMessage().contains("lowercase"));
            assertTrue(ex.getMessage().contains("special"));
        }

        @Test
        @DisplayName("too short even with all character classes")
        void tooShort() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("aA1!bB2", "password"));
            assertTrue(ex.getMessage().contains("at least 8 characters"));
        }

        @Test
        @DisplayName("missing uppercase")
        void missingUppercase() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("lowercase1!", "password"));
            assertTrue(ex.getMessage().contains("uppercase"));
        }

        @Test
        @DisplayName("missing lowercase")
        void missingLowercase() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("UPPERCASE1!", "password"));
            assertTrue(ex.getMessage().contains("lowercase"));
        }

        @Test
        @DisplayName("missing digit")
        void missingDigit() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("Abcdefgh!k", "password"));
            assertTrue(ex.getMessage().contains("digit"));
        }

        @Test
        @DisplayName("missing special character")
        void missingSpecial() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("Abcdefgh1k", "password"));
            assertTrue(ex.getMessage().contains("special"));
        }

        @Test
        @DisplayName("common password 'Password1!' — on blocklist, passes complexity")
        void commonPassword() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("Password1!", "password"));
            assertTrue(ex.getMessage().contains("too common"));
        }

        @Test
        @DisplayName("common password 'Qwerty1234!' — on blocklist, passes complexity")
        void commonPassword2() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("Qwerty1234!", "password"));
            assertTrue(ex.getMessage().contains("too common"));
        }
    }

    @Nested
    @DisplayName("accepts strong passwords")
    class Accepts {

        @Test
        @DisplayName("typical strong password")
        void strongPassword() {
            assertDoesNotThrow(() -> policy.validate("MyStr0ng!Pa$$word", "password"));
        }

        @Test
        @DisplayName("minimum-length strong password (exactly 8 chars)")
        void minimumLength() {
            assertDoesNotThrow(() -> policy.validate("aA1!bcde", "password"));
        }

        @Test
        @DisplayName("password with many special characters")
        void manySpecials() {
            assertDoesNotThrow(() -> policy.validate("P@$$w0rd!!", "password"));
        }
    }

    @Nested
    @DisplayName("configurable rules")
    class Configurable {

        @Test
        @DisplayName("disabling all complexity rules only checks length")
        void relaxedPolicy() throws Exception {
            setField("requireUppercase", false);
            setField("requireLowercase", false);
            setField("requireDigit", false);
            setField("requireSpecial", false);
            setField("blockCommon", false);

            // 8 chars, no complexity — should pass under relaxed policy
            assertDoesNotThrow(() -> policy.validate("abcdefgh", "password"));
        }

        @Test
        @DisplayName("lowering min-length to 6 accepts shorter passwords")
        void lowerMinLength() throws Exception {
            setField("minLength", 6);
            // 6 chars with all classes
            assertDoesNotThrow(() -> policy.validate("aA1!bc", "password"));
        }

        @Test
        @DisplayName("disabling block-common allows previously-blocked passwords")
        void disableBlockCommon() throws Exception {
            setField("blockCommon", false);
            // "Password1!" is on the blocklist but passes all complexity rules
            // — should pass when blocklist is disabled
            assertDoesNotThrow(() -> policy.validate("Password1!", "password"));
        }
    }

    @Nested
    @DisplayName("field name in error details")
    class FieldName {

        @Test
        @DisplayName("uses 'password' for register")
        void registerField() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("12345678", "password"));
            assertNotNull(ex.details().get("password"));
        }

        @Test
        @DisplayName("uses 'newPassword' for change/reset")
        void newPasswordField() {
            var ex = assertThrows(ValidationException.class,
                    () -> policy.validate("12345678", "newPassword"));
            assertNotNull(ex.details().get("newPassword"));
        }
    }
}
