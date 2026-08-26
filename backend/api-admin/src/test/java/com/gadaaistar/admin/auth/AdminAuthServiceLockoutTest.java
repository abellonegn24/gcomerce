package com.gadaaistar.admin.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.gadaaistar.common.Audience;
import com.gadaaistar.common.exceptions.AccountLockedException;
import com.gadaaistar.common.exceptions.UnauthorizedException;
import com.gadaaistar.persistence.admin.AdminRefreshToken;
import com.gadaaistar.persistence.admin.AdminUser;
import com.gadaaistar.persistence.admin.AdminUserRepository;
import com.gadaaistar.persistence.auth.LoginAttemptService;
import com.gadaaistar.admin.security.AdminRefreshTokenService;
import com.gadaaistar.admin.security.TotpService;
import com.gadaaistar.web.security.JwtIssuer;
import com.gadaaistar.web.security.PasswordHasher;
import com.nimbusds.jwt.JWTClaimsSet;

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
 * Unit tests for the lockout behaviour wired into {@link AdminAuthService}
 * (INSA CWE-307 remediation).
 *
 * <p>Uses Mockito to stub the repository, hasher, TOTP service, JWT issuer,
 * refresh-token service, and {@link LoginAttemptService} — no Quarkus runtime
 * or database required. Verifies that both the password step ({@code login})
 * and the MFA step ({@code verifyMfa}) orchestrate the lockout service
 * correctly.
 *
 * <p>Test matrix (mirrors plan §2i):
 * <ul>
 *   <li>Unknown email → 401 + dummy bcrypt burned.</li>
 *   <li>Wrong password → {@code recordFailure} called + 401.</li>
 *   <li>Locked account → {@code AccountLockedException} BEFORE password verify.</li>
 *   <li>Correct password → MFA challenge issued; counter cleared; no recordSuccess yet.</li>
 *   <li>Wrong TOTP code → {@code recordFailure} called + 401.</li>
 *   <li>Locked during MFA → {@code AccountLockedException} BEFORE TOTP verify.</li>
 *   <li>Correct TOTP → {@code recordSuccess} called.</li>
 * </ul>
 */
class AdminAuthServiceLockoutTest {

    private AdminAuthService service;
    private AdminUserRepository admins;
    private PasswordHasher hasher;
    private LoginAttemptService loginAttempts;
    private TotpService totp;
    private JwtIssuer jwtIssuer;
    private AdminRefreshTokenService refreshTokens;
    private AdminAuthMapper mapper;

    private static final String EMAIL    = "admin@test.com";
    private static final String PASSWORD = "correct-password";
    private static final String WRONG    = "wrong-password";
    private static final String TOTP     = "123456";
    private static final String BAD_TOTP = "000000";
    private static final String IP       = "196.189.236.206";
    private static final String UA       = "INSA-Scanner/1.0";
    private static final String CHALLENGE_TOKEN = "challenge-jwt";

    @BeforeEach
    void setUp() throws Exception {
        service = new AdminAuthService();
        admins       = mock(AdminUserRepository.class);
        hasher       = mock(PasswordHasher.class);
        loginAttempts = mock(LoginAttemptService.class);
        totp         = mock(TotpService.class);
        jwtIssuer    = mock(JwtIssuer.class);
        refreshTokens = mock(AdminRefreshTokenService.class);
        mapper       = mock(AdminAuthMapper.class);

        inject("admins", admins);
        inject("hasher", hasher);
        inject("loginAttempts", loginAttempts);
        inject("totp", totp);
        inject("jwtIssuer", jwtIssuer);
        inject("refreshTokens", refreshTokens);
        inject("mapper", mapper);

        // Default config values via reflection.
        setInt("challengeTtlSeconds", 300);
        setString("mfaIssuer", "Gadaa Istar Admin");
    }

