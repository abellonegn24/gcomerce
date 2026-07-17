package com.gadaaistar.persistence.quote;

import java.util.List;
import java.util.Optional;

import com.gadaaistar.common.QuoteStatus;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuoteRepository implements PanacheRepository<Quote> {

    public Optional<Quote> findByUuid(String uuid) {
        return find("uuid", uuid).firstResultOptional();
    }

    public List<Quote> listForCustomer(Long customerId, int page, int size) {
        return find("customer.id", Sort.by("createdAt").descending(), customerId)
                .page(Page.of(page, size))
                .list();
    }

    public long countForCustomer(Long customerId) {
        return count("customer.id", customerId);
    }

    public List<Quote> listByStatus(QuoteStatus status, int page, int size) {
        return find("status", Sort.by("createdAt").descending(), status)
                .page(Page.of(page, size))
                .list();
    }

    public long countByStatus(QuoteStatus status) {
        return count("status", status);
    }

    public List<Quote> listAll(int page, int size) {
        return findAll(Sort.by("createdAt").descending())
                .page(Page.of(page, size))
                .list();
    }
}
