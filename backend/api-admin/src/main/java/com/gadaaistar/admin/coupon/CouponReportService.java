package com.gadaaistar.admin.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.gadaaistar.persistence.coupon.Coupon;
import com.gadaaistar.persistence.coupon.CouponRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.jboss.logging.Logger;

/**
 * Business logic for coupon reports: expiry checks and usage aggregation.
 *
 * <p>Queries are kept simple and read-only — no new tables or migrations
 * needed. The service delegates email sending to {@link CouponReportMailService}.
 */
@ApplicationScoped
public class CouponReportService {

    private static final Logger LOG = Logger.getLogger(CouponReportService.class);

    @Inject CouponRepository coupons;
    @Inject CouponReportMailService mailService;
    @Inject EntityManager em;

    // -------------------------------------------------------------------------

    /**
     * Find active coupons that will expire within the next {@code warningDays}
     * and send a notification email if any are found.
     *
     * @param warningDays days-ahead window for the expiry check
     * @return number of expiring coupons found (0 = no email sent)
     */
    public int checkExpiringCoupons(int warningDays) {
        var now = Instant.now();
        var cutoff = now.plus(Duration.ofDays(warningDays));

        // Active coupons with an expiry date within the window.
        var query = em.createQuery(
                "SELECT c FROM Coupon c WHERE c.active = true " +
                "AND c.expiresAt IS NOT NULL " +
                "AND c.expiresAt > :now AND c.expiresAt <= :cutoff " +
                "ORDER BY c.expiresAt ASC",
                Coupon.class);
        query.setParameter("now", now);
        query.setParameter("cutoff", cutoff);
        var expiring = query.getResultList();

        if (expiring.isEmpty()) {
            LOG.debug("Coupon expiry check: no coupons expiring within " + warningDays + " days");
            return 0;
        }

        LOG.infof("Coupon expiry check: %d coupon(s) expiring within %d days", expiring.size(), warningDays);

        mailService.sendExpiryWarning(expiring, warningDays)
                .exceptionally(err -> {
                    LOG.errorf(err, "Failed to send coupon expiry warning email");
                    return null;
                });

        return expiring.size();
    }

    /**
     * Generate and send a coupon usage report covering the last
     * {@code reportDays} days.
     *
     * @param reportDays how many days back to include in the report
     */
    public void sendUsageReport(int reportDays) {
        var since = Instant.now().minus(Duration.ofDays(reportDays));

        // Total redemptions in the period.
        var redemptionCount = em.createQuery(
                "SELECT COUNT(r) FROM CouponRedemption r WHERE r.redeemedAt >= :since",
                Long.class)
                .setParameter("since", since)
                .getSingleResult();

        // Total discount amount in the period.
        var totalDiscount = em.createQuery(
                "SELECT COALESCE(SUM(r.discountAmount), 0) FROM CouponRedemption r WHERE r.redeemedAt >= :since",
                BigDecimal.class)
                .setParameter("since", since)
                .getSingleResult();

        // Active coupon count.
        long activeCount = coupons.count("active = true");

        // Top 10 coupons by redemption count in the period.
        @SuppressWarnings("unchecked")
        var topRows = em.createQuery(
                "SELECT r.coupon.id, r.code, " +
                "COUNT(r) AS redemptionCount, " +
                "COALESCE(SUM(r.discountAmount), 0) AS totalDiscount " +
                "FROM CouponRedemption r " +
                "WHERE r.redeemedAt >= :since " +
                "GROUP BY r.coupon.id, r.code " +
                "ORDER BY redemptionCount DESC")
                .setParameter("since", since)
                .setMaxResults(10)
                .getResultList();

        var topCoupons = new ArrayList<CouponReportMailService.UsageRow>(topRows.size());
        for (var row : topRows) {
            Object[] cols = (Object[]) row;
            var couponId = (Long) cols[0];
            var code = (String) cols[1];
            var count = (Long) cols[2];
            var discount = (BigDecimal) cols[3];

            // Fetch the coupon to get discount type for display.
            var coupon = coupons.findByIdOptional(couponId).orElse(null);
            String discountLabel;
            if (coupon != null) {
                if (coupon.isPercent()) {
                    discountLabel = coupon.discountValue.stripTrailingZeros().toPlainString() + "% off";
                } else {
                    discountLabel = coupon.currency + " "
                            + coupon.discountValue.setScale(2, RoundingMode.HALF_UP).toPlainString() + " off";
                }
            } else {
                discountLabel = "—";
            }

            topCoupons.add(new CouponReportMailService.UsageRow(
                    code,
                    discountLabel,
                    count.intValue(),
                    discount.setScale(2, RoundingMode.HALF_UP).toPlainString()
            ));
        }

        LOG.infof("Coupon usage report: %d redemptions, %s total discount, %d active coupons (last %d days)",
                redemptionCount, totalDiscount, activeCount, reportDays);

        mailService.sendUsageReport(
                redemptionCount,
                totalDiscount,
                activeCount,
                reportDays,
                topCoupons
        ).exceptionally(err -> {
            LOG.errorf(err, "Failed to send coupon usage report email");
            return null;
        });
    }
}
