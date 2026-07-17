package com.gadaaistar.admin.customers;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request / response DTOs for admin customer management.
 * Kept as a single file for locality — the API is small on purpose.
 */
public final class CustomersDtos {

    private CustomersDtos() {}

    // -------------------------------------------------------------------------
    // Read models
    // -------------------------------------------------------------------------

    /** One row in the /customers table. */
    public record CustomerRow(
            Long id,
            String email,
            String displayName,
            String customerType,
            String status,
            String groupCode,
            String preferredCurrency,
            boolean emailVerified,
            Instant lastLoginAt,
            Instant createdAt
    ) {}

    public record PageMeta(long total, int page, int size, boolean hasNext, boolean hasPrev) {}

    public record PagedCustomers(List<CustomerRow> items, PageMeta page) {}

    /** Address snapshot used by the detail screen. */
    public record CustomerAddressDto(
            Long id,
            String addressType,        // BILLING / SHIPPING / BOTH
            boolean isDefault,
            String fullName,
            String company,
            String streetLine1,
            String streetLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone
    ) {}

    /** Full customer detail. */
    public record CustomerDetail(
            Long id,
            String email,
            String customerType,       // B2C / B2B
            String status,             // PENDING_VERIFICATION / PENDING_APPROVAL / ACTIVE / SUSPENDED / DEACTIVATED
            String firstName,
            String lastName,
            String companyName,
            String companyTin,
            String companyVatNumber,
            String businessLicenseUrl,
            String phone,
            String preferredCurrency,
            String preferredLocale,
            Long groupId,
            String groupCode,
            String groupName,
            boolean emailVerified,
            Instant emailVerifiedAt,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt,
            List<CustomerAddressDto> addresses,
            long orderCount,
            /** Machine-readable list of legal status codes the admin can move to right now. */
            List<String> allowedNextStatuses
    ) {}

    // -------------------------------------------------------------------------
    // Write requests
    // -------------------------------------------------------------------------

    public record ChangeStatusRequest(
            @NotBlank String status,
            @Size(max = 500) String reason
    ) {}

    /** Body for setting or clearing the customer group. Pass {@code groupId = null} to clear. */
    public record SetGroupRequest(Long groupId) {}

    public record ActionResponse(
            Long customerId,
            String status,
            String message
    ) {}
}
