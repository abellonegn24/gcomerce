package com.gadaaistar.persistence.admin;

import java.time.Instant;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AdminRefreshTokenRepository implements PanacheRepositoryBase<AdminRefreshToken, String> {

    public Optional<AdminRefreshToken> findActive(String id) {
        return find("id = ?1 AND revokedAt IS NULL AND expiresAt > ?2", id, Instant.now())
                .firstResultOptional();
    }

    public long revokeAllForAdmin(Long adminUserId) {
        return update("revokedAt = ?1 WHERE adminUser.id = ?2 AND revokedAt IS NULL",
                Instant.now(), adminUserId);
    }
}
