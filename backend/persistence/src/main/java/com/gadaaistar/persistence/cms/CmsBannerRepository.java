package com.gadaaistar.persistence.cms;

import java.time.Instant;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for {@link CmsBanner} entities.
 */
@ApplicationScoped
public class CmsBannerRepository implements PanacheRepositoryBase<CmsBanner, Long> {

    public List<CmsBanner> listAllOrdered() {
        return list("ORDER BY placement ASC, sortOrder ASC, id ASC");
    }

    /**
     * Active banners for a given placement, considering the current time
     * against the activeFrom / activeTo scheduling window.
     */
    public List<CmsBanner> listActiveByPlacement(String placement, Instant now) {
        return find("""
                placement = ?1 AND active = true \
                AND (activeFrom IS NULL OR activeFrom <= ?2) \
                AND (activeTo IS NULL OR activeTo >= ?2) \
                ORDER BY sortOrder ASC, id ASC
                """, placement, now)
                .list();
    }
}
