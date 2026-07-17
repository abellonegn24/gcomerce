package com.gadaaistar.storefront.leases;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jboss.logging.Logger;

import io.quarkus.redis.datasource.RedisDataSource;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Redis-backed distributed lease.
 *
 * <p>Acquisition: {@code SET key nonce NX PX <ttlMs>}. Redis's {@code NX} flag
 * guarantees only one client wins even under contention; the {@code PX}
 * expiry means a crashed holder can't lock everyone out forever.
 *
 * <p>Release: a small Lua script that checks the current value matches our
 * nonce before deleting. Without the check we would happily delete a lease
 * a successor holder acquired after our TTL expired.
 *
 * <h4>Failure semantics</h4>
 * <strong>Fail-closed.</strong> Any Redis exception on acquire returns
 * {@link Optional#empty()} so the caller does <em>not</em> run — better to
 * skip a sweep than have every replica sweep at once. On release we swallow
 * exceptions and log; the TTL will drop the key eventually.
 */
@ApplicationScoped
public class RedisLeaseBackend implements LeaseBackend {

    private static final Logger LOG = Logger.getLogger(RedisLeaseBackend.class);

    /** Prefix for every lease key. Namespaces us from other Redis users. */
    private static final String KEY_PREFIX = "gi:lease:";

    /**
     * Check-and-delete on release. Redis Lua runs atomically so no other
     * client can slip a competing SET between our GET and DEL.
     *
     * <pre>
     * KEYS[1] = lease key
     * ARGV[1] = nonce we expect
     *
     * returns 1 if deleted, 0 if not (nonce mismatch or missing key)
     * </pre>
     */
    private static final String RELEASE_LUA =
        "if redis.call('GET', KEYS[1]) == ARGV[1] then\n" +
        "  return redis.call('DEL', KEYS[1])\n" +
        "else\n" +
        "  return 0\n" +
        "end";

    @Inject RedisDataSource redis;

    @Override
    public Optional<LeaseToken> tryAcquire(String name, Duration ttl) {
        String key = KEY_PREFIX + name;
        String nonce = UUID.randomUUID().toString();
        long ttlMs = ttl.toMillis();

        try {
            Response resp = redis.execute("SET", key, nonce, "NX", "PX", Long.toString(ttlMs));
            // Vert.x maps a rejected NX to a bulk-string nil reply, which we
            // read as null / non-"OK". Only the string "OK" means we won.
            if (resp == null || !"OK".equals(resp.toString())) {
                return Optional.empty();
            }

            var token = new LeaseToken(
                    name,
                    nonce,
                    Instant.now().plus(ttl),
                    () -> release(key, nonce));
            return Optional.of(token);
        } catch (Exception e) {
            // Fail-closed on acquire — refusing to run beats stampeding.
            LOG.warnf("Redis lease acquire failed for '%s' (%s) — skipping run", name, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Run the check-and-delete script. Swallows exceptions — a failed
     * release is annoying but self-healing (the TTL will drop the key).
     */
    private void release(String key, String nonce) {
        try {
            redis.execute("EVAL", RELEASE_LUA, "1", key, nonce);
        } catch (Exception e) {
            LOG.warnf("Redis lease release failed for key=%s (%s) — TTL will reap it",
                    key, e.getMessage());
        }
    }

    @Override
    public String label() {
        return "redis";
    }
}
