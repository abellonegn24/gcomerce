package com.gadaaistar.storefront.leases;

import java.time.Duration;
import java.util.Optional;

/**
 * Strategy interface for the distributed-lease implementation used by the
 * abandoned-order sweeper (and, in future, any other cross-replica singleton
 * we introduce).
 *
 * <p>Two implementations ship:
 * <ul>
 *   <li>{@link InMemoryLeaseBackend} — trivial success. The single JVM's own
 *       {@code AtomicBoolean running} guard on the sweeper is already enough
 *       when we deploy one replica.</li>
 *   <li>{@link RedisLeaseBackend} — Redis {@code SET NX PX} plus a
 *       check-and-delete Lua release. Multi-instance safe.</li>
 * </ul>
 *
 * <p>Failure semantics differ from the rate-limit backends: leases are
 * <strong>fail-closed</strong>. If Redis is unreachable and we can't confirm
 * exclusivity, we refuse to run rather than have every replica sweep at once
 * and stampede the DB.
 */
public interface LeaseBackend {

    /**
     * Try to acquire the named lease for the given TTL.
     *
     * @param name  logical lease key (e.g. {@code "abandoned-order-sweeper"})
     * @param ttl   how long we intend to hold the lease before releasing.
     *              The backend uses this as an expiry so a crashed holder
     *              can't lock everyone out forever.
     * @return      {@link Optional#empty()} if someone else holds it or the
     *              backend errored; otherwise a token the caller must
     *              {@link LeaseToken#release()} in a try-finally.
     */
    Optional<LeaseToken> tryAcquire(String name, Duration ttl);

    /** Human-readable name of this backend ({@code "memory"} or {@code "redis"}). */
    String label();
}
