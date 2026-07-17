package com.gadaaistar.storefront.ratelimit;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Sliding-window rate limiter — public entry point used by {@code RateLimitFilter}.
 *
 * <p>Behaves as a thin selector shim over a pluggable {@link RateLimitBackend}
 * so multi-instance deployments can swap the process-local in-memory store
 * for a shared Redis-backed one at runtime without touching call sites or
 * the {@code @RateLimit} annotation. The {@link #tryAcquire} signature and
 * {@link Decision} record stayed identical across the Phase 4d.6 refactor.
 *
 * <h4>Backend selection</h4>
 * Driven by {@code gadaa.ratelimit.backend}:
 * <ul>
 *   <li>{@code memory} (default) — {@link InMemoryRateLimitBackend}. Fine for
 *       single-instance dev / staging.</li>
 *   <li>{@code redis} — {@link RedisRateLimitBackend}. Required for
 *       multi-instance production; the Redis extension must be configured
 *       (see {@code quarkus.redis.hosts}). Fail-open on Redis errors.</li>
 * </ul>
 *
 * <h4>Global on/off</h4>
 * {@code gadaa.ratelimit.enabled=false} short-circuits every call to
 * {@link Decision#ALLOWED} regardless of backend. Intended for load tests
 * where the limiter itself is not what we want to measure.
 *
 * <h4>Metrics (Phase 5.3)</h4>
 * Exposes Prometheus counters via Micrometer:
 * <ul>
 *   <li>{@code gi_ratelimit_requests_total{backend,result}} — incremented on
 *       every tryAcquire call; result is "allowed" or "rejected".</li>
 *   <li>{@code gi_ratelimit_active_keys{backend}} — gauge reporting the active
 *       key count (memory backend only; Redis returns -1).</li>
 * </ul>
 */
@ApplicationScoped
public class RateLimitService {

    private static final Logger LOG = Logger.getLogger(RateLimitService.class);

    /** Global master switch. Set to false in tests / benchmarks. */
    @ConfigProperty(name = "gadaa.ratelimit.enabled", defaultValue = "true")
    boolean enabled;

    /** Which backend to use: "memory" (default) or "redis". */
    @ConfigProperty(name = "gadaa.ratelimit.backend", defaultValue = "memory")
    String backendName;

    @Inject InMemoryRateLimitBackend memoryBackend;
    // Redis backend is looked up via CDI Instance<> so it stays optional —
    // if the quarkus-redis-client extension were removed the memory backend
    // would keep working.
    @Inject jakarta.enterprise.inject.Instance<RedisRateLimitBackend> redisBackend;
    @Inject MeterRegistry meterRegistry;

    private RateLimitBackend active;

    // Metrics
    private Counter allowedCounter;
    private Counter rejectedCounter;
    private final AtomicLong activeKeysGaugeValue = new AtomicLong(0);

    @PostConstruct
    void init() {
        if (!enabled) {
            LOG.warn("Rate limiting is DISABLED (gadaa.ratelimit.enabled=false). " +
                     "This should only happen in tests or when explicitly bypassed.");
        }

        if ("redis".equalsIgnoreCase(backendName)) {
            if (redisBackend.isResolvable()) {
                active = redisBackend.get();
                LOG.infof("Rate-limit backend: %s (multi-instance safe)", active.label());
            } else {
                LOG.warn("gadaa.ratelimit.backend=redis but the Redis backend bean is not resolvable " +
                         "(quarkus-redis-client missing?). Falling back to memory.");
                active = memoryBackend;
            }
        } else {
            active = memoryBackend;
            LOG.infof("Rate-limit backend: %s (single-instance only)", active.label());
        }

        // --- Micrometer counters ---
        var backendTag = Tags.of("backend", active.label());
        allowedCounter = meterRegistry.counter("gi_ratelimit_requests_total",
                backendTag.and("result", "allowed"));
        rejectedCounter = meterRegistry.counter("gi_ratelimit_requests_total",
                backendTag.and("result", "rejected"));

        // --- Active keys gauge ---
        meterRegistry.gauge("gi_ratelimit_active_keys", backendTag, activeKeysGaugeValue);
    }

    /**
     * Try to record a hit against a bucket for a given client.
     *
     * @param bucket   logical bucket name (see {@code @RateLimit(bucket=…)})
     * @param clientId caller identity — usually the client IP
     * @param max      max hits permitted inside the window
     * @param window   window duration
     * @return         {@link Decision#allowed()} — true if the caller is under the limit
     */
    public Decision tryAcquire(String bucket, String clientId, int max, Duration window) {
        if (!enabled) return Decision.ALLOWED;
        var decision = active.tryAcquire(bucket, clientId, max, window);

        if (decision.allowed()) {
            allowedCounter.increment();
        } else {
            rejectedCounter.increment();
        }

        return decision;
    }

    /**
     * Test / diagnostics — number of tracked keys in the active backend.
     * Returns {@code -1} for the Redis backend (SCAN would be O(keyspace)).
     */
    public int activeKeyCount() {
        int count = active == null ? 0 : active.activeKeyCount();
        activeKeysGaugeValue.set(count);
        return count;
    }

    /** Human-readable name of the active backend (for logs and /q/health). */
    public String activeBackendLabel() {
        return active == null ? "uninitialised" : active.label();
    }

    /**
     * Outcome of a {@link #tryAcquire(String, String, int, Duration)} call.
     *
     * @param allowed            whether the request is permitted
     * @param retryAfterSeconds  hint for the {@code Retry-After} response header;
     *                           {@code 0} when allowed
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {
        public static final Decision ALLOWED = new Decision(true, 0L);
    }
}
