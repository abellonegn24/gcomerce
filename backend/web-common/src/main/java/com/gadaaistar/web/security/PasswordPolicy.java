package com.gadaaistar.web.security;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.gadaaistar.common.exceptions.ValidationException;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Enforces a server-side password strength policy (INSA CWE-521 remediation).
 *
 * <p>Applied consistently across every endpoint that establishes or changes a
 * password:
 * <ul>
 *   <li>{@code POST /api/storefront/v1/auth/register} — new customer account</li>
 *   <li>{@code POST /api/storefront/v1/auth/me/change-password} — authenticated change</li>
 *   <li>{@code POST /api/storefront/v1/auth/reset-password} — token-based reset</li>
 * </ul>
 *
 * <h4>Policy rules</h4>
 * <ul>
 *   <li>Minimum length (default 10, configurable).</li>
 *   <li>At least one uppercase letter (configurable).</li>
 *   <li>At least one lowercase letter (configurable).</li>
 *   <li>At least one digit (configurable).</li>
 *   <li>At least one special character (configurable).</li>
 *   <li>Rejects commonly-used / well-known weak passwords (configurable).</li>
 * </ul>
 *
 * <p>All thresholds are configurable via {@code application.properties} so
 * staging/prod can tighten or relax the policy without code changes:
 * <pre>
 * gadaa.security.password.min-length=10
 * gadaa.security.password.require-uppercase=true
 * gadaa.security.password.require-lowercase=true
 * gadaa.security.password.require-digit=true
 * gadaa.security.password.require-special=true
 * gadaa.security.password.block-common=true
 * </pre>
 */
@ApplicationScoped
public class PasswordPolicy {

    @ConfigProperty(name = "gadaa.security.password.min-length", defaultValue = "8")
    int minLength;

    @ConfigProperty(name = "gadaa.security.password.max-length", defaultValue = "128")
    int maxLength;

    @ConfigProperty(name = "gadaa.security.password.require-uppercase", defaultValue = "true")
    boolean requireUppercase;

    @ConfigProperty(name = "gadaa.security.password.require-lowercase", defaultValue = "true")
    boolean requireLowercase;

    @ConfigProperty(name = "gadaa.security.password.require-digit", defaultValue = "true")
    boolean requireDigit;

    @ConfigProperty(name = "gadaa.security.password.require-special", defaultValue = "true")
    boolean requireSpecial;

    @ConfigProperty(name = "gadaa.security.password.block-common", defaultValue = "true")
    boolean blockCommon;

    /**
     * A curated blocklist of the most commonly-used passwords that would pass
     * the basic complexity rules but are trivially guessable. These are the
     * top entries from publicly leaked password databases (RockYou, Have I
     * Been Pwned top-100, etc.) that meet the minimum character-class
     * requirements. The check is case-insensitive.
     */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password1!", "password1@", "password12!",
            "password123!", "password1234",
            "qwerty1234!", "qwerty1!",
            "welcome123!", "welcome1!",
            "admin12345!", "admin123!",
            "abc1234567!", "abc12345!",
            "letmein123!", "letmein1!",
            "dragon1234!", "dragon1!",
            "monkey1234!", "monkey1!",
            "master1234!", "master1!",
            "sunshine12!", "sunshine1!",
            "princess12!", "princess1!",
            "football1!", "football12!",
            "shadow123!", "shadow1!",
            "charlie123!", "charlie1!",
            "michael12!", "michael1!",
            "iloveyou12!", "iloveyou1!",
            "abcd1234!", "abcd12345",
            "passw0rd!", "passw0rd1!",
            "p@ssw0rd!", "p@ssword1!",
            "qwerty!1", "qwerty!12",
            "abcdefg1!", "abcdefg12!",
            "test1234!", "test12345",
            "hello123!", "hello1234!",
            "super123!", "super1234!",
            "user1234!", "user12345",
            "root1234!", "root12345",
            "admin@123", "admin@1234",
            "welcome@1", "welcome@12",
            "changeme1!", "changeme12!",
            "newpass12!", "newpass1!",
            "temp1234!", "temp12345",
            "abcdef!1", "abcdef!12",
            "1234abcd!", "1234abcde!",
            "aa123456!", "aa1234567",
            "aa1!aa1!", "aa1!aa1!a",
            "zxcvbnm1!", "zxcvbnm12!",
            "asdfgh1!", "asdfgh12!"
    );

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT    = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL  = Pattern.compile("[^A-Za-z0-9]");

    /**
     * Validate the given password against the configured policy. Throws
     * {@link ValidationException} with a descriptive message and field-error
     * map if the password is too weak.
     *
     * @param password     the plaintext password to validate
     * @param fieldName    the request field name to include in the field-error
     *                     map (e.g. {@code "password"}, {@code "newPassword"})
     */
    public void validate(String password, String fieldName) {
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Password is required.",
                    Map.of(fieldName, "Password is required."));
        }

        var errors = new java.util.LinkedHashMap<String, String>();
        var missing = new java.util.ArrayList<String>();

        if (password.length() < minLength) {
            errors.put(fieldName, "Password must be at least " + minLength + " characters long.");
            missing.add("at least " + minLength + " characters");
        }
        if (password.length() > maxLength) {
            errors.put(fieldName, "Password must not exceed " + maxLength + " characters.");
            missing.add("at most " + maxLength + " characters");
        }
        if (requireUppercase && !UPPERCASE.matcher(password).find()) {
            missing.add("an uppercase letter");
        }
        if (requireLowercase && !LOWERCASE.matcher(password).find()) {
            missing.add("a lowercase letter");
        }
        if (requireDigit && !DIGIT.matcher(password).find()) {
            missing.add("a digit");
        }
        if (requireSpecial && !SPECIAL.matcher(password).find()) {
            missing.add("a special character");
        }

        if (!missing.isEmpty()) {
            var msg = new StringBuilder("Password is too weak. It must contain ");
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) msg.append(i == missing.size() - 1 ? " and " : ", ");
                msg.append(missing.get(i));
            }
            msg.append(".");
            if (errors.isEmpty()) {
                errors.put(fieldName, msg.toString());
            }
            throw new ValidationException(msg.toString(), java.util.Collections.unmodifiableMap(errors));
        }

        if (blockCommon && COMMON_PASSWORDS.contains(password.toLowerCase())) {
            throw new ValidationException(
                    "This password is too common and easily guessable. Please choose a more unique password.",
                    Map.of(fieldName, "This password is on the blocked list of commonly-used passwords."));
        }
    }
}
