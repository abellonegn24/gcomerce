package com.gadaaistar.storefront.order;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.gadaaistar.storefront.leases.DistributedLease;
import com.gadaaistar.storefront.leases.LeaseToken;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Scheduled sweeper that expires {@code PENDING_PAYMENT} orders whose
 * customer never completed the CyberSource redirect and therefore never
 * transitioned to {@code PAID} nor {@code CANCELLED}.
 *
 * <p><strong>Why we need it.</strong> {@code OrderService.createFromCart}
 * <em>reserves</em> (rather than decrements) inventory on placement. Reservation
 * is only released on an explicit cancel — customer-initiated (via
 * {@code /api/storefront/v1/me/orders/{uuid}/cancel}) or payment-gateway-initiated
 * (via {@code CyberSourceWebhookResource#markPaymentFailed}). If a customer
 * simply closes the tab on the CyberSource redirect page, neither of those
 * fires and their reservation blocks other customers from buying the same
 * SKU forever. This sweeper is the safety net.
 *
 * <p><strong>Design notes.</strong>
 * <ul>
 *   <li>The find (returns just ids) runs in {@link OrderService#findAbandonedIds}
 *       inside its own short {@code @Transactional} tx. The <em>write</em>
 *       for each order goes through {@link OrderService#cancelAbandoned(long, String)}
 *       which opens a {@code REQUIRES_NEW} tx per-order so a single failure
 *       inside the loop cannot roll back its peers.</li>
 *   <li>Re-checks the status inside {@code cancelAbandoned} — a CyberSource
 *       webhook could have flipped the row to {@code PAID} between our SELECT
 *       and our UPDATE. The check turns that race into a no-op instead of a
 *       corrupt row.</li>
 *   <li>Skips overlapping runs via {@code ConcurrentExecution.SKIP} — a long
 *       DB stall must not cause a queue of sweeps to pile up.</li>
 *   <li>Global on/off switch ({@code gadaa.orders.abandoned-sweeper.enabled})
 *       plus configurable TTL and batch size so ops can tune it without
 *       redeploying.</li>
 * </ul>
 */
@ApplicationScoped
public class AbandonedOrderSweeper {

    private static final Logger LOG = Logger.getLogger(AbandonedOrderSweeper.class);

    @ConfigProperty(name = "gadaa.orders.abandoned-sweeper.enabled", defaultValue = "true")
    boolean enabled;

    /**
     * Minutes after {@code placedAt} before a PENDING_PAYMENT order is
     * considered abandoned. The CyberSource Secure Acceptance redirect page
     * itself typically expires after 15 minutes, so 60 gives us a comfortable
     * margin for a customer who took a long look at the payment page and
     * finally submitted just before we swept.
     */
    @ConfigProperty(name = "gadaa.orders.abandoned-sweeper.ttl-minutes", defaultValue = "60")
    long ttlMinutes;

    /**
     * Max orders processed per sweep. Keeps the sweep bounded even on a fresh
     * boot where many hours of pending orders may be waiting. Any leftovers
     * are picked up on the next sweep.
     */
    @ConfigProperty(name = "gadaa.orders.abandoned-sweeper.batch-size", defaultValue = "100")
    int batchSize;

    /**
     * Lease name used to serialise the sweep across replicas.
     * <p>Constant on purpose — every JVM that runs this sweeper contends for
     * exactly this key. Change it and you split the herd.
     */
    static final String LEASE_NAME = "abandoned-order-sweeper";

    /** Re-entrancy guard on top of Quarkus's {@code SKIP} — belt & braces. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Inject OrderService orderService;
    @Inject DistributedLease lease;

    // -------------------------------------------------------------------------

    /**
     * Runs every 5 minutes by default (configurable via
     * {@code gadaa.orders.abandoned-sweeper.every}). Cadence is deliberately
     * short compared to the default TTL — the sweep itself is idempotent and
     * cheap when there is nothing to do (one indexed range scan). Deferred 30s
     * after boot so we don't interfere with Flyway / initial CDI startup.
     */
    @Scheduled(
        every  = "${gadaa.orders.abandoned-sweeper.every:5m}",
        delay  = 30,
        delayUnit = java.util.concurrent.TimeUnit.SECONDS,
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    void sweep() {
        if (!enabled) return;
        if (!running.compareAndSet(false, true)) return;
        try {
            // Cross-replica exclusivity: without the lease every replica in a
            // load-balanced deploy would run this loop in lockstep, wasting
            // work and racing each other on the same rows. The TTL is 3× the
            // cadence so a slow sweep doesn't lose the lease mid-flight; the
            // TTL exists solely to auto-recover from a crashed holder.
            var leaseTtl = Duration.ofMinutes(15);
            var acquired = lease.tryAcquire(LEASE_NAME, leaseTtl);
            if (acquired.isEmpty()) {
                LOG.debug("Abandoned-order sweeper: another replica holds the lease; skipping this run");
                return;
            }
            LeaseToken token = acquired.get();
            try {
                doSweep();
            } finally {
                token.release();
            }
        } finally {
            running.set(false);
        }
    }

    /**
     * Actual sweep loop. Split out from {@link #sweep()} so the lease and
     * re-entrancy guards form a clean, easy-to-read outer wrapper.
     */
    private void doSweep() {
        var cutoff = Instant.now().minus(Duration.ofMinutes(ttlMinutes));

        // Read in its own short tx and hand back plain longs so nothing
        // lazy escapes the tx boundary.
        List<Long> ids = orderService.findAbandonedIds(cutoff, batchSize);
        if (ids.isEmpty()) return;

        LOG.infof("Abandoned-order sweeper: %d pending-payment order(s) older than %d min",
                ids.size(), ttlMinutes);

        int cancelled = 0;
        int skipped   = 0;
        int failed    = 0;
        String reason = "Auto-cancelled by system: no payment confirmation within "
                + ttlMinutes + " minutes.";
        for (var id : ids) {
            try {
                boolean flipped = orderService.cancelAbandoned(id, reason);
                if (flipped) {
                    cancelled++;
                } else {
                    // A concurrent webhook / customer cancel won the race.
                    skipped++;
                }
            } catch (RuntimeException ex) {
                failed++;
                LOG.errorf(ex, "Abandoned-order sweeper: failed to cancel order id=%d", id);
            }
        }
        LOG.infof("Abandoned-order sweeper: cancelled=%d skipped=%d failed=%d",
                cancelled, skipped, failed);
    }

    // -------------------------------------------------------------------------
    // Test / ops hook — lets an integration test drive the sweep on demand
    // without waiting for the scheduled trigger. Not exposed on any REST path.
    // -------------------------------------------------------------------------

    /** Manually trigger a sweep. Same guardrails as the scheduled entry. */
    public void runNow() {
        sweep();
    }
}
