package com.gadaaistar.persistence.auth;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailVerificationTokenRepository implements PanacheRepository<EmailVerificationToken> {

    public Optional<EmailVerificationToken> findByHash(String hash) {
        return find("tokenHash", hash).firstResultOptional();
    }
}
