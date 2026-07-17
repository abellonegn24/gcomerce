package com.gadaaistar.persistence.auth;

import java.time.Instant;

import com.gadaaistar.persistence.customer.Customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    public String tokenHash;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "used_at")
    public Instant usedAt;

    @Column(name = "ip_address", length = 64)
    public String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    public boolean isActive() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }
}
