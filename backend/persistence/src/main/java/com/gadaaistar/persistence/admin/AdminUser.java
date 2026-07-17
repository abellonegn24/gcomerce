package com.gadaaistar.persistence.admin;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_user")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 255)
    public String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    public String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    public String fullName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    public AdminRole role;

    /** ACTIVE / SUSPENDED / DEACTIVATED — narrow enum; stored as VARCHAR. */
    @Column(nullable = false, length = 32)
    public String status = "ACTIVE";

    /**
     * Base32-encoded TOTP secret. NULL until MFA setup is started (first login).
     * Non-null but with NULL {@link #mfaEnrolledAt} means enrollment in progress.
     */
    @Column(name = "mfa_secret", length = 255)
    public String mfaSecret;

    /** Timestamp of first successful TOTP verify; NULL if enrollment not yet completed. */
    @Column(name = "mfa_enrolled_at")
    public Instant mfaEnrolledAt;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean canLogin() {
        return "ACTIVE".equals(status);
    }

    /** True once the admin has completed TOTP enrollment (verified their first code). */
    public boolean isMfaEnrolled() {
        return mfaSecret != null && mfaEnrolledAt != null;
    }
}
