package com.gadaaistar.persistence.order;

import java.time.Clock;
import java.time.LocalDate;

import com.gadaaistar.common.exceptions.NotFoundException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Allocates the next invoice number for a given document type + fiscal year.
 *
 * <p>Correctness requirements (FR-TAX-06 in the delivery plan):
 * <ul>
 *   <li><b>Gap-free</b>: numbering must be strictly increasing with no skips —
 *       Ethiopian tax auditors require this.</li>
 *   <li><b>Concurrency-safe</b>: two concurrent checkouts must never receive
 *       the same number, even under high load.</li>
 * </ul>
 *
 * <p>Implementation: {@code SELECT ... FOR UPDATE} on the {@code invoice_sequence}
 * row inside a transaction, then increment + release the lock at commit. This
 * serializes issuance per (doc_type, fiscal_year) tuple. The current MVP takes
 * the sequence-issuing hit on the transactional path — Phase 4 may swap for
 * a queue-backed allocator if we outgrow it.
 *
 * <p>Lives in the persistence module so both the storefront and admin
 * runtimes can share the same gap-free allocator.
 */
@ApplicationScoped
public class InvoiceNumberService {

    /** doc_type constant in the seed data — {@code invoice_sequence.doc_type}. */
    public static final String DOC_TYPE_INVOICE = "INVOICE";

    /** Prefix from {@code application.properties}, defaults to {@code GI}. */
    @ConfigProperty(name = "gadaa.company.invoice-prefix", defaultValue = "GI")
    String defaultPrefix;

    /**
     * Ethiopian fiscal year starts in Hamle (July). Configurable so we can
     * unit-test with a different starting month, but the default is 7.
     */
    @ConfigProperty(name = "gadaa.company.fiscal-year-start-month", defaultValue = "7")
    int fiscalYearStartMonth;

    @Inject EntityManager em;

    private Clock clock = Clock.systemDefaultZone();

    // Test hook — production code never touches this.
    void setClock(Clock clock) { this.clock = clock; }

    /**
     * Allocate the next invoice number for the current fiscal year. Must be
     * called from within a JTA transaction (annotated here so callers on
     * non-transactional paths get a runtime error rather than silent wrong
     * behaviour).
     *
     * @return e.g. {@code GI-2026-00042}
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public String allocate() {
        return allocate(DOC_TYPE_INVOICE, currentFiscalYear());
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public String allocate(String docType, int fiscalYear) {
        // Native SELECT ... FOR UPDATE serializes concurrent callers on this row.
        var row = em.createNativeQuery(
                """
                SELECT id, prefix, current_value
                FROM invoice_sequence
                WHERE doc_type = :docType AND fiscal_year = :year
                FOR UPDATE
                """)
                .setParameter("docType", docType)
                .setParameter("year", fiscalYear)
                .setMaxResults(1)
                .getResultList();

        if (row.isEmpty()) {
            // Bootstrap if we've entered a new fiscal year on a live system.
            em.createNativeQuery(
                    "INSERT INTO invoice_sequence (doc_type, fiscal_year, prefix, current_value) VALUES (?, ?, ?, 0)")
                    .setParameter(1, docType)
                    .setParameter(2, fiscalYear)
                    .setParameter(3, defaultPrefix)
                    .executeUpdate();
            // Re-run the lock-select on the fresh row.
            row = em.createNativeQuery(
                    """
                    SELECT id, prefix, current_value
                    FROM invoice_sequence
                    WHERE doc_type = :docType AND fiscal_year = :year
                    FOR UPDATE
                    """)
                    .setParameter("docType", docType)
                    .setParameter("year", fiscalYear)
                    .setMaxResults(1)
                    .getResultList();
            if (row.isEmpty()) {
                throw new NotFoundException(
                        "invoice_sequence row for docType='" + docType + "' fiscalYear=" + fiscalYear + " missing.");
            }
        }

        var cols = (Object[]) row.get(0);
        long id            = ((Number) cols[0]).longValue();
        String prefix      = (String) cols[1];
        long currentValue  = ((Number) cols[2]).longValue();
        long nextValue     = currentValue + 1;

        em.createNativeQuery("UPDATE invoice_sequence SET current_value = ? WHERE id = ?")
                .setParameter(1, nextValue)
                .setParameter(2, id)
                .executeUpdate();

        // Format: PREFIX-YYYY-00042 — 5-digit zero-padded to keep alphabetical
        // sort in sync with numeric order up to 99,999 invoices per year.
        return "%s-%d-%05d".formatted(prefix, fiscalYear, nextValue);
    }

    /**
     * Compute the current Ethiopian fiscal year. If today's month is on or
     * after {@link #fiscalYearStartMonth}, the fiscal year is the current
     * calendar year; otherwise it is one behind.
     */
    public int currentFiscalYear() {
        var today = LocalDate.now(clock);
        return today.getMonthValue() >= fiscalYearStartMonth ? today.getYear() : today.getYear() - 1;
    }
}
