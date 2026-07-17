package com.gadaaistar.persistence.catalog;

import java.util.List;
import java.util.Optional;

import com.gadaaistar.common.ProductCategory;
import com.gadaaistar.common.ProductStatus;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductRepository implements PanacheRepository<Product> {

    public Optional<Product> findBySku(String sku) {
        return find("sku", sku).firstResultOptional();
    }

    public Optional<Product> findBySlug(String slug) {
        return find("slug", slug).firstResultOptional();
    }

    /** Public-facing (ACTIVE only) list, optionally filtered by category, paged. */
    public List<Product> listActive(ProductCategory category, int pageIndex, int pageSize) {
        var sort = Sort.by("name").ascending();
        if (category == null) {
            return find("status", sort, ProductStatus.ACTIVE)
                    .page(Page.of(pageIndex, pageSize))
                    .list();
        }
        return find("status = ?1 AND category = ?2", sort, ProductStatus.ACTIVE, category)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public long countActive(ProductCategory category) {
        if (category == null) return count("status", ProductStatus.ACTIVE);
        return count("status = ?1 AND category = ?2", ProductStatus.ACTIVE, category);
    }

    /** All active products (no pagination) — used by SEO sitemap. */
    public List<Product> findAllActive() {
        return find("status", Sort.by("name").ascending(), ProductStatus.ACTIVE).list();
    }

    /** Storefront search — ACTIVE only, case-insensitive LIKE on sku/name/slug. */
    public List<Product> searchActive(String q, ProductCategory category, int pageIndex, int pageSize) {
        var sort = Sort.by("name").ascending();
        var pattern = "%" + q.toLowerCase() + "%";
        if (category == null) {
            return find("status = ?1 AND (LOWER(sku) LIKE ?2 OR LOWER(name) LIKE ?2 OR LOWER(slug) LIKE ?2)",
                        sort, ProductStatus.ACTIVE, pattern)
                    .page(Page.of(pageIndex, pageSize))
                    .list();
        }
        return find("status = ?1 AND category = ?2 AND (LOWER(sku) LIKE ?3 OR LOWER(name) LIKE ?3 OR LOWER(slug) LIKE ?3)",
                    sort, ProductStatus.ACTIVE, category, pattern)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public long searchActiveCount(String q, ProductCategory category) {
        var pattern = "%" + q.toLowerCase() + "%";
        if (category == null) {
            return count("status = ?1 AND (LOWER(sku) LIKE ?2 OR LOWER(name) LIKE ?2 OR LOWER(slug) LIKE ?2)",
                        ProductStatus.ACTIVE, pattern);
        }
        return count("status = ?1 AND category = ?2 AND (LOWER(sku) LIKE ?3 OR LOWER(name) LIKE ?3 OR LOWER(slug) LIKE ?3)",
                    ProductStatus.ACTIVE, category, pattern);
    }

    /** Admin list — all statuses, paged, optional text search on sku/name/slug. */
    public List<Product> adminSearch(String q, ProductStatus status, ProductCategory category,
                                     int pageIndex, int pageSize) {
        var sql = new StringBuilder("1=1");
        var params = new java.util.ArrayList<Object>();
        int idx = 1;
        if (q != null && !q.isBlank()) {
            sql.append(" AND (LOWER(sku) LIKE ?").append(idx)
               .append(" OR LOWER(name) LIKE ?").append(idx)
               .append(" OR LOWER(slug) LIKE ?").append(idx).append(")");
            params.add("%" + q.toLowerCase() + "%");
            idx++;
        }
        if (status != null) {
            sql.append(" AND status = ?").append(idx);
            params.add(status);
            idx++;
        }
        if (category != null) {
            sql.append(" AND category = ?").append(idx);
            params.add(category);
            idx++;
        }
        return find(sql.toString(), Sort.by("updatedAt").descending(), params.toArray())
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public long adminSearchCount(String q, ProductStatus status, ProductCategory category) {
        var sql = new StringBuilder("1=1");
        var params = new java.util.ArrayList<Object>();
        int idx = 1;
        if (q != null && !q.isBlank()) {
            sql.append(" AND (LOWER(sku) LIKE ?").append(idx)
               .append(" OR LOWER(name) LIKE ?").append(idx)
               .append(" OR LOWER(slug) LIKE ?").append(idx).append(")");
            params.add("%" + q.toLowerCase() + "%");
            idx++;
        }
        if (status != null) {
            sql.append(" AND status = ?").append(idx);
            params.add(status);
            idx++;
        }
        if (category != null) {
            sql.append(" AND category = ?").append(idx);
            params.add(category);
            idx++;
        }
        return count(sql.toString(), params.toArray());
    }
}
