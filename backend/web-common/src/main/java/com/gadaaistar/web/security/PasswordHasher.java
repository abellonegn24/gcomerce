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
}
