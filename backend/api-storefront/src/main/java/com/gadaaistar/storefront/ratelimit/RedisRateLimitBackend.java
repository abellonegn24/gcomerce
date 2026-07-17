package com.gadaaistar.storefront.ratelimit;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import com.gadaaistar.storefront.ratelimit.RateLimitService.Decision;

import io.quarkus.redis.datasource.RedisDataSource;
import io.vertx.mutiny.redis.client.Response;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Redis-backed sliding-window rate limiter.
 *
 * <p>Each {@code (bucket, clientId)} pair maps to a Redis sorted set whose
 * members are the individual hit timestamps (scores = ms-since-epoch). On
 * every check we run a single Lua script that atomically:
 * <ol>
 *   <li>Drops members with score {@code < now - windowMs} (evicts expired hits).</li>
 *   <li>Reads the remaining count.</li>
 *   <li>If {@code count >= max}, returns {@code {0, oldestScore}} without mutating.</li>
 *   <li>Otherwise appends the new hit and returns {@code {1, 0}}.</li>
 * </ol>
 * Also sets a {@code PEXPIRE} on the key equal to the window duration so
 * long-abandoned keys reap themselves — no separate cleanup scheduler needed.
 *
 * <h4>Fail-open</h4>
 * On any Redis exception (connection refused, timeout, script error) we
 * <strong>allow</strong> the request and log at WARN. Rate limits are a
 * belt-and-braces defence — a Redis outage should not take the site offline.
 *
 * <h4>Uniqueness of ZSET members</h4>
 * ZADD replaces the score of an existing member; if two hits arrive with the
 * same millisecond timestamp the second would be a no-op. To keep every hit
 * counted we append a per-JVM monotonic counter to the member string, seeded
 * with a random component so replicas don't collide either.
 */
@ApplicationScoped
public class RedisRateLimitBackend implements RateLimitBackend {

    private static final Logger LOG = Logger.getLogger(RedisRateLimitBackend.class);

    /** Prefix for every rate-limit key. Namespaces us from other Redis users. */
    private static final String KEY_PREFIX = "gi:rl:";

    /**
     * Atomic sliding-window Lua script. Redis guarantees the whole script
     * runs as one indivisible transaction.
     *
     * <pre>
     * KEYS[1] = zset key
     * ARGV[1] = now (ms)
     * ARGV[2] = window (ms)
     * ARGV[3] = max hits
     * ARGV[4] = new member string (uniqueness token)
     *
     * returns {allowed (0|1), oldestScoreOrZero}
     * </pre>
     */
    private static final String SLIDING_WINDOW_LUA =
        "local key      = KEYS[1]\n" +
        "local now      = tonumber(ARGV[1])\n" +
        "local windowMs = tonumber(ARGV[2])\n" +
        "local maxHits  = tonumber(ARGV[3])\n" +
        "local member   = ARGV[4]\n" +
        "redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)\n" +
        "local count = redis.call('ZCARD', key)\n" +
        "if count >= maxHits then\n" +
        "  local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')\n" +
        "  local oldestScore = 0\n" +
        "  if oldest[2] then oldestScore = tonumber(oldest[2]) end\n" +
        "  return {0, oldestScore}\n" +
        "end\n" +
        "redis.call('ZADD', key, now, member)\n" +
        "redis.call('PEXPIRE', key, windowMs)\n" +
        "return {1, 0}";

    /** Uniqueness counter for ZSET members. Random seed avoids replica-collision. */
    private final AtomicLong memberSeq = new AtomicLong(ThreadLocalRandom.current().nextLong());

    /** Cached SHA of the loaded script; falls back to EVAL on NOSCRIPT. */
    private volatile String scriptSha;

    @Inject RedisDataSource redis;

    @PostConstruct
    void init() {
        // Try to preload the script so subsequent calls use EVALSHA (cheaper
        // wire cost). Non-fatal if Redis is unreachable at boot — we'll
        // re-attempt on first tryAcquire.
        try {
            Response resp = redis.execute("SCRIPT", "LOAD", SLIDING_WINDOW_LUA);
            if (resp != null) {
                scriptSha = resp.toString();
                LOG.debugf("Preloaded rate-limit Lua script, sha=%s", scriptSha);
            }
        } catch (Exception e) {
            LOG.warnf("Rate-limit Lua script preload failed (%s). Will retry on first call.",
                    e.getMessage());
        }
    }

    @Override
    public Decision tryAcquire(String bucket, String clientId, int max, Duration window) {
        if (max <= 0) return new Decision(false, window.getSeconds());

        String key = KEY_PREFIX + bucket + ":" + (clientId == null ? "anon" : clientId);
        long now      = System.currentTimeMillis();
        long windowMs = window.toMillis();
        String member = now + ":" + memberSeq.incrementAndGet();

        try {
            Response resp = eval(key, now, windowMs, max, member);
            if (resp == null || resp.size() < 2) {
                // Should never happen — treat as allow to fail-open.
                LOG.warnf("Rate-limit Redis returned unexpected shape for key=%s", key);
                return Decision.ALLOWED;
            }
            long allowed = resp.get(0).toLong();
            if (allowed == 1L) return Decision.ALLOWED;

            long oldestScore = resp.get(1).toLong();
            long retryAfterMs = (oldestScore + windowMs) - now;
            long retryAfterSeconds = Math.max(1L, (retryAfterMs + 999) / 1000);
            return new Decision(false, retryAfterSeconds);
        } catch (Exception e) {
            // Fail-open: don't punish users for a Redis blip.
            LOG.warnf("Rate-limit Redis error on key=%s (%s) — allowing request",
                    key, e.getMessage());
            return Decision.ALLOWED;
        }
    }

    /**
     * Run the sliding-window script, preferring EVALSHA when we have a
     * cached SHA. A {@code NOSCRIPT} response (Redis restarted / script
     * flushed) transparently falls back to a full EVAL that re-loads it.
     */
    private Response eval(String key, long now, long windowMs, int max, String member) {
        if (scriptSha != null) {
            try {
                return redis.execute("EVALSHA", scriptSha, "1", key,
                        Long.toString(now), Long.toString(windowMs),
                        Integer.toString(max), member);
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (!msg.contains("NOSCRIPT")) throw e;
                // Script was flushed; forget the SHA and fall through to EVAL.
                LOG.debug("Redis NOSCRIPT — reloading rate-limit script");
                scriptSha = null;
            }
        }
        Response resp = redis.execute("EVAL", SLIDING_WINDOW_LUA, "1", key,
                Long.toString(now), Long.toString(windowMs),
                Integer.toString(max), member);
        // Opportunistically cache the SHA on the next SCRIPT LOAD.
        try {
            Response loaded = redis.execute("SCRIPT", "LOAD", SLIDING_WINDOW_LUA);
            if (loaded != null) scriptSha = loaded.toString();
        } catch (Exception ignore) {
            // Non-fatal; we'll pay for the full EVAL next call.
        }
        return resp;
    }

    @Override
    public int activeKeyCount() {
        // We could SCAN gi:rl:*, but it's O(n) over the whole keyspace and
        // this method is only used for local diagnostics. Return -1 to
        // signal "not tracked here".
        return -1;
    }

    @Override
    public String label() {
        return "redis";
    }
}
