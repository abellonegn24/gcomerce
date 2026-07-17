package com.gadaaistar.storefront.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.gadaaistar.common.exceptions.NotFoundException;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Determines the applicable VAT rule for a given ship-to country and computes
 * VAT amounts on subtotals.
 *
 * <p>Selection logic (see FR-TAX-02 / FR-TAX-03 in the delivery plan):
 * <ol>
 *   <li>Look up a rule keyed on the exact country code that is active today.</li>
 *   <li>Fallback to the default rule (country_code IS NULL) — the export
 *       zero-rated rule seeded in Flyway V1.</li>
 * </ol>
 *
 * <p>Prices in the catalog are stored VAT-exclusive; this service simply
 * multiplies and rounds. All monetary math uses {@link BigDecimal}.
 */
@ApplicationScoped
public class TaxService {

    @Inject EntityManager em;

    /**
     * Result of looking up a tax rule for a specific ship-to country.
     */
    public record TaxDecision(
            long   ruleId,
            String taxClassCode,
            String countryCode,     // may be null for the default (export) rule
            BigDecimal ratePercent, // e.g., 15.000 or 0.000
            boolean zeroRated,
            String zeroRatedReason
    ) {}

    /**
     * Resolve the applicable tax rule for the given ship-to country.
     *
     * @param countryCode ISO-3166-1 alpha-2 uppercase (e.g., "ET", "DE", "US")
     */
    public TaxDecision resolveFor(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("countryCode is required");
        }
        var normalized = countryCode.trim().toUpperCase();
        var today = LocalDate.now();

        var byCountry = query(
                """
                SELECT r.id, tc.code, r.country_code, r.rate_percent, r.zero_rated, r.zero_rated_reason
                FROM tax_rule r
                JOIN tax_class tc ON tc.id = r.tax_class_id
                WHERE r.country_code = :cc
                  AND r.active = TRUE
                  AND r.effective_from <= :today
                  AND (r.effective_to IS NULL OR r.effective_to >= :today)
                ORDER BY r.effective_from DESC
                LIMIT 1
                """, normalized, today);
        if (byCountry != null) return byCountry;

        var byDefault = query(
                """
                SELECT r.id, tc.code, r.country_code, r.rate_percent, r.zero_rated, r.zero_rated_reason
                FROM tax_rule r
                JOIN tax_class tc ON tc.id = r.tax_class_id
                WHERE r.country_code IS NULL
                  AND r.active = TRUE
                  AND r.effective_from <= :today
                  AND (r.effective_to IS NULL OR r.effective_to >= :today)
                ORDER BY r.effective_from DESC
                LIMIT 1
                """, null, today);
        if (byDefault != null) return byDefault;

        throw new NotFoundException("No tax rule configured for country '" + normalized + "'.");
    }

    /**
     * Compute the VAT amount and gross total for a given VAT-exclusive subtotal.
     * Rounding: HALF_UP to 2 decimal places (standard commercial rounding).
     */
    public TaxComputation compute(BigDecimal netSubtotal, String countryCode) {
        var decision = resolveFor(countryCode);
        var rate     = decision.ratePercent().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        var taxAmt   = netSubtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        var gross    = netSubtotal.add(taxAmt).setScale(2, RoundingMode.HALF_UP);
        return new TaxComputation(decision, netSubtotal.setScale(2, RoundingMode.HALF_UP), taxAmt, gross);
    }

    public record TaxComputation(TaxDecision decision, BigDecimal net, BigDecimal tax, BigDecimal gross) {}

    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private TaxDecision query(String sql, String cc, LocalDate today) {
        var q = em.createNativeQuery(sql)
                .setParameter("today", today);
        if (cc != null) q.setParameter("cc", cc);

        var rows = q.getResultList();
        if (rows.isEmpty()) return null;
        var row = (Object[]) rows.get(0);

        return new TaxDecision(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                row[3] == null ? BigDecimal.ZERO : new BigDecimal(row[3].toString()),
                ((Boolean) row[4]),
                (String) row[5]);
    }
}
