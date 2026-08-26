package com.gadaaistar.web.ratelimit;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Sliding-window rate limiter — public entry point used by {@link RateLimitFilter}.
 *
 * <p>Behaves as a thin selector shim over a pluggable {@link RateLimitBackend}
 * so multi-instance deployments can swap the process-local in-memory store
 * for a shared Redis-backed one at runtime without touching call sites or
 * the {@code @RateLimit} annotation.
 *
 * <h4>Backend selection</h4>
 * Driven by {@code gadaa.ratelimit.backend}:
 * <ul>
 *   <li>{@code memory} (default) — {@link InMemoryRateLimitBackend}. Fine for
 *       single-instance dev / staging.</li>
 *   <li>{@code redis} — any {@link RateLimitBackend} whose {@link RateLimitBackend#label()}
 *       returns {@code "redis"} (the concrete impl lives in {@code api-storefront}
 *       and is discovered via CDI). Required for multi-instance production;
 *       the Redis extension must be configured (see {@code quarkus.redis.hosts}).
 *       Fail-open on Redis errors.</li>
 * </ul>
 *
 * <p>Lives in {@code web-common} so both the storefront and admin runtimes
 * share a single rate-limit implementation. The optional Redis backend is
 * discovered generically via {@code Instance<RateLimitBackend>} so
 * {@code web-common} does not need a compile-time dependency on
 * {@code quarkus-redis-client}.
 *
 * <h4>Global on/off</h4>
 * {@code gadaa.ratelimit.enabled=false} short-circuits every call to
 * {@link Decision#ALLOWED} regardless of backend.
 *
 * <h4>Metrics</h4>
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
    /** All other backends (e.g. the Redis one in api-storefront). Optional. */
    @Inject Instance<RateLimitBackend> otherBackends;
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
            RateLimitBackend redis = resolveByLabel("redis");
            if (redis != null) {
                active = redis;
                LOG.infof("Rate-limit backend: %s (multi-instance safe)", active.label());
            } else {
                LOG.warn("gadaa.ratelimit.backend=redis but no redis-labelled backend is resolvable " +
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

    /** Find a backend (other than the memory one) whose label matches. */
    private RateLimitBackend resolveByLabel(String label) {
        if (otherBackends == null) return null;
        // Exclude InMemoryRateLimitBackend (already held) by checking label.
        Stream<RateLimitBackend> stream = otherBackends.stream();
        return stream.filter(b -> label.equalsIgnoreCase(b.label()))
                     .findFirst()
                     .orElse(null);
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
