package com.gadaaistar.admin.customers;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import com.gadaaistar.common.CustomerStatus;
import com.gadaaistar.common.CustomerType;
import com.gadaaistar.common.exceptions.BadRequestException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.common.exceptions.ValidationException;
import com.gadaaistar.persistence.auth.CustomerRefreshTokenRepository;
import com.gadaaistar.persistence.auth.LoginAttemptService;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerAddress;
import com.gadaaistar.persistence.customer.CustomerGroup;
import com.gadaaistar.persistence.customer.CustomerGroupRepository;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.persistence.order.CheckoutOrderRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Read + admin-write operations against the {@code customer} table.
 *
 * <p>The heart of this service is a small explicit state machine for
 * {@link CustomerStatus} transitions. Admin-facing status changes are strictly
 * limited to a handful of {@code ACTIVE ↔ SUSPENDED ↔ DEACTIVATED} moves — the
 * {@code PENDING_*} states can only leave via the dedicated approval /
 * verification flows so the audit trail stays clean.
 */
@ApplicationScoped
public class CustomersService {

    private static final Logger LOG = Logger.getLogger(CustomersService.class);

    /**
     * Legal transitions initiated by an admin. Kept intentionally narrow:
     * <ul>
     *   <li>{@code ACTIVE} → {@code SUSPENDED} (temporary block, login refused)</li>
     *   <li>{@code SUSPENDED} → {@code ACTIVE} (unblock)</li>
     *   <li>{@code ACTIVE} / {@code SUSPENDED} → {@code DEACTIVATED} (close account; terminal)</li>
     *   <li>{@code PENDING_APPROVAL} → {@code DEACTIVATED} (reject a B2B application)</li>
     * </ul>
     * <p>Everything else (e.g. {@code PENDING_VERIFICATION} → {@code ACTIVE}) must
     * happen through the customer-side email-verification flow.
     */
    static final Map<CustomerStatus, Set<CustomerStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            CustomerStatus.PENDING_VERIFICATION, EnumSet.of(CustomerStatus.DEACTIVATED),
            CustomerStatus.PENDING_APPROVAL,     EnumSet.of(CustomerStatus.DEACTIVATED),
            CustomerStatus.ACTIVE,               EnumSet.of(CustomerStatus.SUSPENDED, CustomerStatus.DEACTIVATED),
            CustomerStatus.SUSPENDED,            EnumSet.of(CustomerStatus.ACTIVE, CustomerStatus.DEACTIVATED),
            CustomerStatus.DEACTIVATED,          EnumSet.noneOf(CustomerStatus.class)
    );

    @Inject CustomerRepository              customers;
    @Inject CustomerGroupRepository         groups;
    @Inject CustomerRefreshTokenRepository  refreshTokens;
    @Inject CheckoutOrderRepository         orders;
    @Inject B2bNotificationMailService      b2bMail;
    @Inject LoginAttemptService             loginAttempts;

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public CustomersDtos.PagedCustomers list(String q, String rawType, String rawStatus,
                                              int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        var type   = parseType(rawType);
        var status = parseStatus(rawStatus);

        var rows = customers.pageForAdmin(q, type, status, safePage, safeSize)
                .stream()
                .map(this::toRow)
                .toList();
        long total = customers.countForAdmin(q, type, status);
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new CustomersDtos.PagedCustomers(rows, new CustomersDtos.PageMeta(
                total,
                safePage,
                safeSize,
                safePage < totalPages - 1,
                safePage > 0
        ));
    }

    public CustomersDtos.CustomerDetail get(long id) {
        var c = customers.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        return toDetail(c);
    }

    // -------------------------------------------------------------------------
    // Write — status transitions
    // -------------------------------------------------------------------------

    @Transactional
    public CustomersDtos.ActionResponse changeStatus(long id, String rawTarget, String reason, long actorId) {
        var target = parseStatus(rawTarget);
        if (target == null) {
            throw new ValidationException("Missing or invalid target status.");
        }
        var c = customers.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        if (target == c.status) {
            throw new BadRequestException("NO_OP", "Customer is already " + c.status + ".");
        }
        var allowed = ALLOWED_STATUS_TRANSITIONS.getOrDefault(c.status, EnumSet.noneOf(CustomerStatus.class));
        if (!allowed.contains(target)) {
            throw new BadRequestException("ILLEGAL_TRANSITION",
                    "Cannot move customer from " + c.status + " to " + target + ".");
        }
        // Rejecting outbound side-effects: when we suspend or deactivate, kick
        // any live refresh tokens so the customer is logged out on all devices.
        if (target == CustomerStatus.SUSPENDED || target == CustomerStatus.DEACTIVATED) {
            refreshTokens.revokeAllForCustomer(c.id);
        }
        var before = c.status;
        c.status = target;
        LOG.infof("Admin id=%d changed customer id=%d status: %s → %s%s",
                actorId, c.id, before, target,
                reason == null || reason.isBlank() ? "" : " (reason: " + reason.trim() + ")");
        // Send B2B rejection email when a PENDING_APPROVAL B2B customer is rejected.
        if (before == CustomerStatus.PENDING_APPROVAL
                && target == CustomerStatus.DEACTIVATED
                && c.customerType == CustomerType.B2B) {
            b2bMail.sendB2bRejected(c, reason);
        }
        return new CustomersDtos.ActionResponse(c.id, c.status.name(),
                "Status updated to " + target + ".");
    }

    /**
     * Admin approves a B2B application: {@code PENDING_APPROVAL → ACTIVE}. Also
     * force-completes email verification if the customer never confirmed
     * (typical B2B flow: the sales rep vets the license out-of-band).
     */
    @Transactional
    public CustomersDtos.ActionResponse approveB2B(long id, long actorId) {
        var c = customers.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        if (c.customerType != CustomerType.B2B) {
            throw new BadRequestException("NOT_B2B",
                    "Only B2B customers go through the approval flow.");
        }
        if (c.status != CustomerStatus.PENDING_APPROVAL) {
            throw new BadRequestException("NOT_PENDING",
                    "Only B2B customers in PENDING_APPROVAL can be approved (was " + c.status + ").");
        }
        c.status = CustomerStatus.ACTIVE;
        if (c.emailVerifiedAt == null) c.emailVerifiedAt = Instant.now();
        LOG.infof("Admin id=%d approved B2B customer id=%d (%s)", actorId, c.id, c.email);
        // Send B2B approval notification email (best-effort, non-blocking).
        b2bMail.sendB2bApproved(c);
        return new CustomersDtos.ActionResponse(c.id, c.status.name(),
                "B2B account approved and marked ACTIVE.");
    }

    @Transactional
    public CustomersDtos.ActionResponse forceVerifyEmail(long id, long actorId) {
        var c = customers.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        if (c.emailVerifiedAt != null) {
            throw new BadRequestException("ALREADY_VERIFIED",
                    "This customer's email is already verified (" + c.emailVerifiedAt + ").");
        }
        c.emailVerifiedAt = Instant.now();
        // For a B2C signup that only lacks email verification, flip status to ACTIVE too.
        if (c.customerType == CustomerType.B2C && c.status == CustomerStatus.PENDING_VERIFICATION) {
            c.status = CustomerStatus.ACTIVE;
        }
        LOG.infof("Admin id=%d force-verified email for customer id=%d", actorId, c.id);
        return new CustomersDtos.ActionResponse(c.id, c.status.name(),
                "Email marked verified.");
    }

    @Transactional
    public CustomersDtos.ActionResponse revokeSessions(long id, long actorId) {
        var c = customers.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        long revoked = refreshTokens.revokeAllForCustomer(c.id);
        LOG.infof("Admin id=%d revoked %d refresh tokens for customer id=%d", actorId, revoked, c.id);
        return new CustomersDtos.ActionResponse(c.id, c.status.name(),
                "Revoked " + revoked + " active session(s). The customer must sign in again.");
    }

    /**
     * Manually clear a brute-force lockout on a customer account (INSA CWE-307).
     * Lets support staff resolve false lockouts without waiting for the
     * lockout window to expire.
     */
    @Transactional
    public CustomersDtos.ActionResponse unlock(long id, long actorId) {
        var c = customers.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        loginAttempts.unlock(c);
        LOG.infof("Admin id=%d unlocked customer id=%d", actorId, c.id);
        return new CustomersDtos.ActionResponse(c.id, c.status.name(),
                "Account lockout cleared. The customer can sign in again.");
    }

    @Transactional
    public CustomersDtos.ActionResponse setGroup(long id, Long groupId, long actorId) {
        var c = customers.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        if (groupId == null) {
            c.group = null;
            LOG.infof("Admin id=%d cleared customer group for id=%d", actorId, c.id);
            return new CustomersDtos.ActionResponse(c.id, c.status.name(),
                    "Customer group cleared. Storefront pricing falls back to the base list.");
        }
        var group = groups.findByIdOptional(groupId)
                .orElseThrow(() -> new NotFoundException("CustomerGroup", groupId));
        c.group = group;
        LOG.infof("Admin id=%d moved customer id=%d into group %s", actorId, c.id, group.code);
        return new CustomersDtos.ActionResponse(c.id, c.status.name(),
                "Customer group updated to " + group.code + ".");
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private CustomersDtos.CustomerRow toRow(Customer c) {
        return new CustomersDtos.CustomerRow(
                c.id,
                c.email,
                c.displayName(),
                c.customerType == null ? null : c.customerType.name(),
                c.status == null ? null : c.status.name(),
                c.group == null ? null : c.group.code,
                c.preferredCurrency,
                c.emailVerifiedAt != null,
                c.lastLoginAt,
                c.createdAt
        );
    }

    private CustomersDtos.CustomerDetail toDetail(Customer c) {
        var addresses = c.addresses == null ? List.<CustomerAddress>of() : c.addresses;
        var addressDtos = addresses.stream()
                .sorted(Comparator.<CustomerAddress, Boolean>comparing(a -> !a.isDefault)
                        .thenComparing(a -> a.updatedAt == null ? Instant.EPOCH : a.updatedAt,
                                Comparator.reverseOrder()))
                .map(a -> new CustomersDtos.CustomerAddressDto(
                        a.id,
                        a.addressType == null ? "BOTH" : a.addressType.name(),
                        a.isDefault,
                        a.fullName,
                        a.company,
                        a.streetLine1,
                        a.streetLine2,
                        a.city,
                        a.region,
                        a.postalCode,
                        a.countryCode,
                        a.phone
                ))
                .toList();
        long orderCount = orders.countForCustomer(c.id);
        CustomerGroup g = c.group;
        var allowed = ALLOWED_STATUS_TRANSITIONS.getOrDefault(c.status, EnumSet.noneOf(CustomerStatus.class))
                .stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .toList();

        return new CustomersDtos.CustomerDetail(
                c.id,
                c.email,
                c.customerType == null ? null : c.customerType.name(),
                c.status == null ? null : c.status.name(),
                c.firstName,
                c.lastName,
                c.companyName,
                c.companyTin,
                c.companyVatNumber,
                c.businessLicenseUrl,
                c.phone,
                c.preferredCurrency,
                c.preferredLocale,
                g == null ? null : g.id,
                g == null ? null : g.code,
                g == null ? null : g.name,
                c.emailVerifiedAt != null,
                c.emailVerifiedAt,
                c.lastLoginAt,
                c.createdAt,
                c.updatedAt,
                addressDtos,
                orderCount,
                allowed
        );
    }

    // -------------------------------------------------------------------------
    // Parsers
    // -------------------------------------------------------------------------

    private static CustomerType parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return CustomerType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid customer type '" + raw + "'. Expected B2C or B2B.");
        }
    }

    private static CustomerStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return CustomerStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid customer status '" + raw + "'.");
        }
    }
}
