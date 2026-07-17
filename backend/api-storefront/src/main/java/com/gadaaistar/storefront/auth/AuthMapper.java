package com.gadaaistar.storefront.auth;

import com.gadaaistar.persistence.customer.Customer;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuthMapper {

    public AuthDtos.CustomerDto toDto(Customer c) {
        return new AuthDtos.CustomerDto(
                c.id,
                c.email,
                c.customerType,
                c.status,
                c.firstName,
                c.lastName,
                c.companyName,
                c.companyTin,
                c.phone,
                c.preferredCurrency,
                c.preferredLocale,
                c.group == null ? null : c.group.code,
                c.isEmailVerified(),
                c.createdAt
        );
    }
}
