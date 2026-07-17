package com.gadaaistar.persistence.customer;

import java.util.List;
import java.util.Optional;

import com.gadaaistar.common.CustomerStatus;
import com.gadaaistar.common.CustomerType;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerRepository implements PanacheRepository<Customer> {

    public Optional<Customer> findByEmail(String email) {
        return find("LOWER(email) = ?1", email.toLowerCase()).firstResultOptional();
    }

    public boolean existsByEmail(String email) {
        return count("LOWER(email) = ?1", email.toLowerCase()) > 0;
    }

    // -------------------------------------------------------------------------
    // Admin list — filter + paged
    // -------------------------------------------------------------------------

    /**
     * Return a single Panache query built from optional filters. Callers use
     * this to run both the paged {@code list()} and the total {@code count()}
     * so the two numbers stay in sync.
     */
    private io.quarkus.hibernate.orm.panache.PanacheQuery<Customer> buildAdminQuery(
            String q, CustomerType type, CustomerStatus status) {
        var jpql = new StringBuilder("1 = 1");
        var params = new Parameters();

        if (q != null && !q.isBlank()) {
            jpql.append(" AND (LOWER(email) LIKE :q OR LOWER(COALESCE(firstName, '')) LIKE :q "
                     + "OR LOWER(COALESCE(lastName, '')) LIKE :q "
                     + "OR LOWER(COALESCE(companyName, '')) LIKE :q)");
            params.and("q", "%" + q.trim().toLowerCase() + "%");
        }
        if (type != null) {
            jpql.append(" AND customerType = :type");
            params.and("type", type);
        }
        if (status != null) {
            jpql.append(" AND status = :status");
            params.and("status", status);
        }
        return find(jpql.toString(), Sort.by("createdAt").descending(), params);
    }

    public List<Customer> pageForAdmin(String q, CustomerType type, CustomerStatus status,
                                        int page, int size) {
        return buildAdminQuery(q, type, status)
                .page(Page.of(page, size))
                .list();
    }

    public long countForAdmin(String q, CustomerType type, CustomerStatus status) {
        return buildAdminQuery(q, type, status).count();
    }

    /**
     * Count how many customers currently reference the given group. Used by the
     * admin CustomerGroupsService to guard a DELETE — a group that is still in
     * use must not be silently dropped, otherwise every customer in it would
     * quietly lose their B2B pricing tier.
     */
    public long countInGroup(long groupId) {
        return count("group.id", groupId);
    }
}
