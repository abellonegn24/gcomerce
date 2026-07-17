package com.gadaaistar.persistence.cart;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CartRepository implements PanacheRepository<Cart> {

    public Optional<Cart> findByCustomer(Long customerId) {
        return find("customer.id", customerId).firstResultOptional();
    }

    public Optional<Cart> findBySessionKey(String sessionKey) {
        return find("sessionKey", sessionKey).firstResultOptional();
    }
}
