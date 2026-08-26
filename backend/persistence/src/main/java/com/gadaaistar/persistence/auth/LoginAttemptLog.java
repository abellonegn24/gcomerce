package com.gadaaistar.persistence.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only audit trail of every login attempt (INSA CWE-307 remediation).
 *
 * <p>Satisfies the auditor's "failed authentication attempts should be logged
 * and monitored" requirement and feeds future alerting / admin dashboards.
 * Written by {@code LoginAttemptService} on both successful and failed logins.
 *
 * <p>{@code accountType} is {@code "CUSTOMER"} or {@code "ADMIN"}.
 */
@Entity
@Table(name = "login_attempt_log")
public class LoginAttemptLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "account_type", nullable = false, length = 16)
    public String accountType;

    @Column(nullable = false, length = 255)
    public String email;

    @Column(name = "ip_address", length = 64)
    public String ipAddress;

    @Column(name = "user_agent", length = 500)
    public String userAgent;

    @Column(nullable = false)
    public boolean success;

    @Column(name = "attempted_at", nullable = false)
    public Instant attemptedAt;
}
