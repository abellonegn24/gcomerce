package com.gadaaistar.admin.coupon;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Scheduled jobs for coupon notifications.
 *
 * <p><strong>Daily expiry check</strong> — runs at 08:00 UTC by default.
 * Finds active coupons expiring within the configured warning window and
 * sends an email to the finance/admin team. Skips if no coupons are
 * expiring or the feature is disabled.
 *
 * <p><strong>Weekly usage report</strong> — runs every Monday at 09:00 UTC.
 * Summarises coupon redemption activity for the last 7 days.
 *
 * <p>Both jobs use {@code ConcurrentExecution.SKIP} so overlapping runs
 * are silently discarded. An {@link AtomicBoolean} guard adds belt-and-braces
 * protection.
 */
@ApplicationScoped
public class CouponReportScheduler {

    private static final Logger LOG = Logger.getLogger(CouponReportScheduler.class);

    @ConfigProperty(name = "gadaa.coupon.expiry-check.enabled", defaultValue = "true")
    boolean expiryCheckEnabled;

    @ConfigProperty(name = "gadaa.coupon.expiry-check.warning-days", defaultValue = "7")
    int warningDays;

    @ConfigProperty(name = "gadaa.coupon.usage-report.enabled", defaultValue = "true")
    boolean usageReportEnabled;

    @ConfigProperty(name = "gadaa.coupon.usage-report.report-days", defaultValue = "7")
    int reportDays;

    @Inject CouponReportService reportService;

    private final AtomicBoolean expiryRunning = new AtomicBoolean(false);
    private final AtomicBoolean usageRunning  = new AtomicBoolean(false);

    // -------------------------------------------------------------------------

    /**
     * Daily expiry check at 08:00 UTC. Cron expression is configurable via
     * {@code gadaa.coupon.expiry-check.cron}.
     */
    @Scheduled(
        cron = "${gadaa.coupon.expiry-check.cron:0 0 8 * * ?}",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    void checkExpiring() {
        if (!expiryCheckEnabled) return;
        if (!expiryRunning.compareAndSet(false, true)) return;
        try {
            int count = reportService.checkExpiringCoupons(warningDays);
            if (count > 0) {
                LOG.infof("Coupon expiry check: sent warning for %d coupon(s)", count);
            }
        } finally {
            expiryRunning.set(false);
        }
    }

    /**
     * Weekly usage report every Monday at 09:00 UTC. Cron expression is
     * configurable via {@code gadaa.coupon.usage-report.cron}.
     */
    @Scheduled(
        cron = "${gadaa.coupon.usage-report.cron:0 0 9 ? * MON}",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    void sendUsageReport() {
        if (!usageReportEnabled) return;
        if (!usageRunning.compareAndSet(false, true)) return;
        try {
            reportService.sendUsageReport(reportDays);
            LOG.infof("Coupon usage report: sent (last %d days)", reportDays);
        } finally {
            usageRunning.set(false);
        }
    }

    // -------------------------------------------------------------------------

    /** Manual trigger for the expiry check (used by the admin REST endpoint). */
    public int runExpiryCheck() {
        return reportService.checkExpiringCoupons(warningDays);
    }

    /** Manual trigger for the usage report (used by the admin REST endpoint). */
    public void runUsageReport() {
        reportService.sendUsageReport(reportDays);
    }
}
