package com.gadaaistar.storefront.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.gadaaistar.common.CustomerStatus;
import com.gadaaistar.common.CustomerType;
import com.gadaaistar.common.exceptions.AccountLockedException;
import com.gadaaistar.common.exceptions.UnauthorizedException;
import com.gadaaistar.persistence.auth.CustomerRefreshToken;
import com.gadaaistar.persistence.auth.LoginAttemptService;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerGroupRepository;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.storefront.security.RefreshTokenService;
import com.gadaaistar.web.security.JwtIssuer;
import com.gadaaistar.web.security.PasswordHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the lockout behaviour wired into {@link AuthService#login}
 * (INSA CWE-307 remediation).
 *
 * <p>Uses Mockito to stub {@link CustomerRepository}, {@link PasswordHasher},
 * and {@link LoginAttemptService} — no Quarkus runtime or database required.
 * The test verifies that {@code AuthService.login} orchestrates the lockout
 * service correctly; the lockout policy itself is covered by
 * {@code LoginAttemptServiceTest}.
 *
 * <p>Test matrix (mirrors plan §2i):
 * <ul>
 *   <li>Unknown email → 401 + dummy bcrypt burned (anti-enumeration).</li>
 *   <li>Wrong password → {@code recordFailure} called + 401.</li>
 *   <li>Locked account → {@code AccountLockedException} propagates BEFORE password verify.</li>
 *   <li>Correct password → {@code recordSuccess} called.</li>
 *   <li>Correct password after lockout expiry → {@code recordSuccess} called (lockout cleared).</li>
 * </ul>
 */
class AuthServiceLockoutTest {

    private AuthService service;
    private CustomerRepository customers;
    private PasswordHasher hasher;
    private LoginAttemptService loginAttempts;
    private JwtIssuer jwtIssuer;
    private RefreshTokenService refreshTokens;
    private AuthMapper mapper;
    private CustomerGroupRepository groups;

    private static final String EMAIL    = "alice@test.com";
    private static final String PASSWORD = "correct-password";
    private static final String WRONG    = "wrong-password";
    private static final String IP       = "196.189.236.206";
    private static final String UA       = "INSA-Scanner/1.0";

    @BeforeEach
    void setUp() throws Exception {
        service = new AuthService();
        customers     = mock(CustomerRepository.class);
        hasher        = mock(PasswordHasher.class);
        loginAttempts = mock(LoginAttemptService.class);
        jwtIssuer     = mock(JwtIssuer.class);
        refreshTokens = mock(RefreshTokenService.class);
        mapper        = mock(AuthMapper.class);
        groups        = mock(CustomerGroupRepository.class);

        inject("customers", customers);
        inject("hasher", hasher);
        inject("loginAttempts", loginAttempts);
        inject("jwtIssuer", jwtIssuer);
        inject("refreshTokens", refreshTokens);
        inject("mapper", mapper);
        inject("groups", groups);
    }

    private void inject(String name, Object value) throws Exception {
        var f = AuthService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private Customer activeCustomer() {
        var c = new Customer();
        c.id = 42L;
        c.email = EMAIL;
        c.passwordHash = "$2a$10$hashedvalue";
        c.customerType = CustomerType.B2C;
        c.status = CustomerStatus.ACTIVE;
        c.emailVerifiedAt = Instant.now();
        return c;
    }

    private AuthDtos.LoginRequest loginReq(String password) {
        return new AuthDtos.LoginRequest(EMAIL, password);
    }

    /** Stub the session-issuance chain so login() can complete on the success path. */
    private void stubSessionIssuance(Customer c) {
        when(jwtIssuer.issueAccessToken(any(), eq(c.id), eq(c.email), any(String[].class)))
                .thenReturn(new JwtIssuer.IssuedToken("access-token", Instant.now().plusSeconds(900)));
        var tokenRow = new CustomerRefreshToken();
        tokenRow.expiresAt = Instant.now().plusSeconds(86400);
        when(refreshTokens.issueForCustomer(eq(c), anyString(), anyString()))
                .thenReturn(new RefreshTokenService.IssuedRefresh("refresh-token", tokenRow));
        when(mapper.toDto(c)).thenReturn(new AuthDtos.CustomerDto(
                c.id, c.email, c.customerType, c.status, null, null, null, null, null,
                "USD", "en-US", null, true, Instant.now()));
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Unknown email (anti-enumeration)")
    class UnknownEmail {
        @Test
        @DisplayName("throws 401 INVALID_CREDENTIALS and burns a dummy bcrypt verify")
        void unknownEmail() {
            when(customers.findByEmail(EMAIL)).thenReturn(Optional.empty());

            var ex = assertThrows(UnauthorizedException.class,
                    () -> service.login(loginReq(PASSWORD), IP, UA));
            assertEquals("INVALID_CREDENTIALS", ex.code());

            // Dummy verify must run so timing matches the wrong-password path.
            verify(hasher, times(1)).verifyDummy();
            // No lockout interaction — there's no account to lock.
            verify(loginAttempts, never()).checkNotLocked(any());
            verify(loginAttempts, never()).recordFailure(any(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Wrong password")
    class WrongPassword {
        @Test
        @DisplayName("calls recordFailure and throws 401")
        void wrongPassword() {
            var c = activeCustomer();
            when(customers.findByEmail(EMAIL)).thenReturn(Optional.of(c));
            when(hasher.verify(WRONG, c.passwordHash)).thenReturn(false);

            var ex = assertThrows(UnauthorizedException.class,
                    () -> service.login(loginReq(WRONG), IP, UA));
            assertEquals("INVALID_CREDENTIALS", ex.code());

            verify(loginAttempts, times(1)).checkNotLocked(c);
            verify(loginAttempts, times(1)).recordFailure(eq(c), eq(IP), eq(UA),
                    eq(LoginAttemptService.AccountType.CUSTOMER));
            verify(loginAttempts, never()).recordSuccess(any(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Locked account")
    class LockedAccount {
        @Test
        @DisplayName("AccountLockedException propagates BEFORE password verify")
        void lockedBeforePassword() {
            var c = activeCustomer();
            when(customers.findByEmail(EMAIL)).thenReturn(Optional.of(c));
            // checkNotLocked throws — simulates a live lockout.
            doThrow(new AccountLockedException(600))
                    .when(loginAttempts).checkNotLocked(c);

            var ex = assertThrows(AccountLockedException.class,
                    () -> service.login(loginReq(PASSWORD), IP, UA));
            assertEquals(429, ex.status());
            assertEquals("ACCOUNT_LOCKED", ex.code());

            // Password must NOT be verified while locked.
            verify(hasher, never()).verify(anyString(), anyString());
            verify(loginAttempts, never()).recordFailure(any(), anyString(), anyString(), any());
            verify(loginAttempts, never()).recordSuccess(any(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Correct password")
    class CorrectPassword {
        @Test
        @DisplayName("calls recordSuccess and returns a session")
        void success() {
            var c = activeCustomer();
            when(customers.findByEmail(EMAIL)).thenReturn(Optional.of(c));
            when(hasher.verify(PASSWORD, c.passwordHash)).thenReturn(true);
            stubSessionIssuance(c);

            var resp = service.login(loginReq(PASSWORD), IP, UA);
            assertNotNull(resp);

            verify(loginAttempts, times(1)).checkNotLocked(c);
            verify(loginAttempts, times(1)).recordSuccess(eq(c), eq(IP), eq(UA),
                    eq(LoginAttemptService.AccountType.CUSTOMER));
            verify(loginAttempts, never()).recordFailure(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("correct password after lockout expiry → recordSuccess (lockout cleared)")
        void successAfterExpiry() {
            var c = activeCustomer();
            // Simulate an expired lockout: lockedUntil in the past, sequence > 0.
            c.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
            c.setLockoutSequence(2);

            when(customers.findByEmail(EMAIL)).thenReturn(Optional.of(c));
            when(hasher.verify(PASSWORD, c.passwordHash)).thenReturn(true);
            stubSessionIssuance(c);

            // checkNotLocked does NOT throw (lockout expired).
            var resp = service.login(loginReq(PASSWORD), IP, UA);
            assertNotNull(resp);

            verify(loginAttempts, times(1)).checkNotLocked(c);
            verify(loginAttempts, times(1)).recordSuccess(eq(c), eq(IP), eq(UA),
                    eq(LoginAttemptService.AccountType.CUSTOMER));
        }
    }
}
