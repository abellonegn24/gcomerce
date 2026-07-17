package com.gadaaistar.storefront.auth;

import java.time.Instant;

import com.gadaaistar.common.Audience;
import com.gadaaistar.common.CustomerStatus;
import com.gadaaistar.common.CustomerType;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.common.exceptions.UnauthorizedException;
import com.gadaaistar.common.exceptions.ValidationException;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerGroupRepository;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.storefront.security.RefreshTokenService;
import com.gadaaistar.web.security.JwtIssuer;
import com.gadaaistar.web.security.PasswordHasher;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Storefront authentication use-cases: register, login, refresh, logout.
 *
 * <p>Email verification and password-reset flows are wired up as separate
 * services in the same package (post-Phase-1) — the token entities and
 * database columns are already in place from Flyway V2.
 */
@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    @Inject CustomerRepository       customers;
    @Inject CustomerGroupRepository  groups;
    @Inject PasswordHasher       hasher;
    @Inject JwtIssuer            jwtIssuer;
    @Inject RefreshTokenService  refreshTokens;
    @Inject AuthMapper           mapper;
    @Inject EmailVerificationService emailVerification;

    // -------------------------------------------------------------------------
    // Register
    // -------------------------------------------------------------------------

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req, String ip, String userAgent) {
        var normalizedEmail = req.email().trim().toLowerCase();
        if (customers.existsByEmail(normalizedEmail)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists.",
                    java.util.Map.of("email", normalizedEmail));
        }

        var type = parseCustomerType(req.customerType());
        if (type == CustomerType.B2B && (req.companyName() == null || req.companyName().isBlank())) {
            throw new ValidationException("Company name is required for B2B accounts.",
                    java.util.Map.of("companyName", "must not be blank for B2B customers"));
        }

        var customer = new Customer();
        customer.email             = normalizedEmail;
        customer.passwordHash      = hasher.hash(req.password());
        customer.customerType      = type;
        customer.status            = type == CustomerType.B2B
                                        ? CustomerStatus.PENDING_APPROVAL
                                        : CustomerStatus.PENDING_VERIFICATION;
        customer.firstName         = req.firstName().trim();
        customer.lastName          = req.lastName().trim();
        customer.companyName       = req.companyName();
        customer.companyTin        = req.companyTin();
        customer.companyVatNumber  = req.companyVatNumber();
        customer.phone             = req.phone();
        customer.preferredCurrency = req.preferredCurrency() == null || req.preferredCurrency().isBlank()
                                        ? "USD" : req.preferredCurrency();
        customer.preferredLocale   = req.preferredLocale() == null || req.preferredLocale().isBlank()
                                        ? "en-US" : req.preferredLocale();

        if (req.customerGroupCode() != null && !req.customerGroupCode().isBlank()) {
            customer.group = groups.findByCode(req.customerGroupCode())
                    .orElseThrow(() -> new ValidationException("Unknown customer group '"
                            + req.customerGroupCode() + "'."));
        } else {
            customer.group = groups.findByCode("RETAIL").orElse(null);
        }

        customers.persist(customer);
        LOG.infof("Registered new customer id=%d type=%s status=%s", customer.id, customer.customerType, customer.status);

        // Fire off the verification email. B2B still needs manual approval before
        // ACTIVE status but we want their email confirmed regardless.
        try {
            emailVerification.start(customer);
        } catch (Exception mailErr) {
            // Don't fail the registration if the mailer is unavailable — the
            // customer can hit /auth/resend-verification later.
            LOG.warnf(mailErr, "Failed to enqueue verification email for customer id=%d", customer.id);
        }

        return issueSessionForCustomer(customer, ip, userAgent);
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req, String ip, String userAgent) {
        var normalizedEmail = req.email().trim().toLowerCase();
        var customer = customers.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password."));

        if (!hasher.verify(req.password(), customer.passwordHash)) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.");
        }
        if (!customer.status.canLogin()) {
            throw new UnauthorizedException("ACCOUNT_" + customer.status.name(),
                    "This account cannot sign in at this time.");
        }

        customer.lastLoginAt = Instant.now();
        return issueSessionForCustomer(customer, ip, userAgent);
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest req, String ip, String userAgent) {
        var rotated = refreshTokens.rotate(req.refreshToken(), ip, userAgent);
        var customer = rotated.customer();

        if (!customer.status.canLogin()) {
            refreshTokens.revokeAllForCustomer(customer.id);
            throw new UnauthorizedException("ACCOUNT_" + customer.status.name(),
                    "This account cannot sign in at this time.");
        }

        var access = jwtIssuer.issueAccessToken(
                Audience.STOREFRONT, customer.id, customer.email, rolesFor(customer));

        return new AuthDtos.AuthResponse(
                access.token(),
                access.expiresAt(),
                rotated.newRefreshToken(),
                rotated.expiresAt(),
                mapper.toDto(customer));
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    @Transactional
    public void logout(String presentedRefreshToken) {
        if (presentedRefreshToken != null && !presentedRefreshToken.isBlank()) {
            refreshTokens.revoke(presentedRefreshToken);
        }
    }

    // -------------------------------------------------------------------------
    // Me
    // -------------------------------------------------------------------------

    public AuthDtos.CustomerDto me(long customerId) {
        var customer = customers.findByIdOptional(customerId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));
        return mapper.toDto(customer);
    }

    // -------------------------------------------------------------------------
    // Profile updates
    // -------------------------------------------------------------------------

    /**
     * Apply a partial profile update. Null / blank inputs are ignored so a
     * caller can PATCH just a phone number without wiping their address, etc.
     */
    @Transactional
    public AuthDtos.CustomerDto updateProfile(long customerId, AuthDtos.UpdateProfileRequest req) {
        var customer = customers.findByIdOptional(customerId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));

        if (nonBlank(req.firstName()))         customer.firstName = req.firstName().trim();
        if (nonBlank(req.lastName()))          customer.lastName = req.lastName().trim();
        if (nonBlank(req.companyName()))       customer.companyName = req.companyName().trim();
        if (nonBlank(req.companyTin()))        customer.companyTin = req.companyTin().trim();
        if (nonBlank(req.companyVatNumber()))  customer.companyVatNumber = req.companyVatNumber().trim();
        if (nonBlank(req.phone()))             customer.phone = req.phone().trim();
        if (nonBlank(req.preferredCurrency())) customer.preferredCurrency = req.preferredCurrency().trim().toUpperCase();
        if (nonBlank(req.preferredLocale()))   customer.preferredLocale = req.preferredLocale().trim();

        return mapper.toDto(customer);
    }

    /**
     * Verify the current password, hash the new one, and revoke every
     * refresh token so any other device is forced to sign in again.
     */
    @Transactional
    public void changePassword(long customerId, AuthDtos.ChangePasswordRequest req) {
        var customer = customers.findByIdOptional(customerId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));

        if (!hasher.verify(req.currentPassword(), customer.passwordHash)) {
            throw new UnauthorizedException("INVALID_CREDENTIALS",
                    "Current password is incorrect.");
        }
        // Sanity: don't allow reusing the same password.
        if (hasher.verify(req.newPassword(), customer.passwordHash)) {
            throw new ValidationException(
                    "Please choose a password you haven't used recently.");
        }

        customer.passwordHash = hasher.hash(req.newPassword());
        refreshTokens.revokeAllForCustomer(customer.id);
        LOG.infof("Customer id=%d changed password; all sessions revoked.", customer.id);
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    // -------------------------------------------------------------------------

    private AuthDtos.AuthResponse issueSessionForCustomer(Customer customer, String ip, String userAgent) {
        var access = jwtIssuer.issueAccessToken(
                Audience.STOREFRONT, customer.id, customer.email, rolesFor(customer));
        var refresh = refreshTokens.issueForCustomer(customer, ip, userAgent);

        return new AuthDtos.AuthResponse(
                access.token(),
                access.expiresAt(),
                refresh.presentedToken(),
                refresh.tokenRow().expiresAt,
                mapper.toDto(customer));
    }

    private static String[] rolesFor(Customer customer) {
        return new String[] {
                "CUSTOMER",
                customer.customerType == CustomerType.B2B ? "B2B" : "B2C"
        };
    }

    private static CustomerType parseCustomerType(String raw) {
        if (raw == null || raw.isBlank()) return CustomerType.B2C;
        try {
            return CustomerType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid customer type '" + raw + "'. Expected B2C or B2B.");
        }
    }
}
