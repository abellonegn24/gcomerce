package com.gadaaistar.common.exceptions;

import java.util.Map;

/**
 * Thrown when a request exceeds a rate-limit bucket.
 *
 * <p>Maps to HTTP {@code 429 TOO_MANY_REQUESTS}. The {@link #retryAfterSeconds()}
 * hint is added to the response body <em>and</em> surfaced as the
 * {@code Retry-After} header by {@code GlobalExceptionMapper}.
 */
public class RateLimitExceededException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String bucket, long retryAfterSeconds) {
        super(
            "RATE_LIMIT_EXCEEDED",
            429,
            "Too many requests. Please wait and try again.",
            Map.of(
                "bucket", bucket,
                "retryAfterSeconds", retryAfterSeconds
            )
        );
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
