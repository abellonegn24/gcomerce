package com.gadaaistar.storefront.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-CBC encrypt/decrypt primitives for the YagoutPay wire format.
 *
 * <p>Per the integration document all parameters cross the wire
 * AES-CBC-encrypted under the merchant encryption key:
 * <ul>
 *   <li>Key = {@code base64decode(encryption_key)} — 16, 24, or 32 bytes
 *       selects AES-128/192/256 (the reference implementation auto-detects
 *       on decoded length; JCA does the same via the key size).</li>
 *   <li>IV = the fixed ASCII string {@code "0123456789abcdef"} (16 bytes).</li>
 *   <li>Padding = PKCS7 — Java's {@code PKCS5Padding} is byte-identical for
 *       AES's 16-byte block size.</li>
 *   <li>Ciphertext = base64.</li>
 * </ul>
 *
 * <p>The {@code hash} field is the same AES encryption applied to the
 * lowercase hex SHA-256 of {@code me_id~order_no~amount~country~currency}.
 */
public final class YagoutPayCrypto {

    private static final byte[] IV = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private YagoutPayCrypto() {}

    /** AES-CBC encrypt {@code plaintext} and return base64 ciphertext. */
    public static String encrypt(String plaintext, String base64Key) {
        try {
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(base64Key), new IvParameterSpec(IV));
            var out = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (IllegalArgumentException e) {
            throw e; // bad key — surfaced by caller
        } catch (Exception e) {
            throw new IllegalStateException("AES encryption failed", e);
        }
    }

    /** Decrypt a base64 AES-CBC ciphertext back to UTF-8 plaintext. */
    public static String decrypt(String base64Cipher, String base64Key) {
        try {
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(base64Key), new IvParameterSpec(IV));
            var out = cipher.doFinal(Base64.getDecoder().decode(base64Cipher));
            return new String(out, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e; // bad base64 / bad key — surfaced by caller
        } catch (Exception e) {
            throw new IllegalStateException("AES decryption failed", e);
        }
    }

    /** Lowercase hex SHA-256 — the plaintext that gets encrypted into {@code hash}. */
    public static String sha256Hex(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
        }
    }

    private static SecretKeySpec keySpec(String base64Key) {
        var keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "YagoutPay encryption key must decode to 16, 24 or 32 bytes "
                            + "(got " + keyBytes.length + ")");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
