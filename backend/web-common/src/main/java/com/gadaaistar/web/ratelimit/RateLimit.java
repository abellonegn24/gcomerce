package com.gadaaistar.web.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rate-limit a JAX-RS resource method (or every method on a resource class).
 *
 * <p>Enforced by {@link RateLimitFilter} using the sliding-window
 * {@link RateLimitService}. The identity for the bucket key is the caller's
 * IP (first entry of {@code X-Forwarded-For}, else the socket remote address).
 *
 * <h4>Example</h4>
 * <pre>{@code
 * @POST @Path("/login")
 * @RateLimit(bucket = "auth-login", max = 10, windowSeconds = 60)
 * public AuthResponse login(...) { ... }
 * }</pre>
 *
 * <p><strong>Scope:</strong> {@link RateLimitService} picks between an
 * in-memory sliding window (default) and a Redis-backed one at runtime via
 * {@code gadaa.ratelimit.backend}. Multi-instance production deploys should
 * set that to {@code redis}; single-instance dev / staging can stay on
 * memory.
 *
 * <p><strong>Location:</strong> lives in {@code web-common} so both the
 * storefront and admin Quarkus runtimes can apply it to their endpoints.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RateLimit {

    /** Bucket name — groups related endpoints (e.g. all password-reset attempts). */
    String bucket();

    /** Maximum requests allowed within {@link #windowSeconds()} for a single client. */
    int max() default 30;

    /** Sliding window duration in seconds. */
    int windowSeconds() default 60;
}
