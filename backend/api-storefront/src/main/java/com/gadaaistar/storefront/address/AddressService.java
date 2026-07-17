package com.gadaaistar.storefront.address;

import java.util.List;

import com.gadaaistar.common.AddressType;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.customer.CustomerAddress;
import com.gadaaistar.persistence.customer.CustomerAddressRepository;
import com.gadaaistar.persistence.customer.CustomerRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * CRUD for the signed-in customer's saved addresses (shipping / billing / both).
 *
 * <p>Business rules:
 * <ul>
 *   <li>Setting {@code isDefault=true} clears the flag on every other address
 *       of the same customer whose type overlaps with the incoming type
 *       (e.g. flagging a SHIPPING address as default also clears the BOTH
 *       address's shipping-default status).</li>
 *   <li>The first address a customer saves is automatically the default.</li>
 * </ul>
 */
@ApplicationScoped
public class AddressService {

    @Inject CustomerAddressRepository addresses;
    @Inject CustomerRepository        customers;
    @Inject AddressMapper             mapper;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    public List<AddressDtos.AddressDto> list(long customerId) {
        return addresses.listForCustomer(customerId).stream().map(mapper::toDto).toList();
    }

    public AddressDtos.AddressDto get(long customerId, long addressId) {
        return mapper.toDto(require(customerId, addressId));
    }

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    @Transactional
    public AddressDtos.AddressDto create(long customerId, AddressDtos.CreateAddressRequest req) {
        var customer = customers.findByIdOptional(customerId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));

        var isFirst = addresses.count("customer.id", customerId) == 0;

        var a = new CustomerAddress();
        a.customer     = customer;
        a.addressType  = req.addressType();
        a.isDefault    = isFirst || req.isDefault();
        a.fullName     = nz(req.fullName());
        a.company      = nz(req.company());
        a.streetLine1  = req.streetLine1();
        a.streetLine2  = nz(req.streetLine2());
        a.city         = req.city();
        a.region       = nz(req.region());
        a.postalCode   = nz(req.postalCode());
        a.countryCode  = req.countryCode().toUpperCase();
        a.phone        = nz(req.phone());

        if (a.isDefault) {
            addresses.clearDefaultFor(customerId, a.addressType);
        }
        addresses.persist(a);
        return mapper.toDto(a);
    }

    @Transactional
    public AddressDtos.AddressDto update(long customerId, long addressId, AddressDtos.UpdateAddressRequest req) {
        var a = require(customerId, addressId);
        if (req.addressType() != null) a.addressType = req.addressType();
        if (req.fullName()    != null) a.fullName    = nz(req.fullName());
        if (req.company()     != null) a.company     = nz(req.company());
        if (req.streetLine1() != null) a.streetLine1 = req.streetLine1();
        if (req.streetLine2() != null) a.streetLine2 = nz(req.streetLine2());
        if (req.city()        != null) a.city        = req.city();
        if (req.region()      != null) a.region      = nz(req.region());
        if (req.postalCode()  != null) a.postalCode  = nz(req.postalCode());
        if (req.countryCode() != null) a.countryCode = req.countryCode().toUpperCase();
        if (req.phone()       != null) a.phone       = nz(req.phone());

        if (Boolean.TRUE.equals(req.isDefault()) && !a.isDefault) {
            addresses.clearDefaultFor(customerId, a.addressType);
            a.isDefault = true;
        } else if (Boolean.FALSE.equals(req.isDefault())) {
            a.isDefault = false;
        }
        return mapper.toDto(a);
    }

    @Transactional
    public void delete(long customerId, long addressId) {
        var a = require(customerId, addressId);
        var wasDefault = a.isDefault;
        var type       = a.addressType;
        addresses.delete(a);

        // Promote another address to default so /checkout always has one to
        // pre-select. Rule: newest of same or BOTH type wins.
        if (wasDefault) {
            addresses.listForCustomer(customerId).stream()
                    .filter(other -> other.addressType == type || other.addressType == AddressType.BOTH
                                     || type == AddressType.BOTH)
                    .findFirst()
                    .ifPresent(next -> next.isDefault = true);
        }
    }

    // -------------------------------------------------------------------------

    private CustomerAddress require(long customerId, long addressId) {
        return addresses.findForCustomer(customerId, addressId)
                .orElseThrow(() -> new NotFoundException("CustomerAddress", addressId));
    }

    /** Blank-string → null, so an accidental "" doesn't leak into the DB. */
    private static String nz(String s) {
        if (s == null) return null;
        var trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
