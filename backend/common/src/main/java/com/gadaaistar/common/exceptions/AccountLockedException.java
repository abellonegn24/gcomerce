package com.gadaaistar.common.exceptions;

import java.util.Map;

/**
 * Thrown when a login attempt is rejected because the account is temporarily
 * locked due to repeated failed authentication attempts (INSA CWE-307).
 *
 * <p>Maps to HTTP {@code 429 TOO_MANY_REQUESTS}. The {@link #retryAfterSeconds()}
 * hint is surfaced as the {@code Retry-After} response header by
 * {@code GlobalExceptionMapper} (mirroring {@link RateLimitExceededException}).
 *
 * <p>Lockout is per-account (not per-IP) and progressive — each successive
 * lockout doubles the duration, capped at a configured maximum.
 */
public class AccountLockedException extends ApiException {

    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super(
            "ACCOUNT_LOCKED",
            429,
            "This account is temporarily locked due to repeated failed login attempts. Please try again later.",
            Map.of("retryAfterSeconds", Math.max(1, retryAfterSeconds))
        );
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
