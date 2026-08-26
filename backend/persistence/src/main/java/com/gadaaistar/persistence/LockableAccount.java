package com.gadaaistar.persistence;

import java.time.Instant;

/**
 * Common contract for entities that can be locked out after repeated failed
 * login attempts (INSA CWE-307 remediation). Implemented by both
 * {@code Customer} and {@code AdminUser} so {@code LoginAttemptService} can
 * manipulate either through a single code path.
 *
 * <p>Implementations are expected to persist the three lockout fields
 * ({@code failedLoginAttempts}, {@code lockedUntil}, {@code lockoutSequence})
 * as JPA columns — see Flyway {@code V21__login_attempt_tracking.sql}.
 */
public interface LockableAccount {

    /** Primary key — used for logging. */
    Long getId();

    /** Email address — used for logging and the audit log. */
    String getEmail();

    /** Current consecutive failed-attempt count (resets on success or lockout). */
    int getFailedLoginAttempts();

    void setFailedLoginAttempts(int count);

    /** If non-null and in the future, the account is locked until this instant. */
    Instant getLockedUntil();

    void setLockedUntil(Instant until);

    /** How many times this account has been locked (drives progressive doubling). */
    int getLockoutSequence();

    void setLockoutSequence(int sequence);
}