    private void inject(String name, Object value) throws Exception {
        var f = AdminAuthService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private void setInt(String name, int value) throws Exception {
        var f = AdminAuthService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private void setString(String name, String value) throws Exception {
        var f = AdminAuthService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private AdminUser activeAdminWithMfa() {
        var a = new AdminUser();
        a.id = 7L;
        a.email = EMAIL;
        a.passwordHash = "$2a$10$hashedvalue";
        a.status = "ACTIVE";
        a.mfaSecret = "JBSWY3DPEHPK3PXP";
        a.mfaEnrolledAt = Instant.now().minus(Duration.ofDays(1));
        return a;
    }

    private AdminAuthDtos.LoginRequest loginReq(String password) {
        return new AdminAuthDtos.LoginRequest(EMAIL, password);
    }

    private AdminAuthDtos.VerifyMfaRequest mfaReq(String code) {
        return new AdminAuthDtos.VerifyMfaRequest(CHALLENGE_TOKEN, code);
    }

    /** Stub the challenge JWT issuance so login() can complete on the success path. */
    private void stubChallengeJwt(AdminUser a) {
        when(jwtIssuer.issueShortLivedToken(
                eq(Audience.ADMIN), eq(a.id), anyString(), any(Duration.class), any()))
                .thenReturn(new JwtIssuer.IssuedToken(CHALLENGE_TOKEN, Instant.now().plusSeconds(300)));
    }

    /** Stub the session-issuance chain so verifyMfa() can complete on the success path. */
    private void stubSessionIssuance(AdminUser a) {
        when(jwtIssuer.issueAccessToken(eq(Audience.ADMIN), eq(a.id), eq(a.email), any(String[].class)))
                .thenReturn(new JwtIssuer.IssuedToken("access-token", Instant.now().plusSeconds(900)));
        var tokenRow = new AdminRefreshToken();
        tokenRow.expiresAt = Instant.now().plusSeconds(86400);
        when(refreshTokens.issueForAdmin(eq(a), anyString(), anyString()))
                .thenReturn(new AdminRefreshTokenService.IssuedRefresh("refresh-token", tokenRow));
        when(mapper.toDto(a)).thenReturn(new AdminAuthDtos.AdminUserDto(
                a.id, a.email, "Admin User", "SUPERADMIN", a.status, true, Instant.now()));
    }

    // -------------------------------------------------------------------------
    // Step 1 — password login
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("login (password step)")
    class LoginStep {

        @Test
        @DisplayName("unknown email → 401 + dummy bcrypt burned")
        void unknownEmail() {
            when(admins.findByEmail(EMAIL)).thenReturn(Optional.empty());

            var ex = assertThrows(UnauthorizedException.class,
                    () -> service.login(loginReq(PASSWORD), IP, UA));
            assertEquals("INVALID_CREDENTIALS", ex.code());

            verify(hasher, times(1)).verifyDummy();
            verify(loginAttempts, never()).checkNotLocked(any());
            verify(loginAttempts, never()).recordFailure(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("wrong password → recordFailure called + 401")
        void wrongPassword() {
            var a = activeAdminWithMfa();
            when(admins.findByEmail(EMAIL)).thenReturn(Optional.of(a));
            when(hasher.verify(WRONG, a.passwordHash)).thenReturn(false);

            var ex = assertThrows(UnauthorizedException.class,
                    () -> service.login(loginReq(WRONG), IP, UA));
            assertEquals("INVALID_CREDENTIALS", ex.code());

            verify(loginAttempts, times(1)).checkNotLocked(a);
            verify(loginAttempts, times(1)).recordFailure(eq(a), eq(IP), eq(UA),
                    eq(LoginAttemptService.AccountType.ADMIN));
            verify(loginAttempts, never()).recordSuccess(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("locked account → AccountLockedException BEFORE password verify")
        void lockedBeforePassword() {
            var a = activeAdminWithMfa();
            when(admins.findByEmail(EMAIL)).thenReturn(Optional.of(a));
            doThrow(new AccountLockedException(600))
                    .when(loginAttempts).checkNotLocked(a);

            var ex = assertThrows(AccountLockedException.class,
                    () -> service.login(loginReq(PASSWORD), IP, UA));
            assertEquals(429, ex.status());
            assertEquals("ACCOUNT_LOCKED", ex.code());

            verify(hasher, never()).verify(anyString(), anyString());
            verify(loginAttempts, never()).recordFailure(any(), anyString(), anyString(), any());
            verify(loginAttempts, never()).recordSuccess(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("correct password → MFA challenge issued; recordSuccess NOT called yet")
        void correctPasswordIssuesChallenge() {
            var a = activeAdminWithMfa();
            when(admins.findByEmail(EMAIL)).thenReturn(Optional.of(a));
            when(hasher.verify(PASSWORD, a.passwordHash)).thenReturn(true);
            stubChallengeJwt(a);

            var resp = service.login(loginReq(PASSWORD), IP, UA);
            assertNotNull(resp);
            assertEquals(AdminAuthDtos.LoginState.MFA_REQUIRED, resp.state());
            assertNotNull(resp.challengeToken());

            verify(loginAttempts, times(1)).checkNotLocked(a);
            // recordSuccess is deferred to verifyMfa — only the counter is cleared.
            verify(loginAttempts, never()).recordSuccess(any(), anyString(), anyString(), any());
            verify(loginAttempts, never()).recordFailure(any(), anyString(), anyString(), any());
        }
    }

    // -------------------------------------------------------------------------
    // Step 2 — TOTP verify
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("verifyMfa (TOTP step)")
    class MfaStep {

        private JWTClaimsSet challengeClaims(long adminId) {
            return new JWTClaimsSet.Builder().subject(String.valueOf(adminId)).build();
        }

        @Test
        @DisplayName("wrong TOTP code → recordFailure called + 401")
        void wrongTotp() {
            var a = activeAdminWithMfa();
            when(jwtIssuer.verifyPurpose(CHALLENGE_TOKEN, Audience.ADMIN, "mfa-challenge"))
                    .thenReturn(challengeClaims(a.id));
            when(admins.findByIdOptional(a.id)).thenReturn(Optional.of(a));
            when(totp.verifyCode(a.mfaSecret, BAD_TOTP)).thenReturn(false);

            var ex = assertThrows(UnauthorizedException.class,
                    () -> service.verifyMfa(mfaReq(BAD_TOTP), IP, UA));
            assertEquals("MFA_INVALID_CODE", ex.code());

            verify(loginAttempts, times(1)).checkNotLocked(a);
            verify(loginAttempts, times(1)).recordFailure(eq(a), eq(IP), eq(UA),
                    eq(LoginAttemptService.AccountType.ADMIN));
            verify(loginAttempts, never()).recordSuccess(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("locked during MFA → AccountLockedException BEFORE TOTP verify")
        void lockedBeforeTotp() {
            var a = activeAdminWithMfa();
            when(jwtIssuer.verifyPurpose(CHALLENGE_TOKEN, Audience.ADMIN, "mfa-challenge"))
                    .thenReturn(challengeClaims(a.id));
            when(admins.findByIdOptional(a.id)).thenReturn(Optional.of(a));
            doThrow(new AccountLockedException(600))
                    .when(loginAttempts).checkNotLocked(a);

            var ex = assertThrows(AccountLockedException.class,
                    () -> service.verifyMfa(mfaReq(TOTP), IP, UA));
            assertEquals(429, ex.status());
            assertEquals("ACCOUNT_LOCKED", ex.code());

            verify(totp, never()).verifyCode(anyString(), anyString());
            verify(loginAttempts, never()).recordFailure(any(), anyString(), anyString(), any());
            verify(loginAttempts, never()).recordSuccess(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("correct TOTP → recordSuccess called + session issued")
        void correctTotp() {
            var a = activeAdminWithMfa();
            when(jwtIssuer.verifyPurpose(CHALLENGE_TOKEN, Audience.ADMIN, "mfa-challenge"))
                    .thenReturn(challengeClaims(a.id));
            when(admins.findByIdOptional(a.id)).thenReturn(Optional.of(a));
            when(totp.verifyCode(a.mfaSecret, TOTP)).thenReturn(true);
            stubSessionIssuance(a);

            var resp = service.verifyMfa(mfaReq(TOTP), IP, UA);
            assertNotNull(resp);
            assertNotNull(resp.accessToken());

            verify(loginAttempts, times(1)).checkNotLocked(a);
            verify(loginAttempts, times(1)).recordSuccess(eq(a), eq(IP), eq(UA),
                    eq(LoginAttemptService.AccountType.ADMIN));
            verify(loginAttempts, never()).recordFailure(any(), anyString(), anyString(), any());
        }
    }
}
