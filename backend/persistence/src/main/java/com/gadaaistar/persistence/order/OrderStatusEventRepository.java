package com.gadaaistar.persistence.order;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderStatusEventRepository implements PanacheRepository<OrderStatusEvent> {

    public List<OrderStatusEvent> historyFor(Long orderId) {
        return list("order.id", Sort.by("occurredAt").and("id"), orderId);
    }
}
