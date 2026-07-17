package com.gadaaistar.storefront.address;

import java.time.Instant;

import com.gadaaistar.common.AddressType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs for the customer-facing address book. */
public final class AddressDtos {

    private AddressDtos() {}

    // -------------------------------------------------------------------------
    // Response
    // -------------------------------------------------------------------------

    public record AddressDto(
            Long id,
            AddressType addressType,
            boolean isDefault,
            String fullName,
            String company,
            String streetLine1,
            String streetLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone,
            Instant createdAt,
            Instant updatedAt
    ) {}

    // -------------------------------------------------------------------------
    // Requests
    // -------------------------------------------------------------------------

    /** All required-on-create fields carry validation. */
    public record CreateAddressRequest(
            @NotNull AddressType addressType,
            boolean isDefault,
            @Size(max = 200) String fullName,
            @Size(max = 255) String company,
            @NotBlank @Size(max = 255) String streetLine1,
            @Size(max = 255) String streetLine2,
            @NotBlank @Size(max = 120) String city,
            @Size(max = 120) String region,
            @Size(max = 32)  String postalCode,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be a 2-letter ISO-3166-1 alpha-2 code")
            String countryCode,
            @Size(max = 64) String phone
    ) {}

    /** Partial update — every field is optional. Blank strings clear the field. */
    public record UpdateAddressRequest(
            AddressType addressType,
            Boolean isDefault,
            @Size(max = 200) String fullName,
            @Size(max = 255) String company,
            @Size(max = 255) String streetLine1,
            @Size(max = 255) String streetLine2,
            @Size(max = 120) String city,
            @Size(max = 120) String region,
            @Size(max = 32)  String postalCode,
            @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be a 2-letter ISO-3166-1 alpha-2 code")
            String countryCode,
            @Size(max = 64) String phone
    ) {}
}
