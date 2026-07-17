package com.gadaaistar.persistence.cms;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for {@link CmsPage} entities.
 */
@ApplicationScoped
public class CmsPageRepository implements PanacheRepositoryBase<CmsPage, Long> {

    public Optional<CmsPage> findBySlug(String slug) {
        return find("slug = ?1", slug).firstResultOptional();
    }

    public boolean slugExists(String slug) {
        return count("slug = ?1", slug) > 0;
    }

    public boolean slugExistsExcluding(String slug, long excludeId) {
        return count("slug = ?1 AND id <> ?2", slug, excludeId) > 0;
    }

    public List<CmsPage> listAllOrdered() {
        return list("ORDER BY sortOrder ASC, id ASC");
    }

    public List<CmsPage> listPublished() {
        return list("status = 'PUBLISHED' ORDER BY sortOrder ASC, id ASC");
    }
}
