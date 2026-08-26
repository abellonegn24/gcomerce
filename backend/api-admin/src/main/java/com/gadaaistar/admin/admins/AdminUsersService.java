package com.gadaaistar.admin.admins;

import java.util.Comparator;

import com.gadaaistar.admin.security.AdminRefreshTokenService;
import com.gadaaistar.common.exceptions.BadRequestException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.admin.AdminUser;
import com.gadaaistar.persistence.admin.AdminUserRepository;
import com.gadaaistar.persistence.auth.LoginAttemptService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Read + narrow-write operations against the {@code admin_user} table.
 *
 * <p>Right now only two flows are exposed:
 * <ol>
 *   <li>{@link #list()} — full roster (auth already limits access to admins).</li>
 *   <li>{@link #resetMfa(long, long)} — SUPERADMIN wipes another admin's TOTP
 *       secret + revokes all their refresh tokens, forcing re-enrollment on
 *       the next sign-in.</li>
 * </ol>
 */
@ApplicationScoped
public class AdminUsersService {

    private static final Logger LOG = Logger.getLogger(AdminUsersService.class);

    @Inject AdminUserRepository        admins;
    @Inject AdminRefreshTokenService   refreshTokens;
    @Inject LoginAttemptService        loginAttempts;

    // -------------------------------------------------------------------------

    public AdminUsersDtos.ListResponse list() {
        var items = admins.streamAll()
                .sorted(Comparator.comparing((AdminUser a) -> a.email))
                .map(a -> new AdminUsersDtos.AdminUserSummary(
                        a.id,
                        a.email,
                        a.fullName,
                        a.role == null ? null : a.role.name,
                        a.status,
                        a.isMfaEnrolled(),
                        a.lastLoginAt,
                        a.createdAt
                ))
                .toList();
        return new AdminUsersDtos.ListResponse(items);
    }

    /**
     * Force target admin to re-enroll MFA on their next sign-in AND kick every
     * one of their live refresh tokens.
     *
     * @param targetId the admin whose MFA should be wiped
     * @param actorId  the SUPERADMIN performing the reset (for the audit log)
     */
    @Transactional
    public AdminUsersDtos.ResetMfaResponse resetMfa(long targetId, long actorId) {
        if (targetId == actorId) {
            // Prevent locking yourself out: use the setup flow instead.
            throw new BadRequestException("SELF_RESET_FORBIDDEN",
                    "You cannot reset your own MFA — use the enrollment flow after signing out.");
        }
        var target = admins.findByIdOptional(targetId)
                .orElseThrow(() -> new NotFoundException("AdminUser", targetId));

        target.mfaSecret     = null;
        target.mfaEnrolledAt = null;
        refreshTokens.revokeAllForAdmin(target.id);

        LOG.infof("MFA reset for admin id=%d (email=%s) by super-admin id=%d",
                target.id, target.email, actorId);

        return new AdminUsersDtos.ResetMfaResponse(
                target.id,
                target.email,
                "MFA_RESET",
                "MFA secret wiped and all sessions revoked. The admin will re-enroll on next sign-in."
        );
    }

    /**
     * Manually clear a brute-force lockout on an admin account (INSA CWE-307).
     * SUPERADMIN-gated, same as {@link #resetMfa}.
     */
    @Transactional
    public AdminUsersDtos.ResetMfaResponse unlock(long targetId, long actorId) {
        var target = admins.findByIdOptional(targetId)
                .orElseThrow(() -> new NotFoundException("AdminUser", targetId));
        loginAttempts.unlock(target);
        LOG.infof("Admin id=%d unlocked by super-admin id=%d", target.id, actorId);
        return new AdminUsersDtos.ResetMfaResponse(
                target.id,
                target.email,
                "UNLOCKED",
                "Account lockout cleared. The admin can sign in again."
        );
    }
}
