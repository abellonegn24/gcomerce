package com.gadaaistar.admin.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletionStage;

import com.gadaaistar.persistence.coupon.Coupon;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Sends coupon-related notification emails to the admin / finance team.
 * Lives in the admin API because coupon management is admin-only.
 *
 * <p>Two email types:
 * <ul>
 *   <li><b>Coupon expiry warning</b> — daily check for active coupons
 *       expiring within N days.</li>
 *   <li><b>Coupon usage report</b> — periodic summary of redemption
 *       activity across all coupons.</li>
 * </ul>
 */
@ApplicationScoped
public class CouponReportMailService {

    private static final Logger LOG = Logger.getLogger(CouponReportMailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("MMM d, yyyy 'at' HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    @Inject ReactiveMailer mailer;

    @Location("mail/coupon-expiry-warning.html") Template expiryWarningHtml;
    @Location("mail/coupon-expiry-warning.txt")  Template expiryWarningText;
    @Location("mail/coupon-usage-report.html")   Template usageReportHtml;
    @Location("mail/coupon-usage-report.txt")    Template usageReportText;

    @ConfigProperty(name = "quarkus.mailer.from") String fromAddress;
    @ConfigProperty(name = "gadaa.coupon.notification-email",
                    defaultValue = "finance@gadaaistar.com")
    String notificationEmail;
    @ConfigProperty(name = "gadaa.frontend.admin-url",
                    defaultValue = "http://localhost:4300")
    String adminUrl;

    // -------------------------------------------------------------------------

    /**
     * Send a coupon expiry warning email listing coupons that will expire
     * within the configured warning window.
     *
     * @param expiringCoupons list of active coupons expiring soon
     * @param warningDays     the warning window in days
     */
    public CompletionStage<Void> sendExpiryWarning(List<Coupon> expiringCoupons, int warningDays) {
        if (expiringCoupons == null || expiringCoupons.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedStage(null);
        }

        var subject = "Coupon expiry warning: " + expiringCoupons.size()
                + " coupon(s) expiring within " + warningDays + " days";
        var adminLink = adminUrl + "/coupons";

        var rows = expiringCoupons.stream()
                .map(c -> new ExpiryRow(
                        c.code,
                        formatDiscount(c),
                        c.redeemedCount,
                        c.expiresAt != null ? DATE_FMT.format(c.expiresAt) : "—"
                ))
                .toList();

        var html = fill(expiryWarningHtml,
                "couponCount", expiringCoupons.size(),
                "warningDays", warningDays,
                "adminLink", adminLink,
                "coupons", rows);
        var text = fill(expiryWarningText,
                "couponCount", expiringCoupons.size(),
                "warningDays", warningDays,
                "adminLink", adminLink,
                "coupons", rows);

        return send(notificationEmail, subject, html, text);
    }

    /**
     * Send a coupon usage report email summarising redemption activity.
     *
     * @param totalRedemptions total redemption count in the period
     * @param totalDiscount    total discount amount given in the period
     * @param activeCoupons    count of currently active coupons
     * @param reportDays       how many days the report covers
     * @param topCoupons       top coupons by redemption count
     */
    public CompletionStage<Void> sendUsageReport(
            long totalRedemptions, BigDecimal totalDiscount,
            long activeCoupons, int reportDays,
            List<UsageRow> topCoupons) {

        var period = "Last " + reportDays + " days";
        var subject = "Coupon usage report — " + period;
        var adminLink = adminUrl + "/coupons";
        var discountStr = totalDiscount.setScale(2, RoundingMode.HALF_UP).toPlainString();

        var html = fill(usageReportHtml,
                "period", period,
                "totalRedemptions", totalRedemptions,
                "totalDiscount", discountStr,
                "activeCoupons", activeCoupons,
                "reportDays", reportDays,
                "adminLink", adminLink,
                "topCoupons", topCoupons);
        var text = fill(usageReportText,
                "period", period,
                "totalRedemptions", totalRedemptions,
                "totalDiscount", discountStr,
                "activeCoupons", activeCoupons,
                "reportDays", reportDays,
                "adminLink", adminLink,
                "topCoupons", topCoupons);

        return send(notificationEmail, subject, html, text);
    }

    // -------------------------------------------------------------------------

    private CompletionStage<Void> send(String to, String subject,
                                        TemplateInstance html, TemplateInstance text) {
        var mail = Mail.withHtml(to, subject, html.render())
                .setText(text.render())
                .setFrom(fromAddress);

        return mailer.send(mail)
                .invoke(() -> LOG.infof("Sent '%s' to %s", subject, to))
                .onFailure().invoke(err -> LOG.errorf(err, "Failed to send '%s' to %s", subject, to))
                .subscribeAsCompletionStage();
    }

    private static TemplateInstance fill(Template tpl, Object... vars) {
        var instance = tpl.instance();
        for (int i = 0; i + 1 < vars.length; i += 2) {
            instance = instance.data(String.valueOf(vars[i]), vars[i + 1]);
        }
        return instance;
    }

    private static String formatDiscount(Coupon c) {
        if (c.isPercent()) {
            return c.discountValue.stripTrailingZeros().toPlainString() + "% off";
        }
        return c.currency + " " + c.discountValue.setScale(2, RoundingMode.HALF_UP).toPlainString() + " off";
    }

    // -------------------------------------------------------------------------
    // Row records for Qute template iteration
    // -------------------------------------------------------------------------

    public record ExpiryRow(
            String code,
            String discount,
            int redeemedCount,
            String expiresAt
    ) {}

    public record UsageRow(
            String code,
            String discount,
            int redeemedCount,
            String totalDiscount
    ) {}
}
