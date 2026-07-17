package com.gadaaistar.persistence.customer;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerGroupRepository implements PanacheRepository<CustomerGroup> {

    public Optional<CustomerGroup> findByCode(String code) {
        return find("code", code).firstResultOptional();
    }

    /** Case-insensitive uniqueness check used by the admin CRUD service. */
    public boolean codeExists(String code) {
        if (code == null || code.isBlank()) return false;
        return count("LOWER(code) = ?1", code.trim().toLowerCase()) > 0;
    }

    /** Sorted list for the admin page — retail-first, then B2B tiers by code. */
    public List<CustomerGroup> listSorted() {
        return listAll(Sort.by("priceTier").and("code"));
    }
}
