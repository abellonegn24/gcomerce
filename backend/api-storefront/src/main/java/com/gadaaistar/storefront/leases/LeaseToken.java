package com.gadaaistar.storefront.leases;

import java.time.Instant;

/**
 * Handle on a held distributed lease.
 *
 * <p>Callers <strong>must</strong> {@link #release()} the token when done —
 * typically inside a {@code try-finally}. Forgetting to release is not
 * catastrophic (Redis TTL will drop the key eventually) but it means the
 * next sweeper run has to wait for the TTL to expire.
 *
 * <p>Token construction is package-private on purpose — clients should only
 * obtain instances from {@link DistributedLease#tryAcquire}.
 */
public final class LeaseToken {

    private final String name;
    private final String value;
    private final Instant expiresAt;
    private final Runnable releaser;
    private volatile boolean released;

    LeaseToken(String name, String value, Instant expiresAt, Runnable releaser) {
        this.name = name;
        this.value = value;
        this.expiresAt = expiresAt;
        this.releaser = releaser;
    }

    public String name() { return name; }

    /** Random per-acquire nonce; used by the Redis backend to guard release. */
    public String value() { return value; }

    public Instant expiresAt() { return expiresAt; }

    /**
     * Release the lease. Idempotent — calling twice is a no-op. Backends
     * that don't hold real state (in-memory) just return; the Redis backend
     * runs a Lua check-and-delete so we don't stomp on a lease another
     * holder acquired after ours expired.
     */
    public void release() {
        if (released) return;
        released = true;
        releaser.run();
    }
}
