package com.gadaaistar.persistence.customer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.gadaaistar.common.CustomerStatus;
import com.gadaaistar.common.CustomerType;
import com.gadaaistar.persistence.BaseEntity;
import com.gadaaistar.persistence.LockableAccount;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer")
public class Customer extends BaseEntity implements LockableAccount {

    @Column(nullable = false, unique = true, length = 255)
    public String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 16)
    public CustomerType customerType = CustomerType.B2C;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public CustomerStatus status = CustomerStatus.PENDING_VERIFICATION;

    @Column(name = "first_name", length = 120)
    public String firstName;

    @Column(name = "last_name", length = 120)
    public String lastName;

    @Column(name = "company_name", length = 255)
    public String companyName;

    @Column(name = "company_tin", length = 64)
    public String companyTin;

    @Column(name = "company_vat_number", length = 64)
    public String companyVatNumber;

    @Column(name = "business_license_url", length = 500)
    public String businessLicenseUrl;

    @Column(length = 64)
    public String phone;

    @Column(name = "preferred_currency", nullable = false, length = 3)
    public String preferredCurrency = "USD";

    @Column(name = "preferred_locale", nullable = false, length = 10)
    public String preferredLocale = "en-US";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    public CustomerGroup group;

    @Column(name = "email_verified_at")
    public Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    // --- INSA CWE-307: brute-force lockout tracking ---
    @Column(name = "failed_login_attempts", nullable = false)
    public int failedLoginAttempts;

    @Column(name = "locked_until")
    public Instant lockedUntil;

    @Column(name = "lockout_sequence", nullable = false)
    public int lockoutSequence;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<CustomerAddress> addresses = new ArrayList<>();

    // ------------------------------------------------------------------------
    // Convenience
    // ------------------------------------------------------------------------
    public String displayName() {
        if (companyName != null && !companyName.isBlank()) return companyName;
        var full = String.join(" ",
                firstName == null ? "" : firstName,
                lastName  == null ? "" : lastName).trim();
        return full.isBlank() ? email : full;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    // --- LockableAccount ---
    @Override public Long getId() { return id; }
    @Override public String getEmail() { return email; }
    @Override public int getFailedLoginAttempts() { return failedLoginAttempts; }
    @Override public void setFailedLoginAttempts(int count) { this.failedLoginAttempts = count; }
    @Override public Instant getLockedUntil() { return lockedUntil; }
    @Override public void setLockedUntil(Instant until) { this.lockedUntil = until; }
    @Override public int getLockoutSequence() { return lockoutSequence; }
    @Override public void setLockoutSequence(int sequence) { this.lockoutSequence = sequence; }
}
