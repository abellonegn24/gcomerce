package com.gadaaistar.persistence.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for {@link OrderRefund}. Kept intentionally slim —
 * the aggregate refunded_total is maintained on the parent order row,
 * so lookups here are almost always for the audit-history view, not for
 * business decisions.
 */
@ApplicationScoped
public class OrderRefundRepository implements PanacheRepository<OrderRefund> {

    public Optional<OrderRefund> findByUuid(String uuid) {
        return find("uuid", uuid).firstResultOptional();
    }

    public List<OrderRefund> listForOrder(long orderId) {
        return list("order.id = ?1 order by createdAt asc, id asc", orderId);
    }

    /**
     * Sum of {@link OrderRefund.Status#SUCCEEDED} amounts for an order.
     * Used to sanity-check the denormalised {@code refunded_total} on the
     * parent — normally callers just read that column directly.
     */
    public BigDecimal sumSucceededForOrder(long orderId) {
        var q = getEntityManager().createQuery(
                "select coalesce(sum(r.amount), 0) from OrderRefund r "
                        + "where r.order.id = :oid and r.status = :s", BigDecimal.class);
        q.setParameter("oid", orderId);
        q.setParameter("s", OrderRefund.Status.SUCCEEDED);
        return q.getSingleResult();
    }
}
