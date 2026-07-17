package com.gadaaistar.persistence.admin;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "role")
public class AdminRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 64)
    public String name;

    @Column(length = 255)
    public String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
