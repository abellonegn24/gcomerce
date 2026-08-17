-- =============================================================================
-- Gadaa Istar E-Commerce — Flyway V20: admin session hardening
--
-- Adds admin_refresh_token.session_started_at so we can enforce an absolute
-- cap on an admin session's total lifetime that a rotating refresh token
-- cannot circumvent. Also invalidates every pre-existing admin refresh token
-- so that after deploy every admin is forced to re-authenticate under the
-- new (stricter) session policy — this is intentional: sessions that were
-- issued under the old effectively-unlimited policy should not be honored.
-- =============================================================================

-- 1. Add the new column (nullable at first so we can backfill).
ALTER TABLE admin_refresh_token
    ADD COLUMN session_started_at TIMESTAMP(3) NULL;

-- 2. Backfill existing rows so the NOT NULL constraint below is satisfied.
--    (These rows get revoked in step 3, so the value only needs to be sane.)
UPDATE admin_refresh_token
   SET session_started_at = issued_at
 WHERE session_started_at IS NULL;

-- 3. Revoke every currently-live admin refresh token. Any admin currently
--    signed in will have to log in again (with MFA) on their next request,
--    at which point they'll get a token that respects the new session caps.
UPDATE admin_refresh_token
   SET revoked_at = CURRENT_TIMESTAMP(3)
 WHERE revoked_at IS NULL;

-- 4. Lock the column down.
ALTER TABLE admin_refresh_token
    ALTER COLUMN session_started_at SET NOT NULL;
