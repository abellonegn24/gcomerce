package com.gadaaistar.web.ratelimit;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.gadaaistar.web.ratelimit.RateLimitService.Decision;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Process-local sliding-window rate limiter.
 *
 * <p>Per {@code (bucket, clientId)} we keep a queue of hit timestamps. On
 * each check we drop hits older than the window and count what's left; hits
 * at or over the ceiling are rejected. O(hits-per-window) per call with
 * cheap constants — perfectly adequate for the /auth/* burst limits we
 * apply to it.
 *
 * <h4>Limitations</h4>
 * <ul>
 *   <li><strong>Single instance only.</strong> Behind a load balancer with
 *       N replicas the effective limit becomes {@code N × max}. Multi-instance
 *       deployments should switch to the Redis backend via
 *       {@code gadaa.ratelimit.backend=redis}.</li>
 *   <li>State is lost on process restart. That's <em>desirable</em> here — a
 *       forced restart should reset penalty windows, not extend them.</li>
 * </ul>
 */
@ApplicationScoped
public class InMemoryRateLimitBackend implements RateLimitBackend {

    private static final Logger LOG = Logger.getLogger(InMemoryRateLimitBackend.class);

    /**
     * Safety cap on individual queues to keep memory bounded under abuse.
     * If a queue grows past this we start dropping the oldest hits regardless
     * of the sliding window — the caller is way past the limit anyway.
     */
    @ConfigProperty(name = "gadaa.ratelimit.max-queue-size", defaultValue = "1024")
    int maxQueueSize;

    /** All active hit queues. */
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Override
    public Decision tryAcquire(String bucket, String clientId, int max, Duration window) {
        // Zero / negative limits mean "never allow" — useful for kill-switching
        // an endpoint by config without redeploying.
        if (max <= 0) return new Decision(false, window.getSeconds());

        String key = bucket + "|" + (clientId == null ? "anon" : clientId);
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            // Drop hits outside the window from the head.
            Iterator<Long> it = queue.iterator();
            while (it.hasNext()) {
                long t = it.next();
                if (t < windowStart) it.remove();
                else break;   // queue is in insertion order → we're done
            }

            if (queue.size() >= max) {
                long oldest = queue.peekFirst() == null ? now : queue.peekFirst();
                long retryAfterMs = (oldest + window.toMillis()) - now;
                long retryAfterSeconds = Math.max(1L, (retryAfterMs + 999) / 1000);
                return new Decision(false, retryAfterSeconds);
            }

            queue.addLast(now);
            // Defensive: keep individual queues bounded.
            while (queue.size() > maxQueueSize) queue.pollFirst();

            return Decision.ALLOWED;
        }
    }

    /**
     * Periodic sweeper — removes queues that haven't seen a hit in the last
     * 15 minutes so long-tail keys (bots, one-off scans) don't accumulate.
     */
    @Scheduled(every = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reap() {
        if (hits.isEmpty()) return;
        long threshold = System.currentTimeMillis() - Duration.ofMinutes(15).toMillis();
        int removed = 0;
        for (var e : hits.entrySet()) {
            var queue = e.getValue();
            synchronized (queue) {
                Long newest = queue.peekLast();
                if (newest == null || newest < threshold) {
                    hits.remove(e.getKey(), queue);
                    removed++;
                }
            }
        }
        if (removed > 0) LOG.debugf("Reaped %d stale rate-limit queues (active=%d)", removed, hits.size());
    }

    @Override
    public int activeKeyCount() {
        return hits.size();
    }

    @Override
    public String label() {
        return "memory";
    }
}
