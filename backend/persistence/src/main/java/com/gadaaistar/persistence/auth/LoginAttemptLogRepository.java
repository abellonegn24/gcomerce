package com.gadaaistar.persistence.auth;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for {@link LoginAttemptLog} — the append-only audit trail
 * of every login attempt. Used by {@code LoginAttemptService} to persist
 * attempt records and by admin monitoring queries to surface suspicious activity.
 */
@ApplicationScoped
public class LoginAttemptLogRepository implements PanacheRepository<LoginAttemptLog> {
}
