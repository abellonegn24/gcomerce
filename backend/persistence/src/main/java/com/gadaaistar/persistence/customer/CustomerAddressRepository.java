package com.gadaaistar.persistence.customer;

import java.util.List;
import java.util.Optional;

import com.gadaaistar.common.AddressType;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerAddressRepository implements PanacheRepository<CustomerAddress> {

    public List<CustomerAddress> listForCustomer(Long customerId) {
        return list("customer.id",
                Sort.by("isDefault").descending().and("updatedAt", Sort.Direction.Descending),
                customerId);
    }

    public Optional<CustomerAddress> findForCustomer(Long customerId, Long addressId) {
        return find("id = ?1 AND customer.id = ?2", addressId, customerId).firstResultOptional();
    }

    /** Clear the {@code is_default} flag on every other address of the given customer + type. */
    public int clearDefaultFor(Long customerId, AddressType type) {
        return (int) update(
                "isDefault = false WHERE customer.id = ?1 AND (addressType = ?2 OR addressType = ?3) AND isDefault = true",
                customerId, type, AddressType.BOTH);
    }
}
