package com.gadaaistar.persistence.catalog;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PriceListRepository implements PanacheRepository<PriceList> {

    public List<PriceList> findActiveForCustomerGroup(Long customerGroupId) {
        return list("customerGroup.id = ?1 AND active = TRUE", customerGroupId);
    }

    public Optional<PriceList> findFirstActiveForCustomerGroup(Long customerGroupId, String currency) {
        return find("customerGroup.id = ?1 AND active = TRUE AND currency = ?2",
                    customerGroupId, currency)
                .firstResultOptional();
    }
}
