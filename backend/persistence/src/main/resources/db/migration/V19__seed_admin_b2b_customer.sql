-- =============================================================================
-- Gadaa Istar E-Commerce — Flyway V19: seed B2B admin viewer customer
--
-- Inserts a B2B customer with a pre-hashed BCrypt password (cost 12).
-- When this customer logs in, the storefront /account/transactions page
-- shows orders placed by ALL customers (enforced server-side by
-- OrderService.isTransactionViewer which matches on the configured
-- gadaa.transactions.admin-viewer-email).
--
-- Credentials:
--   Email:    admin@gplus.com
--   Password: Admin@123456
--
-- The BCrypt hash was generated with bcryptjs (Mou crypto, $2b$ prefix,
-- 12 rounds) — compatible with the Java PasswordHasher which uses
-- at.favre.lib:bcrypt.
-- =============================================================================

INSERT INTO customer (
    email,
    password_hash,
    customer_type,
    status,
    first_name,
    last_name,
    company_name,
    preferred_currency,
    preferred_locale,
    group_id,
    email_verified_at,
    created_at,
    updated_at,
    version
) VALUES (
    'admin@gplus.com',
    '$2b$12$5.f5MLx4iVDP1K7mPmEh7uo/g8msYmlT67EW6l9h0eapMRXNrQxqi',
    'B2B',
    'ACTIVE',
    'admin',
    'admin',
    'Geda Istar',
    'USD',
    'en-US',
    (SELECT id FROM customer_group WHERE code = 'WHOLESALE_T1'),
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3),
    0
)
ON CONFLICT (email) DO UPDATE
    -- Idempotent: if the row already exists (e.g. re-running migrations on a
    -- dev DB that was partially seeded), keep the existing password hash so we
    -- don't clobber a password the user may have already changed.
    SET updated_at = EXCLUDED.updated_at;
