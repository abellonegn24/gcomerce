package com.gadaaistar.persistence.catalog;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductMediaRepository implements PanacheRepository<ProductMedia> {
}
