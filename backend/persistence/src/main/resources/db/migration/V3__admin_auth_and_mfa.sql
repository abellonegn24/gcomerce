-- =============================================================================
-- Gadaa Istar E-Commerce — Flyway V3: admin refresh tokens + MFA enrollment
--
-- Adds:
--   admin_refresh_token   — rotating refresh tokens for admin sessions
--   admin_user.mfa_enrolled_at  — timestamp of first successful TOTP verify
--
-- MFA is MANDATORY for all admin accounts. When mfa_secret IS NULL the login
-- flow returns a "setup required" challenge with a freshly-generated secret;
-- the admin scans it, enters the first code, and gets an access token.
-- When mfa_secret IS NOT NULL, login returns a "verify required" challenge.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Admin refresh tokens (rotating, hashed at rest — mirrors customer_refresh_token)
-- -----------------------------------------------------------------------------
CREATE TABLE admin_refresh_token (
    id                CHAR(36)       NOT NULL,
    admin_user_id     BIGINT         NOT NULL,
    token_hash        CHAR(64)       NOT NULL,
    issued_at         TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at        TIMESTAMP(3)   NOT NULL,
    revoked_at        TIMESTAMP(3)   NULL,
    ip_address        VARCHAR(64)    NULL,
    user_agent        VARCHAR(500)   NULL,
    replaced_by_id    CHAR(36)       NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_admin_refresh_user FOREIGN KEY (admin_user_id) REFERENCES admin_user(id) ON DELETE CASCADE
);

CREATE INDEX ix_admin_refresh_user   ON admin_refresh_token (admin_user_id);
CREATE INDEX ix_admin_refresh_expiry ON admin_refresh_token (expires_at);

-- -----------------------------------------------------------------------------
-- Track when MFA enrollment completed (distinct from mfa_secret being set,
-- which is set on first login attempt before the admin verifies it).
-- -----------------------------------------------------------------------------
ALTER TABLE admin_user
    ADD COLUMN mfa_enrolled_at TIMESTAMP(3) NULL;

-- =============================================================================
-- Seed a bootstrap super-admin account for dev/staging.
--
-- Credentials (DEV ONLY — replace immediately in staging/prod):
--   email:    admin@gadaaistar.com
--   password: ChangeMe-Admin-2026!
--
-- The password_hash below is a BCrypt cost-12 hash of the password above.
-- MFA is NOT pre-enrolled; the first login will trigger the enrollment flow.
-- =============================================================================
INSERT INTO admin_user (email, password_hash, full_name, role_id, status, mfa_secret)
SELECT
    'admin@gadaaistar.com',
    '$2a$12$L6cnpMH6dNSy/yrW3Lzr9emvPdPFEoghoDxrEfPQHPU6QSVSg7k3C',
    'Platform Super Admin',
    r.id,
    'ACTIVE',
    NULL
FROM role r
WHERE r.name = 'SUPERADMIN'
  AND NOT EXISTS (SELECT 1 FROM admin_user WHERE email = 'admin@gadaaistar.com');
