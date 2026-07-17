package com.gadaaistar.persistence.order;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderItemRepository implements PanacheRepository<OrderItem> {
}
