package com.gadaaistar.storefront.address;

import com.gadaaistar.persistence.customer.CustomerAddress;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AddressMapper {

    public AddressDtos.AddressDto toDto(CustomerAddress a) {
        return new AddressDtos.AddressDto(
                a.id,
                a.addressType,
                a.isDefault,
                a.fullName,
                a.company,
                a.streetLine1,
                a.streetLine2,
                a.city,
                a.region,
                a.postalCode,
                a.countryCode,
                a.phone,
                a.createdAt,
                a.updatedAt
        );
    }
}
