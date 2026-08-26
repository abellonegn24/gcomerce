package com.gadaaistar.web.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Thin wrapper around Favre's BCrypt implementation.
 * Cost 12 is the Gadaa Istar baseline; adjust in one place if hardware evolves.
 */
@ApplicationScoped
public class PasswordHasher {

    private static final int BCRYPT_COST = 12;

    /**
     * Precomputed BCrypt hash (cost 12) of a throwaway password. Generated
     * once at class load so it is always a syntactically valid hash for the
     * configured BCrypt implementation. Used by {@link #verifyDummy()} to
     * burn the same CPU time as a real {@link #verify(String, String)} call
     * on the "email not found" login path, defeating user-enumeration by
     * response timing (INSA CWE-307 hardening).
     */
    private static final String DUMMY_HASH =
            BCrypt.withDefaults().hashToString(BCRYPT_COST, "dummy-password-12345678".toCharArray());

    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, rawPassword.toCharArray());
    }

    public boolean verify(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) return false;
        var result = BCrypt.verifyer().verify(rawPassword.toCharArray(), storedHash);
        return result.verified;
    }

    /**
     * Run a BCrypt verify against a fixed throwaway hash so that the
     * "email not found" branch of a login flow takes the same time as the
     * "wrong password" branch. This defeats user-enumeration timing attacks
     * (INSA CWE-307 hardening). The return value is intentionally ignored.
     */
    public void verifyDummy() {
        BCrypt.verifyer().verify("dummy-password-12345678".toCharArray(), DUMMY_HASH);
    }
}
