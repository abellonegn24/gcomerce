package com.gadaaistar.persistence.auth;

import java.time.Instant;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerRefreshTokenRepository implements PanacheRepositoryBase<CustomerRefreshToken, String> {

    public Optional<CustomerRefreshToken> findActive(String id) {
        return find("id = ?1 AND revokedAt IS NULL AND expiresAt > ?2", id, Instant.now())
                .firstResultOptional();
    }

    public long revokeAllForCustomer(Long customerId) {
        return update("revokedAt = ?1 WHERE customer.id = ?2 AND revokedAt IS NULL",
                Instant.now(), customerId);
    }
}
