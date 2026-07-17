package com.gadaaistar.storefront.leases;

import java.time.Duration;
import java.util.Optional;

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
 * Public entry point for the distributed-lease pattern used by scheduled
 * singletons (currently just {@code AbandonedOrderSweeper}).
 *
 * <p>Selector shim over a pluggable {@link LeaseBackend}:
 * <ul>
 *   <li>{@code gadaa.leases.backend=memory} (default) — in-JVM only. Fine
 *       for single-instance dev / staging where the caller's own
 *       {@code AtomicBoolean} guard already prevents overlap.</li>
 *   <li>{@code gadaa.leases.backend=redis} — Redis {@code SET NX PX} +
 *       Lua check-and-delete on release. Multi-instance safe. Fail-closed
 *       on Redis errors (skips the run rather than stampeding).</li>
 * </ul>
 *
 * <h4>Usage</h4>
 * <pre>{@code
 * lease.tryAcquire("abandoned-order-sweeper", Duration.ofMinutes(10))
 *      .ifPresentOrElse(token -> {
 *          try {
 *              doWork();
 *          } finally {
 *              token.release();
 *          }
 *      }, () -> LOG.debug("Another replica holds the lease; skipping"));
 * }</pre>
 *
 * <h4>Metrics (Phase 5.3)</h4>
 * Exposes Prometheus counters via Micrometer:
 * <ul>
 *   <li>{@code gi_lease_attempts_total{backend,result}} — incremented on
 *       every tryAcquire call; result is "acquired" or "skipped".</li>
 * </ul>
 */
@ApplicationScoped
public class DistributedLease {

    private static final Logger LOG = Logger.getLogger(DistributedLease.class);

    /** Which backend to use: "memory" (default) or "redis". */
    @ConfigProperty(name = "gadaa.leases.backend", defaultValue = "memory")
    String backendName;

    @Inject InMemoryLeaseBackend memoryBackend;
    // Optional resolution — if quarkus-redis-client is missing the bean
    // simply isn't produced and we fall back to memory with a warning.
    @Inject Instance<RedisLeaseBackend> redisBackend;
    @Inject MeterRegistry meterRegistry;

    private LeaseBackend active;

    // Metrics
    private Counter leaseAcquiredCounter;
    private Counter leaseSkippedCounter;

    @PostConstruct
    void init() {
        if ("redis".equalsIgnoreCase(backendName)) {
            if (redisBackend.isResolvable()) {
                active = redisBackend.get();
                LOG.infof("Distributed-lease backend: %s (multi-instance safe)", active.label());
            } else {
                LOG.warn("gadaa.leases.backend=redis but the Redis backend bean is not resolvable " +
                         "(quarkus-redis-client missing?). Falling back to memory.");
                active = memoryBackend;
            }
        } else {
            active = memoryBackend;
            LOG.infof("Distributed-lease backend: %s (single-instance only)", active.label());
        }

        // --- Micrometer counters ---
        var backendTag = Tags.of("backend", active.label());
        leaseAcquiredCounter = meterRegistry.counter("gi_lease_attempts_total",
                backendTag.and("result", "acquired"));
        leaseSkippedCounter = meterRegistry.counter("gi_lease_attempts_total",
                backendTag.and("result", "skipped"));
    }

    /**
     * Try to acquire the named lease. Callers <strong>must</strong> release
     * the returned token in a try-finally.
     *
     * @param name  logical lease key
     * @param ttl   safety expiry — a crashed holder is auto-released after this
     * @return      the token if acquired, {@link Optional#empty()} otherwise
     */
    public Optional<LeaseToken> tryAcquire(String name, Duration ttl) {
        var token = active.tryAcquire(name, ttl);
        if (token.isPresent()) {
            leaseAcquiredCounter.increment();
        } else {
            leaseSkippedCounter.increment();
        }
        return token;
    }

    /** Human-readable name of the active backend (for logs / diagnostics). */
    public String activeBackendLabel() {
        return active == null ? "uninitialised" : active.label();
    }
}
