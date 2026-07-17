package com.gadaaistar.admin.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Self-contained RFC 6238 TOTP implementation (30-second step, 6-digit code,
 * HMAC-SHA1). Compatible with Google Authenticator, Authy, 1Password, etc.
 *
 * <p>We deliberately do not pull in a TOTP library — the algorithm is small,
 * heavily standardized, and easier to audit inline than a transitive dep.
 *
 * <p>Base32 encoding (RFC 4648, uppercase, no padding) is implemented locally
 * to keep the class dependency-free.
 */
@ApplicationScoped
public class TotpService {

    /** RFC 6238 recommends 30 seconds. */
    private static final long TIME_STEP_SECONDS = 30L;

    /** 6-digit codes, matching Google Authenticator default. */
    private static final int CODE_DIGITS = 6;

    /** 20 raw bytes = 32 base32 characters — RFC 4226 minimum, Google Auth-compatible. */
    private static final int SECRET_BYTES = 20;

    /** RFC 4648 base32 alphabet. */
    private static final char[] BASE32_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private static final SecureRandom RNG = new SecureRandom();

    @ConfigProperty(name = "gadaa.admin.mfa.window-tolerance", defaultValue = "1")
    int windowTolerance;

    @ConfigProperty(name = "gadaa.admin.mfa.issuer", defaultValue = "Gadaa Istar Admin")
    String issuer;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Generate a fresh 20-byte secret, base32-encoded. */
    public String generateSecret() {
        var bytes = new byte[SECRET_BYTES];
        RNG.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /**
     * Build an {@code otpauth://totp/…} provisioning URI for QR code display.
     * Encodes the issuer/account label per Google's Key URI Format spec.
     */
    public String buildProvisioningUri(String base32Secret, String accountEmail) {
        var label = issuer + ":" + accountEmail;
        return "otpauth://totp/" + urlEncode(label)
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + CODE_DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    /**
     * Verify a 6-digit code against the secret, sliding
     * {@code windowTolerance} steps in either direction to account for clock skew.
     */
    public boolean verifyCode(String base32Secret, String code) {
        if (base32Secret == null || code == null) return false;
        var cleaned = code.trim().replaceAll("\\s+", "");
        if (!cleaned.matches("\\d{" + CODE_DIGITS + "}")) return false;
        int expected;
        try {
            expected = Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return false;
        }

        long counter = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        byte[] key = base32Decode(base32Secret);

        // constant-time comparison across the tolerance window
        int hits = 0;
        for (long delta = -windowTolerance; delta <= windowTolerance; delta++) {
            int candidate = generateCode(key, counter + delta);
            // We deliberately don't short-circuit — we want to keep timing uniform.
            if (candidate == expected) hits |= 1;
        }
        return hits == 1;
    }

    // -------------------------------------------------------------------------
    // TOTP core
    // -------------------------------------------------------------------------

    private static int generateCode(byte[] key, long counter) {
        byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
        byte[] hash;
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            hash = mac.doFinal(counterBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset]     & 0x7F) << 24) |
                     ((hash[offset + 1] & 0xFF) << 16) |
                     ((hash[offset + 2] & 0xFF) <<  8) |
                     ( hash[offset + 3] & 0xFF);
        int modulus = (int) Math.pow(10, CODE_DIGITS);
        return binary % modulus;
    }

    // -------------------------------------------------------------------------
    // Base32 (RFC 4648, no padding)
    // -------------------------------------------------------------------------

    private static String base32Encode(byte[] data) {
        var sb = new StringBuilder(((data.length * 8 + 4) / 5));
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                sb.append(BASE32_ALPHABET[index]);
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_ALPHABET[index]);
        }
        return sb.toString();
    }

    private static byte[] base32Decode(String s) {
        if (s == null) return new byte[0];
        var clean = s.trim().toUpperCase().replaceAll("[^A-Z2-7]", "");
        var out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int idx = 0;
        for (int i = 0; i < clean.length(); i++) {
            int v = indexOfBase32(clean.charAt(i));
            if (v < 0) continue;
            buffer = (buffer << 5) | v;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[idx++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out;
    }

    private static int indexOfBase32(char c) {
        for (int i = 0; i < BASE32_ALPHABET.length; i++) {
            if (BASE32_ALPHABET[i] == c) return i;
        }
        return -1;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
