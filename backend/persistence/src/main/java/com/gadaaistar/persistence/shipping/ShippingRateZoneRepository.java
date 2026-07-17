package com.gadaaistar.persistence.shipping;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for {@link ShippingRateZone}.
 *
 * <p>Queries are keyed on {@code (shippingMethod, countryCode)} — the unique
 * pair that determines which override applies for a given destination.
 */
@ApplicationScoped
public class ShippingRateZoneRepository implements PanacheRepositoryBase<ShippingRateZone, Long> {

    /**
     * Find the active zone override for a specific method + country.
     * Returns empty if no zone is configured — callers fall back to the
     * method's default rates.
     */
    public Optional<ShippingRateZone> findActiveZone(long methodId, String countryCode) {
        if (countryCode == null || countryCode.isBlank()) return Optional.empty();
        return find("shippingMethod.id = ?1 AND countryCode = ?2 AND active = true",
                    methodId, countryCode.toUpperCase())
                .firstResultOptional();
    }

    /** All zones for a given method, ordered by country code for stable display. */
    public List<ShippingRateZone> listByMethod(long methodId) {
        return list("shippingMethod.id = ?1 ORDER BY countryCode ASC", methodId);
    }

    /** Check if a zone already exists for this (method, country) pair. */
    public boolean existsForMethodAndCountry(long methodId, String countryCode) {
        return count("shippingMethod.id = ?1 AND countryCode = ?2",
                     methodId, countryCode.toUpperCase()) > 0;
    }
}
