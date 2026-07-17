package com.gadaaistar.storefront.ratelimit;

import java.time.Duration;

import com.gadaaistar.storefront.ratelimit.RateLimitService.Decision;

/**
 * Strategy interface for the rate-limit sliding-window store.
 *
 * <p>Two implementations ship:
 * <ul>
 *   <li>{@link InMemoryRateLimitBackend} — process-local {@code ConcurrentHashMap}.
 *       Fast, no external dependency, but resets on restart and gives every
 *       replica its own quota (so behind a load balancer the effective limit
 *       is {@code N × max}).</li>
 *   <li>{@link RedisRateLimitBackend} — Redis-backed sorted-set sliding
 *       window driven by a single atomic Lua script. Multi-instance safe
 *       and shared across replicas.</li>
 * </ul>
 *
 * <p>{@link RateLimitService} picks between them at boot based on the
 * {@code gadaa.ratelimit.backend} config value.
 */
public interface RateLimitBackend {

    /**
     * Try to record a hit against a bucket for a given client.
     *
     * @param bucket   logical bucket name (see {@code @RateLimit(bucket=…)})
     * @param clientId caller identity — usually the client IP
     * @param max      max hits permitted inside the window
     * @param window   window duration
     * @return         {@link Decision#allowed()} — true if the caller is under the limit
     */
    Decision tryAcquire(String bucket, String clientId, int max, Duration window);

    /**
     * Diagnostic — number of currently tracked keys. Backends that can't
     * cheaply enumerate keys (Redis) may return {@code -1} to indicate
     * "not tracked here".
     */
    int activeKeyCount();

    /**
     * Human-readable label surfaced in logs at startup and in the metrics
     * {@code /q/health} envelope. Should be one of {@code "memory"} or
     * {@code "redis"}.
     */
    String label();
}
