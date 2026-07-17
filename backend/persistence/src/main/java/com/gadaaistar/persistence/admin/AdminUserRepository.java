package com.gadaaistar.persistence.admin;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AdminUserRepository implements PanacheRepository<AdminUser> {

    public Optional<AdminUser> findByEmail(String email) {
        return find("LOWER(email) = ?1", email.toLowerCase()).firstResultOptional();
    }
}
