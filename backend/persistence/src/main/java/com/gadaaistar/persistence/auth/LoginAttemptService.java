package com.gadaaistar.persistence.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.gadaaistar.common.exceptions.AccountLockedException;
import com.gadaaistar.persistence.LockableAccount;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/**
 * Enforces per-account lockout after repeated failed login attempts
 * (INSA CWE-307 remediation).
 *
 * <p>This is the <em>second</em> layer of brute-force defence, sitting on top
 * of the per-IP {@code @RateLimit} layer. The auditor reproduced that an
 * attacker rotating IPs could keep guessing the same account indefinitely;
 * this service closes that gap by tracking failures per <em>account</em> and
 * temporarily locking it once a threshold is crossed.
 *
 * <h4>Policy</h4>
 * <ul>
 *   <li>After {@code max-failed-attempts} consecutive failures the account is
 *       locked until {@code lockedUntil}.</li>
 *   <li>Lockout duration is <strong>progressive</strong> when
 *       {@code gadaa.security.login.progressive=true}: each successive lockout
 *       doubles the duration, capped at {@code max-lockout-seconds}.
 *       Formula: {@code min(base * 2^lockoutSequence, max)}.
 *       Defaults: 900s → 1800s → 3600s → 3600s …</li>
 *   <li>A successful login resets {@code failedLoginAttempts},
 *       {@code lockedUntil}, and {@code lockoutSequence} to 0 / null / 0.</li>
 *   <li>{@link #checkNotLocked} runs <em>before</em> the password verify so a
 *       locked account cannot be probed for validity.</li>
 * </ul>
 *
 * <h4>Audit log</h4>
 * Every attempt (success or failure) is recorded in {@link LoginAttemptLog} to
 * satisfy the auditor's "logged and monitored" requirement.
 *
 * <h4>Configuration</h4>
 * <pre>
 * gadaa.security.login.enabled=true
 * gadaa.security.login.max-failed-attempts=5
 * gadaa.security.login.base-lockout-seconds=900
 * gadaa.security.login.max-lockout-seconds=3600
 * gadaa.security.login.progressive=true
 * </pre>
 */
@ApplicationScoped
public class LoginAttemptService {

    private static final Logger LOG = Logger.getLogger(LoginAttemptService.class);

    /** Account type tag written to the audit log. */
    public enum AccountType { CUSTOMER, ADMIN }

