package com.gadaaistar.persistence.cart;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CartItemRepository implements PanacheRepository<CartItem> {

    public Optional<CartItem> findByCartAndProduct(Long cartId, Long productId) {
        return find("cart.id = ?1 AND product.id = ?2 AND variant.id IS NULL", cartId, productId).firstResultOptional();
    }

    public Optional<CartItem> findByCartProductAndVariant(Long cartId, Long productId, Long variantId) {
        if (variantId == null) {
            return findByCartAndProduct(cartId, productId);
        }
        return find("cart.id = ?1 AND product.id = ?2 AND variant.id = ?3", cartId, productId, variantId)
                .firstResultOptional();
    }

    public long deleteByCart(Long cartId) {
        return delete("cart.id", cartId);
    }
}
