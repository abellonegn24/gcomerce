package com.gadaaistar.persistence.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.gadaaistar.common.OrderStatus;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CheckoutOrderRepository implements PanacheRepository<CheckoutOrder> {

    public Optional<CheckoutOrder> findByUuid(String uuid) {
        return find("uuid", uuid).firstResultOptional();
    }

    public Optional<CheckoutOrder> findByInvoiceNumber(String invoiceNumber) {
        return find("invoiceNumber", invoiceNumber).firstResultOptional();
    }

    public List<CheckoutOrder> listForCustomer(Long customerId, int page, int size) {
        return find("customer.id", Sort.by("placedAt").descending(), customerId)
                .page(Page.of(page, size))
                .list();
    }

    public long countForCustomer(Long customerId) {
        return count("customer.id", customerId);
    }

    public List<CheckoutOrder> listByStatus(OrderStatus status, int page, int size) {
        return find("status", Sort.by("placedAt").descending(), status)
                .page(Page.of(page, size))
                .list();
    }

    /**
     * Orders in a given status whose {@code placedAt} is strictly older than
     * {@code olderThan}. Ordered oldest-first so a batched sweeper picks the
     * most-abandoned rows first. Used by the abandoned-order sweeper to expire
     * PENDING_PAYMENT rows and release their reservations.
     */
    public List<CheckoutOrder> listStuckSince(OrderStatus status, Instant olderThan, int limit) {
        return find("status = ?1 and placedAt < ?2",
                Sort.by("placedAt").ascending(),
                status, olderThan)
                .page(Page.of(0, limit))
                .list();
    }
}