    @ConfigProperty(name = "gadaa.security.login.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "gadaa.security.login.max-failed-attempts", defaultValue = "5")
    int maxFailedAttempts;

    @ConfigProperty(name = "gadaa.security.login.base-lockout-seconds", defaultValue = "900")
    long baseLockoutSeconds;

    @ConfigProperty(name = "gadaa.security.login.max-lockout-seconds", defaultValue = "3600")
    long maxLockoutSeconds;

    @ConfigProperty(name = "gadaa.security.login.progressive", defaultValue = "true")
    boolean progressive;

    @Inject LoginAttemptLogRepository auditLog;
    @Inject EntityManager em;

    // -------------------------------------------------------------------------

    /**
     * Reject the request if the account is currently locked.
     *
     * @throws AccountLockedException (→ HTTP 429 + Retry-After) if
     *         {@code lockedUntil} is in the future.
     */
    public void checkNotLocked(LockableAccount account) {
        if (!enabled || account == null) return;

        var lockedUntil = account.getLockedUntil();
        if (lockedUntil == null) return;

        var now = Instant.now();
        if (lockedUntil.isAfter(now)) {
            long retryAfter = ChronoUnit.SECONDS.between(now, lockedUntil) + 1;
            LOG.warnf("Login rejected — account id=%s locked for %ds more (seq=%d)",
                    account.getId(), retryAfter, account.getLockoutSequence());
            throw new AccountLockedException(retryAfter);
        }
        // Lockout window has expired — leave the fields as-is; recordSuccess /
        // the next failed attempt will reset them. We deliberately do NOT clear
        // the lockout sequence here so the next lockout is progressively longer.
    }

    /**
     * Record a failed login attempt. Increments the counter; if the threshold
     * is reached, sets {@code lockedUntil} using the progressive formula and
     * bumps {@code lockoutSequence}.
     *
     * <p>The caller is responsible for persisting the account entity (this
     * method mutates the entity in place; the surrounding {@code @Transactional}
     * flushes it).
     *
     * <p>Uses {@code REQUIRES_NEW} so the failure count + audit log persist
     * even when the caller's transaction rolls back (which is always — the
     * caller throws {@code UnauthorizedException} after calling this method).
     * Without this, the lockout counter would be rolled back together with
     * the failed login and never reach the threshold.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void recordFailure(LockableAccount account, String ip, String userAgent, AccountType type) {
        writeAudit(account, ip, userAgent, type, false);

        if (!enabled || account == null) return;

        // Re-fetch the entity in this new transaction — the caller's entity
        // is detached because we're in a REQUIRES_NEW transaction.
        var managed = reFetch(account);
        if (managed == null) return;

        int newCount = managed.getFailedLoginAttempts() + 1;
        if (newCount < maxFailedAttempts) {
            managed.setFailedLoginAttempts(newCount);
            LOG.warnf("Failed login #%d for %s id=%s email=%s ip=%s",
                    newCount, type, managed.getId(), mask(managed.getEmail()), ip);
            return;
        }

        // Threshold reached — lock the account.
        int seq = managed.getLockoutSequence();
        long duration = progressive
                ? Math.min(baseLockoutSeconds * (1L << seq), maxLockoutSeconds)
                : Math.min(baseLockoutSeconds, maxLockoutSeconds);
        var until = Instant.now().plusSeconds(duration);

        managed.setLockedUntil(until);
        managed.setLockoutSequence(seq + 1);
        managed.setFailedLoginAttempts(0);   // reset so the next window starts fresh

        LOG.warnf("Account %s id=%s email=%s LOCKED for %ds (seq=%d) after %d failed attempts ip=%s",
                type, managed.getId(), mask(managed.getEmail()), duration, seq + 1, newCount, ip);
    }

    /**
     * Record a successful login. Resets the failed-attempt counter, clears the
     * lockout window, and resets the lockout sequence (so a future lockout
     * starts again at the base duration).
     *
     * <p>Uses the default {@code REQUIRED} propagation (joins the caller's
     * transaction) because the caller does not throw on the success path —
     * the transaction commits normally. Using {@code REQUIRES_NEW} here would
     * cause an {@code OptimisticLockException} because the inner transaction
     * would bump the entity's version column, and the outer transaction's
     * flush would then fail with a stale version.
     */
    @Transactional
    public void recordSuccess(LockableAccount account, String ip, String userAgent, AccountType type) {
        writeAudit(account, ip, userAgent, type, true);

        if (!enabled || account == null) return;

        var managed = reFetch(account);
        if (managed == null) return;

        if (managed.getFailedLoginAttempts() > 0 || managed.getLockedUntil() != null
                || managed.getLockoutSequence() > 0) {
            LOG.infof("Successful login for %s id=%s email=%s after %d prior failures (lockout seq was %d) — resetting.",
                    type, managed.getId(), mask(managed.getEmail()),
                    managed.getFailedLoginAttempts(), managed.getLockoutSequence());
        }
        managed.setFailedLoginAttempts(0);
        managed.setLockedUntil(null);
        managed.setLockoutSequence(0);
    }

    /**
     * Manually clear a lockout (admin unlock endpoint). Resets all three
     * fields without writing an audit row (the unlock action itself is logged
     * by the calling resource).
     *
     * <p>Uses the default {@code REQUIRED} propagation — joins the caller's
     * transaction. The admin unlock endpoints do not throw on the success path,
     * so the transaction commits normally.
     */
    @Transactional
    public void unlock(LockableAccount account) {
        if (account == null) return;
        var managed = reFetch(account);
        if (managed == null) return;
        managed.setFailedLoginAttempts(0);
        managed.setLockedUntil(null);
        managed.setLockoutSequence(0);
        LOG.infof("Manual unlock of account id=%s email=%s", managed.getId(), mask(managed.getEmail()));
    }

    // -------------------------------------------------------------------------

    /**
     * Re-fetch the {@link LockableAccount} entity in the current (new)
     * transaction. The entity passed by the caller is detached because
     * {@code REQUIRES_NEW} opens a fresh persistence context. Returns
     * {@code null} if the entity was deleted between the caller's read
     * and now (extremely unlikely for login flows).
     */
    @SuppressWarnings("unchecked")
    private LockableAccount reFetch(LockableAccount account) {
        if (account == null || account.getId() == null) return null;
        try {
            var managed = em.find(account.getClass(), account.getId());
            return (LockableAccount) managed;
        } catch (Exception e) {
            LOG.warnf("Could not re-fetch account id=%s for lockout update: %s",
                    account.getId(), e.getMessage());
            return null;
        }
    }

    private void writeAudit(LockableAccount account, String ip, String userAgent,
                            AccountType type, boolean success) {
        try {
            var row = new LoginAttemptLog();
            row.accountType = type.name();
            row.email       = account == null ? "unknown" : account.getEmail();
            row.ipAddress   = ip;
            row.userAgent   = userAgent;
            row.success     = success;
            row.attemptedAt = Instant.now();
            auditLog.persist(row);
        } catch (Exception e) {
            // Audit logging is best-effort — never block a login because the log write failed.
            LOG.warnf("Failed to write login_attempt_log row: %s", e.getMessage());
        }
    }

    /** Mask an email for logs: keep the local-part's first char + domain. */
    private static String mask(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
