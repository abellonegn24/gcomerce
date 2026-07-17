package com.gadaaistar.persistence.customer;

import java.time.Instant;

import com.gadaaistar.common.AddressType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "customer_address")
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 16)
    public AddressType addressType = AddressType.BOTH;

    @Column(name = "is_default", nullable = false)
    public boolean isDefault = false;

    @Column(name = "full_name", length = 200)
    public String fullName;

    @Column(length = 255)
    public String company;

    @Column(name = "street_line1", nullable = false, length = 255)
    public String streetLine1;

    @Column(name = "street_line2", length = 255)
    public String streetLine2;

    @Column(nullable = false, length = 120)
    public String city;

    @Column(length = 120)
    public String region;

    @Column(name = "postal_code", length = 32)
    public String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    public String countryCode;

    @Column(length = 64)
    public String phone;

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
}
