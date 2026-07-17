package com.gadaaistar.storefront.leases;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * No-coordination lease used on single-instance deployments.
 *
 * <p>Since there is exactly one JVM, cross-replica exclusivity is trivially
 * satisfied and the caller's own in-JVM guards (e.g.
 * {@code AbandonedOrderSweeper}'s {@code AtomicBoolean running}) are enough.
 * We still track holders in a local map so back-to-back {@code tryAcquire}
 * on the same name returns empty until the first one is released — this
 * matches what Redis would do and lets tests exercise contention without
 * a Redis dependency.
 */
@ApplicationScoped
public class InMemoryLeaseBackend implements LeaseBackend {

    /** Currently-held leases, keyed by lease name. */
    private final ConcurrentMap<String, LeaseToken> held = new ConcurrentHashMap<>();

    @Override
    public Optional<LeaseToken> tryAcquire(String name, Duration ttl) {
        // Trim any expired holder before deciding contention. Ordinarily
        // release() clears it, but crashed code paths might not.
        held.compute(name, (k, existing) -> {
            if (existing == null) return null;
            return existing.expiresAt().isBefore(Instant.now()) ? null : existing;
        });

        // Capture the nonce up-front so the release closure can identify
        // exactly which token it belongs to (see the check-and-delete below).
        String nonce = UUID.randomUUID().toString();
        var candidate = new LeaseToken(
                name,
                nonce,
                Instant.now().plus(ttl),
                () -> {
                    // Match-and-clear: leave the entry alone if a successor
                    // (say, after our TTL expired) already took it.
                    held.compute(name, (k, existing) -> {
                        if (existing == null) return null;
                        return nonce.equals(existing.value()) ? null : existing;
                    });
                });

        var prior = held.putIfAbsent(name, candidate);
        return prior == null ? Optional.of(candidate) : Optional.empty();
    }

    @Override
    public String label() {
        return "memory";
    }
}
