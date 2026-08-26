package com.gadaaistar.admin.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.gadaaistar.admin.security.AdminRefreshTokenService;
import com.gadaaistar.admin.security.TotpService;
import com.gadaaistar.common.Audience;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.common.exceptions.UnauthorizedException;
import com.gadaaistar.persistence.admin.AdminUser;
import com.gadaaistar.persistence.admin.AdminUserRepository;
import com.gadaaistar.persistence.auth.LoginAttemptService;
import com.gadaaistar.web.security.JwtIssuer;
import com.gadaaistar.web.security.PasswordHasher;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Admin authentication use-cases: two-step login with mandatory TOTP MFA.
 *
 * <p>Step 1 ({@link #login}):
 * <ol>
 *   <li>Verifies email + password.</li>
 *   <li>If {@code mfa_secret} is NULL, generates and persists a new base32 secret
 *       and returns a {@link AdminAuthDtos.LoginState#MFA_ENROLLMENT_REQUIRED}
 *       challenge including a provisioning URI.</li>
 *   <li>Otherwise returns a {@link AdminAuthDtos.LoginState#MFA_REQUIRED} challenge.</li>
 *   <li>Either way, issues a short-lived challenge JWT (purpose=mfa-challenge)
 *       whose subject is the admin's id — the only allowed input to step 2.</li>
 * </ol>
 *
 * <p>Step 2 ({@link #verifyMfa}):
 * <ol>
 *   <li>Verifies the challenge JWT (must be for {@link Audience#ADMIN}, purpose=mfa-challenge).</li>
 *   <li>Verifies the TOTP code against the persisted secret.</li>
 *   <li>On success, sets {@code mfa_enrolled_at} if it was NULL, updates {@code last_login_at},
 *       and issues a full access + refresh token pair.</li>
 * </ol>
 */
@ApplicationScoped
public class AdminAuthService {

    private static final Logger LOG = Logger.getLogger(AdminAuthService.class);
    private static final String CHALLENGE_PURPOSE = "mfa-challenge";

    @Inject AdminUserRepository        admins;
    @Inject PasswordHasher             hasher;
    @Inject JwtIssuer                  jwtIssuer;
    @Inject TotpService                totp;
    @Inject AdminRefreshTokenService   refreshTokens;
    @Inject AdminAuthMapper            mapper;
    @Inject LoginAttemptService        loginAttempts;

    @ConfigProperty(name = "gadaa.admin.mfa.challenge-ttl-seconds", defaultValue = "300")
    int challengeTtlSeconds;

    @ConfigProperty(name = "gadaa.admin.mfa.issuer", defaultValue = "Gadaa Istar Admin")
    String mfaIssuer;

    // -------------------------------------------------------------------------
    // Step 1 — password login
    // -------------------------------------------------------------------------

    @Transactional
    public AdminAuthDtos.LoginChallengeResponse login(AdminAuthDtos.LoginRequest req, String ip, String userAgent) {
        var normalizedEmail = req.email().trim().toLowerCase();
        var admin = admins.findByEmail(normalizedEmail).orElse(null);

        // Constant-time anti-enumeration: when the email is unknown we still
        // burn a BCrypt verify so the response timing matches the "wrong
        // password" branch (INSA CWE-307 hardening).
        if (admin == null) {
            hasher.verifyDummy();
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.");
        }

        // Account-level lockout check runs BEFORE the password verify so a
        // locked account cannot be probed for validity (INSA CWE-307).
        loginAttempts.checkNotLocked(admin);

        if (!hasher.verify(req.password(), admin.passwordHash)) {
            loginAttempts.recordFailure(admin, ip, userAgent, LoginAttemptService.AccountType.ADMIN);
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.");
        }
        if (!admin.canLogin()) {
            throw new UnauthorizedException("ACCOUNT_" + admin.status,
                    "This admin account cannot sign in at this time.");
        }

        // Password verified — issue the MFA challenge. We do NOT call
        // recordSuccess here: the admin session is not yet authenticated
        // (MFA is still pending). The counter is reset only after verifyMfa()
        // succeeds. However, a correct password should not count as a failure,
        // so we clear any stale failed-attempt counter here. If the admin
        // abandons the MFA step the counter stays at 0 — the next wrong
        // password starts a fresh count.
        if (admin.getFailedLoginAttempts() > 0) {
            admin.setFailedLoginAttempts(0);
        }

        // If this is a fresh admin, generate + persist the TOTP secret NOW so we
        // can hand back a provisioning URI. Enrollment isn't complete until
        // verifyMfa() succeeds and sets mfa_enrolled_at.
        AdminAuthDtos.MfaSetup setup = null;
        var state = AdminAuthDtos.LoginState.MFA_REQUIRED;
        if (admin.mfaSecret == null || admin.mfaSecret.isBlank()) {
            var secret = totp.generateSecret();
            admin.mfaSecret = secret;
            admin.mfaEnrolledAt = null;
            setup = new AdminAuthDtos.MfaSetup(
                    secret,
                    totp.buildProvisioningUri(secret, admin.email),
                    mfaIssuer
            );
            state = AdminAuthDtos.LoginState.MFA_ENROLLMENT_REQUIRED;
            LOG.infof("Admin id=%d starting MFA enrollment.", admin.id);
        } else if (admin.mfaEnrolledAt == null) {
            // Secret was set on a prior abandoned attempt — re-use it, re-emit
            // the setup payload so the client can finish enrollment.
            setup = new AdminAuthDtos.MfaSetup(
                    admin.mfaSecret,
                    totp.buildProvisioningUri(admin.mfaSecret, admin.email),
                    mfaIssuer
            );
            state = AdminAuthDtos.LoginState.MFA_ENROLLMENT_REQUIRED;
        }

        var challenge = jwtIssuer.issueShortLivedToken(
                Audience.ADMIN,
                admin.id,
                CHALLENGE_PURPOSE,
                Duration.ofSeconds(challengeTtlSeconds),
                Map.of(
                        "email", admin.email,
                        "state", state.name()
                )
        );

        return new AdminAuthDtos.LoginChallengeResponse(
                state,
                challenge.token(),
                challenge.expiresAt(),
                setup
        );
    }

    // -------------------------------------------------------------------------
    // Step 2 — TOTP verify
    // -------------------------------------------------------------------------

    @Transactional
    public AdminAuthDtos.AuthResponse verifyMfa(AdminAuthDtos.VerifyMfaRequest req,
                                                 String ip, String userAgent) {
        var claims = jwtIssuer.verifyPurpose(req.challengeToken(), Audience.ADMIN, CHALLENGE_PURPOSE);
        long adminId;
        try {
            adminId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("INVALID_TOKEN", "Challenge token subject is invalid.");
        }

        var admin = admins.findByIdOptional(adminId)
                .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "Challenge subject unknown."));
        if (!admin.canLogin()) {
            throw new UnauthorizedException("ACCOUNT_" + admin.status,
                    "This admin account cannot sign in at this time.");
        }
        if (admin.mfaSecret == null || admin.mfaSecret.isBlank()) {
            throw new UnauthorizedException("MFA_NOT_INITIALIZED",
                    "No MFA secret on file. Please restart the sign-in flow.");
        }

        // Lockout check also guards the MFA step — a failed TOTP code counts
        // toward the lockout threshold (INSA CWE-307).
        loginAttempts.checkNotLocked(admin);

        if (!totp.verifyCode(admin.mfaSecret, req.code())) {
            LOG.warnf("MFA code rejected for admin id=%d", admin.id);
            loginAttempts.recordFailure(admin, ip, userAgent, LoginAttemptService.AccountType.ADMIN);
            throw new UnauthorizedException("MFA_INVALID_CODE",
                    "That code was not accepted. Try again with the current 6-digit code.");
        }

        // MFA verified — full authentication complete. Reset the lockout state.
        loginAttempts.recordSuccess(admin, ip, userAgent, LoginAttemptService.AccountType.ADMIN);

        // First successful verify completes enrollment.
        var now = Instant.now();
        if (admin.mfaEnrolledAt == null) {
            admin.mfaEnrolledAt = now;
            LOG.infof("Admin id=%d completed MFA enrollment.", admin.id);
        }
        admin.lastLoginAt = now;

        return issueSession(admin, ip, userAgent);
    }

    // -------------------------------------------------------------------------
    // Refresh / logout / me
    // -------------------------------------------------------------------------

    @Transactional
    public AdminAuthDtos.AuthResponse refresh(AdminAuthDtos.RefreshRequest req, String ip, String userAgent) {
        var rotated = refreshTokens.rotate(req.refreshToken(), ip, userAgent);
        var admin   = rotated.admin();

        if (!admin.canLogin()) {
            refreshTokens.revokeAllForAdmin(admin.id);
            throw new UnauthorizedException("ACCOUNT_" + admin.status,
                    "This admin account cannot sign in at this time.");
        }

        var access = jwtIssuer.issueAccessToken(
                Audience.ADMIN, admin.id, admin.email, rolesFor(admin));

        return new AdminAuthDtos.AuthResponse(
                access.token(),
                access.expiresAt(),
                rotated.newRefreshToken(),
                rotated.expiresAt(),
                mapper.toDto(admin));
    }

    @Transactional
    public void logout(String presentedRefreshToken) {
        if (presentedRefreshToken != null && !presentedRefreshToken.isBlank()) {
            refreshTokens.revoke(presentedRefreshToken);
        }
    }

    public AdminAuthDtos.AdminUserDto me(long adminId) {
        var admin = admins.findByIdOptional(adminId)
                .orElseThrow(() -> new NotFoundException("AdminUser", adminId));
        return mapper.toDto(admin);
    }

    // -------------------------------------------------------------------------

    private AdminAuthDtos.AuthResponse issueSession(AdminUser admin, String ip, String userAgent) {
        var access = jwtIssuer.issueAccessToken(
                Audience.ADMIN, admin.id, admin.email, rolesFor(admin));
        var refresh = refreshTokens.issueForAdmin(admin, ip, userAgent);

        return new AdminAuthDtos.AuthResponse(
                access.token(),
                access.expiresAt(),
                refresh.presentedToken(),
                refresh.tokenRow().expiresAt,
                mapper.toDto(admin));
    }

    private static String[] rolesFor(AdminUser admin) {
        if (admin.role == null || admin.role.name == null) return new String[] {"ADMIN"};
        return new String[] {"ADMIN", admin.role.name};
    }
}
