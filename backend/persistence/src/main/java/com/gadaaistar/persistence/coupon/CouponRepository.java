package com.gadaaistar.persistence.coupon;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CouponRepository implements PanacheRepository<Coupon> {

    public Optional<Coupon> findByCode(String code) {
        return find("code", code).firstResultOptional();
    }

    public boolean codeExists(String code) {
        return count("code", code) > 0;
    }

    public List<Coupon> listAll(int page, int size) {
        return findAll(Sort.by("createdAt").descending())
                .page(Page.of(page, size))
                .list();
    }

    public long countAll() {
        return count();
    }

    public long countRedemptionsForCustomer(long couponId, long customerId) {
        return getEntityManager()
                .createQuery(
                        "SELECT COUNT(r) FROM CouponRedemption r WHERE r.coupon.id = :cid AND r.customer.id = :custId",
                        Long.class)
                .setParameter("cid", couponId)
                .setParameter("custId", customerId)
                .getSingleResult();
    }
}
