# INSA Security Audit — Remediation Plan

**Audit date:** 2025-08-25
**Auditor:** INSA (Information Network Security Agency)
**Target system:** gcommerce (Gadaa Istar e-commerce platform)
**Affected hosts:**
- `api.gedaaistar.et` → `backend/api-storefront` (Quarkus, port 8080)
- `admin-api.gedaaistar.et` → `backend/api-admin` (Quarkus, port 8081)
- Frontend SPAs: `frontend/apps/storefront`, `frontend/apps/admin`

**Status:** Approved for implementation. Plan refined 2025-08-25.

---

## Findings Summary

| # | Title | Severity | CVSS | CWE | Host | Endpoint |
|---|-------|----------|------|-----|------|----------|
| 1 | Insufficient Protection of Rendered UI Layers or Frames (clickjacking) | Medium | 6.1 | CWE-1021 | `admin-api.gedaaistar.et` | `/` (all pages) |
| 2 | Improper Restriction of Excessive Authentication Attempts (no brute-force protection) | High | 7.4 | CWE-307 | `api.gedaaistar.et` | `/api/storefront/v1/auth/login` (parameter: `password`) |

---

## Architecture Context (relevant to the fixes)

- Two Quarkus runtime modules: `api-storefront` (port 8080) and `api-admin` (port 8081).
- Both depend on a shared `web-common` module that hosts JAX-RS filters (`CorrelationIdFilter`), the global exception mapper (`GlobalExceptionMapper`), JWT issuance/verification (`JwtIssuer`, `JwtAuthFilter`), and password hashing (`PasswordHasher`).
- Both depend on `common` (exception types: `ApiException`, `UnauthorizedException`, `RateLimitExceededException`, etc.) and `persistence` (Hibernate entities + Flyway migrations).
- Schema is owned by `api-storefront` via Flyway. Latest migration: `V20__admin_session_hardening.sql`. New migration will be `V21`.
- Existing IP-based rate limiting: `@RateLimit` annotation + `RateLimitFilter` + `RateLimitService` (pluggable memory/Redis backend). Currently lives in `backend/api-storefront/.../ratelimit/`. Storefront login already has `@RateLimit(bucket = "auth-login", max = 10, windowSeconds = 60)`. Admin login has **no** `@RateLimit`.
- Existing auth flows:
  - Storefront: `AuthResource.login` → `AuthService.login` (email + password → access + refresh tokens).
  - Admin: two-step — `AdminAuthResource.login` (email + password → MFA challenge JWT) then `AdminAuthResource.verify-mfa` (TOTP → access + refresh tokens). Mandatory TOTP.
- `Customer` and `AdminUser` entities currently have **no** failed-attempt counters or lockout timestamps.
- No `Content-Security-Policy`, `X-Frame-Options`, or any anti-clickjacking header is emitted anywhere in the backend or frontend.

---

## Issue 1 — Clickjacking (CWE-1021, Medium)

### Root Cause
No anti-clickjacking headers are emitted on any HTTP response. Confirmed:
- No `Content-Security-Policy: frame-ancestors` header.
- No `X-Frame-Options` header.
- No `ContainerResponseFilter` that sets security headers exists in `backend/`.
- Angular `index.html` files have no CSP `<meta>` tags.
- INSA successfully framed authenticated admin and non-admin pages inside an attacker-controlled iframe.

Although INSA reported this against `admin-api.gedaaistar.et`, the same gap affects `api-storefront` and both frontend SPAs.

### Auditor's Recommended Solution
> Configure the application to return the `Content-Security-Policy` header with an appropriate `frame-ancestors` directive, such as `frame-ancestors 'self';`, and ensure `X-Frame-Options` is consistently applied on all HTML responses. Sensitive actions should also require additional verification where appropriate.

### Fix

#### 1a. New `SecurityHeadersFilter` in `web-common`
**File:** `backend/web-common/src/main/java/com/gadaaistar/web/SecurityHeadersFilter.java`

A `@Provider` `ContainerResponseFilter` (mirrors the existing `CorrelationIdFilter` pattern) that adds the following headers to **every** response — including error responses produced by `GlobalExceptionMapper`:

| Header | Default value | Purpose |
|---|---|---|
| `Content-Security-Policy` | `frame-ancestors 'self'` | Primary anti-clickjacking control (auditor's ask) |
| `X-Frame-Options` | `DENY` | Legacy defense-in-depth for older browsers; `DENY` chosen because these are JSON APIs never intended to be framed |
| `X-Content-Type-Options` | `nosniff` | MIME-sniffing protection |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Limit referrer leakage |

Configuration (added to both `application.properties` files):
```properties
gadaa.security.headers.enabled=true
gadaa.security.headers.frame-ancestors='self'
gadaa.security.headers.x-frame-options=DENY
```
A master switch (`gadaa.security.headers.enabled`) allows tests to disable the filter if needed.

#### 1b. Frontend `index.html` CSP meta tags (defense-in-depth)
**Files:** `frontend/apps/admin/src/index.html`, `frontend/apps/storefront/src/index.html`

Add `<meta http-equiv="Content-Security-Policy" content="frame-ancestors 'self'">` to both. Note: a `<meta>` CSP cannot reliably express `frame-ancestors` in all browsers, so the backend header (and reverse-proxy header for the SPAs) is the primary control; the meta tag is supplementary.

#### 1c. Reverse-proxy requirement (documented)
The Quarkus filter covers the API hosts (`api.*`, `admin-api.*`). The Angular SPAs are served by a reverse proxy (Nginx/Cloudflare) which must also emit `Content-Security-Policy: frame-ancestors 'self'` and `X-Frame-Options: DENY` on HTML responses. This will be recorded in `AGENTS.md` so it is not missed on deploy.

#### 1d. Test
**File:** `backend/api-storefront/src/test/java/com/gadaaistar/web/SecurityHeadersFilterIT.java`

> **Implementation note:** The plan originally proposed placing this test in `web-common/src/test/`. It was moved to `api-storefront/src/test/` because `web-common` is a plain `java-library` module with no test dependencies (no Quarkus test infrastructure). The `@QuarkusTest` lives in `api-storefront` which has the full test stack.

Verify all four headers are present on 400, 401, and 404 responses (including the error paths from `GlobalExceptionMapper`). **Status: written + passing.** Note: the test uses JAX-RS endpoints (not SmallRye management endpoints like `/q/health` or `/q/openapi`) because the `SecurityHeadersFilter` is a JAX-RS `ContainerResponseFilter` that only runs on JAX-RS responses. Non-JAX-RS endpoints (health, OpenAPI) are covered by the reverse-proxy header config documented in `AGENTS.md`.

### Files touched (Issue 1)
- **New:** `backend/web-common/src/main/java/com/gadaaistar/web/SecurityHeadersFilter.java`
- **New:** `backend/api-storefront/src/test/java/com/gadaaistar/web/SecurityHeadersFilterIT.java` (moved from planned `web-common/src/test/` — `web-common` has no test deps)
- **Edit:** `backend/api-storefront/src/main/resources/application.properties`
- **Edit:** `backend/api-admin/src/main/resources/application.properties`
- **Edit:** `frontend/apps/admin/src/index.html`
- **Edit:** `frontend/apps/storefront/src/index.html`
- **New:** `AGENTS.md` (reverse-proxy note)

---

## Issue 2 — Brute-force / no account lockout (CWE-307, High)

### Root Cause
Three gaps identified:

1. **Storefront login** (`AuthService.login`) has an IP-based rate limit (`@RateLimit(bucket = "auth-login", max = 10, windowSeconds = 60)` on `AuthResource.login`), but **no account-level lockout**. An attacker rotating IPs (botnet, proxies) can keep guessing the same account indefinitely — exactly what INSA reproduced (repeated 401s, then a 200 with the valid password `12345678`).
2. **Admin login** (`AdminAuthResource.login`) has **no `@RateLimit` at all** and **no account lockout**. Worse exposure, though not the endpoint INSA tested.
3. `Customer` and `AdminUser` entities have **no** failed-attempt counters or lockout timestamps.

### Auditor's Recommended Solution
> Enforce a defined failed-login threshold with temporary account lockout or progressive delays after repeated unsuccessful attempts. Implement rate limiting based on appropriate factors such as account, IP address, session, and device characteristics. Return HTTP 429 Too Many Requests when applicable rate limits are exceeded. Failed-attempt counters should be securely maintained server-side, and repeated authentication failures should be logged and monitored.

### Policy (confirmed with product owner)
- **Threshold:** 5 consecutive failed attempts → account locked.
- **Progressive lockout durations:** 1st lockout = 15 min, 2nd = 30 min, 3rd = 60 min, capped at 60 min thereafter. Formula: `min(base * 2^lockoutSequence, max)`.
- **Counter reset:** on successful login (`failedLoginAttempts` → 0, `lockedUntil` → null, `lockoutSequence` → 0).
- **Lockout check runs BEFORE password verify** so a locked account cannot be probed for validity.
- **HTTP status:** `429 Too Many Requests` with `Retry-After` header and error code `ACCOUNT_LOCKED` (consistent with the existing `RateLimitExceededException` handling).
- **User-enumeration defense:** the "email not found" path runs a dummy bcrypt verify (`hasher.verifyDummy()`) to match the timing of the real verification path.
- **Scope:** storefront login, admin login, **and admin verify-mfa** (failed TOTP codes count toward lockout).
- **Per-account, not per-IP:** the existing IP `@RateLimit` stays as a first layer; account lockout is the second layer INSA demanded. Both run together.

### Fix

#### 2a. Flyway migration `V21__login_attempt_tracking.sql`
**File:** `backend/persistence/src/main/resources/db/migration/V21__login_attempt_tracking.sql`

```sql
ALTER TABLE customer
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          TIMESTAMP(3) NULL,
    ADD COLUMN lockout_sequence      INT NOT NULL DEFAULT 0;

ALTER TABLE admin_user
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          TIMESTAMP(3) NULL,
    ADD COLUMN lockout_sequence      INT NOT NULL DEFAULT 0;

CREATE INDEX ix_customer_locked_until ON customer (locked_until) WHERE locked_until IS NOT NULL;
CREATE INDEX ix_admin_locked_until   ON admin_user (locked_until) WHERE locked_until IS NOT NULL;

-- Audit log for monitoring (satisfies the "logged and monitored" requirement)
CREATE TABLE login_attempt_log (
    id              BIGINT NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    account_type    VARCHAR(16)  NOT NULL,   -- CUSTOMER / ADMIN
    email           VARCHAR(255) NOT NULL,
    ip_address      VARCHAR(64)  NULL,
    user_agent      VARCHAR(500) NULL,
    success         BOOLEAN      NOT NULL,
    attempted_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
);
CREATE INDEX ix_login_attempt_email_time ON login_attempt_log (email, attempted_at);
CREATE INDEX ix_login_attempt_ip_time    ON login_attempt_log (ip_address, attempted_at);
```

`lockout_sequence` tracks how many times the account has been locked and drives the progressive doubling.

#### 2b. Entity changes
**Files:** `backend/persistence/.../customer/Customer.java`, `backend/persistence/.../admin/AdminUser.java`

Add to both:
```java
@Column(name = "failed_login_attempts", nullable = false)
public int failedLoginAttempts;

@Column(name = "locked_until")
public Instant lockedUntil;

@Column(name = "lockout_sequence", nullable = false)
public int lockoutSequence;
```

Both entities implement a small shared `LockableAccount` interface exposing the three fields plus `id` and `email`, so `LoginAttemptService` can be generic.

> **Implementation note:** `LockableAccount` was placed in `backend/persistence/.../persistence/LockableAccount.java` (not `web-common` or `common`) because both implementing entities live in `persistence` and the interface needs no dependencies outside the entity layer. This avoids a circular dependency.

#### 2c. New `AccountLockedException`
**File:** `backend/common/src/main/java/com/gadaaistar/common/exceptions/AccountLockedException.java`

Extends `ApiException` (status `429`, code `ACCOUNT_LOCKED`, details include `retryAfterSeconds`). Mirrors `RateLimitExceededException`.

#### 2d. `GlobalExceptionMapper` update
**File:** `backend/web-common/.../GlobalExceptionMapper.java`

Add a branch for `AccountLockedException` that injects the `Retry-After` header, mirroring the existing `RateLimitExceededException` handling.

#### 2e. New `LoginAttemptService` in `persistence`
**File:** `backend/persistence/src/main/java/com/gadaaistar/persistence/auth/LoginAttemptService.java`

> **Implementation note:** The plan originally proposed placing `LoginAttemptService` in `web-common/.../security/`. It was moved to `persistence/.../auth/` because the service depends on `LoginAttemptLogRepository` (a Panache repository) and `LockableAccount` entities, both of which require Hibernate — a dependency that `web-common` (a plain `java-library` module) does not have. `persistence` already has Hibernate.

`@ApplicationScoped` bean encapsulating the lockout policy. Config properties:
```properties
gadaa.security.login.enabled=true
gadaa.security.login.max-failed-attempts=5
gadaa.security.login.base-lockout-seconds=900
gadaa.security.login.max-lockout-seconds=3600
gadaa.security.login.progressive=true
```

Methods (generic over `LockableAccount`):
- `void checkNotLocked(LockableAccount account)` — throws `AccountLockedException` if `lockedUntil` is in the future.
- `void recordFailure(LockableAccount account, String email, String ip, String userAgent, AccountType type)` — increment `failedLoginAttempts`; if threshold reached, compute `duration = min(base * 2^lockoutSequence, max)`, set `lockedUntil = now + duration`, increment `lockoutSequence`, reset `failedLoginAttempts` to 0; persist + write `login_attempt_log` row.
- `void recordSuccess(LockableAccount account, String email, String ip, String userAgent, AccountType type)` — reset `failedLoginAttempts`, `lockedUntil`, `lockoutSequence` to 0/null/0; write `login_attempt_log` row.

Logging:
- WARN on each failure (masked email, account id, IP, attempt count).
- INFO on success-after-lockout ("login after lockout cleared").

#### 2f. Wire into `AuthService.login` (storefront)
**File:** `backend/api-storefront/.../auth/AuthService.java`

```text
login(req, ip, userAgent):
  normalizedEmail = req.email().trim().toLowerCase()
  customer = customers.findByEmail(normalizedEmail)
  if customer empty:
      hasher.verifyDummy()                                  // constant-time anti-enumeration
      throw UnauthorizedException("INVALID_CREDENTIALS", …)
  loginAttempts.checkNotLocked(customer)                   // 429 if locked
  if !hasher.verify(req.password(), customer.passwordHash):
      loginAttempts.recordFailure(customer, …, CUSTOMER)
      throw UnauthorizedException("INVALID_CREDENTIALS", …)
  if !customer.status.canLogin():
      throw UnauthorizedException("ACCOUNT_" + customer.status.name(), …)
  loginAttempts.recordSuccess(customer, …, CUSTOMER)
  … existing success path (status normalization, lastLoginAt, issueSessionForCustomer)
```

Add `verifyDummy()` to `PasswordHasher` — runs one bcrypt verify against a fixed precomputed hash to burn the same time as a real verify.

#### 2g. Wire into `AdminAuthService.login` + `verifyMfa` (admin)
**File:** `backend/api-admin/.../auth/AdminAuthService.java`

Same lockout pattern on both steps:
- `login`: `checkNotLocked` before password verify; `recordFailure` on mismatch; `recordSuccess` is **not** called here (login only issues an MFA challenge, not a full session) — instead the counter is reset only after `verifyMfa` succeeds. Failed password attempts still count toward lockout.
- `verifyMfa`: `checkNotLocked` before TOTP verify; `recordFailure` on bad TOTP code; `recordSuccess` on success.

Add `@RateLimit` to `AdminAuthResource`:
- `login`: `@RateLimit(bucket = "admin-auth-login", max = 10, windowSeconds = 60)`
- `verify-mfa`: `@RateLimit(bucket = "admin-mfa-verify", max = 10, windowSeconds = 60)`

**Implementation note — rate-limit filter location:** The `@RateLimit` infrastructure (`RateLimitFilter`, `RateLimitService`, backends) currently lives in `backend/api-storefront/.../ratelimit/`. For `api-admin` to use `@RateLimit`, this infrastructure must be visible to the admin module. Options:
1. **Move** the `ratelimit` package from `api-storefront` to `web-common` (preferred — single source of truth, both runtimes benefit). Requires updating `api-storefront` imports and confirming the Redis extension dependency is available to `web-common` (or keep the Redis backend in `api-storefront` and only move the filter/service interface).
2. **Duplicate** the filter in `api-admin` (not recommended — drift risk).

Decision: move the rate-limit filter + service + in-memory backend to `web-common`; keep the Redis backend in `api-storefront` (since only `api-storefront` depends on `quarkus-redis-client`) and make it injectable only when present. This will be confirmed during implementation; if moving proves invasive, the fallback is to add `quarkus-redis-client` to `api-admin` and move the whole package.

#### 2h. Admin unlock endpoints (recommended, low cost)
**Files:** `backend/api-admin/.../customer/AdminCustomerResource.java` (or new), `backend/api-admin/.../admin/AdminUserResource.java` (or new)

- `POST /api/admin/v1/customers/{id}/unlock` — clears `lockedUntil`, `failedLoginAttempts`, `lockoutSequence` for a customer.
- `POST /api/admin/v1/admin-users/{id}/unlock` — same for an admin user.

Lets support staff resolve false lockouts (e.g., a customer who genuinely forgot their password and tripped the threshold). Both require admin auth.

#### 2i. Tests
- `LoginAttemptServiceTest` (unit, `api-storefront` test source) — threshold triggers lockout; progressive durations 900/1800/3600/3600; reset-on-success; cap at 60 min; unlock clears fields; audit log rows written. **Status: written + passing.**
- `AuthServiceLockoutTest` (unit, Mockito, `api-storefront` test source) — unknown email → 401 + dummy bcrypt; wrong password → `recordFailure` + 401; locked account → `AccountLockedException` before password verify; correct password → `recordSuccess`; correct password after lockout expiry → `recordSuccess`. **Status: written + passing.**
- `AdminAuthServiceLockoutTest` (unit, Mockito, `api-admin` test source) — same matrix for admin login + verify-mfa (failed TOTP counts toward lockout; locked during MFA → 429 before TOTP verify; correct TOTP → `recordSuccess`). **Status: written + passing.**
- `SecurityHeadersFilterIT` (`@QuarkusTest`, `api-storefront` test source) — verifies all four security headers on 200/404/401 responses. **Status: written; requires `GCOMERCE_test` database to run.**
- Verify `AccountLockedException` flows through `GlobalExceptionMapper` to a 429 with `Retry-After` header — covered by the lockout unit tests (exception is thrown with status 429 + code `ACCOUNT_LOCKED` + `retryAfterSeconds`).

### Files touched (Issue 2)
- **New:** `backend/persistence/src/main/resources/db/migration/V21__login_attempt_tracking.sql`
- **Edit:** `backend/persistence/.../customer/Customer.java`
- **Edit:** `backend/persistence/.../admin/AdminUser.java`
- **New:** `backend/common/.../exceptions/AccountLockedException.java`
- **New:** `backend/persistence/.../auth/LoginAttemptService.java` (moved from planned `web-common` location — needs Hibernate)
- **New:** `backend/persistence/.../persistence/LockableAccount.java` (interface; moved from planned `web-common` location)
- **New:** `backend/persistence/.../auth/LoginAttemptLog.java` (audit log entity)
- **New:** `backend/persistence/.../auth/LoginAttemptLogRepository.java` (audit log repo)
- **Edit:** `backend/web-common/.../GlobalExceptionMapper.java`
- **Edit:** `backend/web-common/.../security/PasswordHasher.java` (`verifyDummy`)
- **Edit:** `backend/api-storefront/.../auth/AuthService.java`
- **Edit:** `backend/api-admin/.../auth/AdminAuthService.java`
- **Edit:** `backend/api-admin/.../auth/AdminAuthResource.java` (`@RateLimit`)
- **Move:** `backend/api-storefront/.../ratelimit/*` → `backend/web-common/.../ratelimit/*` (filter + service + in-memory backend; Redis backend stays in `api-storefront`)
- **Edit:** `backend/api-storefront/src/main/resources/application.properties` (login policy config)
- **Edit:** `backend/api-admin/src/main/resources/application.properties` (login policy config + rate-limit config)
- **New:** admin unlock endpoints — `CustomersResource` + `CustomersService`, `AdminUsersResource` + `AdminUsersService`
- **New tests:** `LoginAttemptServiceTest`, `AuthServiceLockoutTest`, `AdminAuthServiceLockoutTest`, `SecurityHeadersFilterIT`

---

## Build & Verification

1. `./gradlew build` — compile + all existing + new tests.
2. Manual curl checks:
   - `curl -I https://api.gedaaistar.et/health` → confirm `Content-Security-Policy: frame-ancestors 'self'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`.
   - Same against `admin-api.gedaaistar.et`.
   - 5 wrong-password POSTs to `/api/storefront/v1/auth/login` → 6th returns `429` with `Retry-After` header and `ACCOUNT_LOCKED` code.
   - Correct password during lockout → `429`.
   - After lockout expiry, correct password → `200` and counter reset.
3. Confirm `login_attempt_log` rows are written for each attempt.
 
---

## Decisions Recorded

- **429, not 423** (Locked) — consistent with the existing `RateLimitExceededException` handling and INSA's explicit guidance ("Return HTTP 429 Too Many Requests").
- **Progressive lockout** — `min(900 * 2^seq, 3600)` seconds: 900, 1800, 3600, 3600…
- **Per-account lockout + per-IP rate limit together** — two layers; IP limit is the existing first layer, account lockout is the new second layer INSA demanded.
- **No email-on-lockout** — out of scope for this remediation; the `login_attempt_log` table + WARN logs cover the "logged and monitored" requirement. Can be added later if desired.
- **Admin login + verify-mfa hardened too** — INSA only tested storefront, but admin is more sensitive and currently has no rate limit at all. In scope per product owner decision.
- **`X-Frame-Options: DENY`** (not `SAMEORIGIN`) — these are JSON APIs never intended to be framed; `DENY` is stricter and appropriate.
- **Rate-limit infrastructure moved to `web-common`** — so both runtimes can use `@RateLimit` without duplication. Redis backend stays in `api-storefront` (only module with the Redis extension).

---

## Out of Scope (noted for future hardening)

- Email notification on account lockout / unlock.
- CAPTCHA or proof-of-work on repeated failures.
- Device/session-based rate limiting (INSA mentioned "session, and device characteristics" — current implementation covers account + IP).
- Reverse-proxy (Nginx/Cloudflare) header configuration for the SPA hosts — documented as a deploy requirement in `AGENTS.md` but not code-changed here.
- Admin dashboard view over `login_attempt_log` for monitoring/alerting.

---

## Implementation Status (updated 2026-08-25)

### Code — complete

All code changes in this plan are implemented. `./gradlew build -x test` succeeds (all modules compile).

### Tests — written and passing

| Test | Type | Module | Status |
|---|---|---|---|
| `LoginAttemptServiceTest` | Unit (plain JUnit) | `api-storefront` | ✅ Passing — threshold, progressive 900/1800/3600/3600, reset-on-success, unlock, audit log |
| `AuthServiceLockoutTest` | Unit (Mockito) | `api-storefront` | ✅ Passing — unknown email, wrong password, locked-before-verify, correct password, post-expiry success |
| `AdminAuthServiceLockoutTest` | Unit (Mockito) | `api-admin` | ✅ Passing — same matrix for login + verify-mfa (failed TOTP counts) |
| `SecurityHeadersFilterIT` | `@QuarkusTest` | `api-storefront` | ✅ Passing — headers verified on 400/401/404 JAX-RS responses |

### Location deviations from the original plan

| Component | Plan said | Actually placed | Reason |
|---|---|---|---|
| `LoginAttemptService` | `web-common/.../security/` | `persistence/.../auth/` | Needs Hibernate (`LoginAttemptLogRepository`); `web-common` is a plain library |
| `LockableAccount` | `web-common` or `common` | `persistence/` | Both implementing entities live in `persistence`; avoids circular dep |
| `SecurityHeadersFilterTest` | `web-common/src/test/` | `api-storefront/src/test/` (`SecurityHeadersFilterIT`) | `web-common` has no test dependencies |

### Remaining verification steps

1. ~~**Create the test database:** `CREATE DATABASE GCOMERCE_test;` on the PostgreSQL instance, then run `./gradlew test`~~ — **Done.** `./gradlew test` passes (all modules, all tests green).
2. **Manual curl checks** against a running server (local or staging):
   - `curl -I https://api.gedaaistar.et/health` → confirm `Content-Security-Policy: frame-ancestors 'self'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`.
   - Same against `admin-api.gedaaistar.et`.
   - 5 wrong-password POSTs to `/api/storefront/v1/auth/login` → 6th returns `429` with `Retry-After` header and `ACCOUNT_LOCKED` code.
   - Correct password during lockout → `429`.
   - After lockout expiry, correct password → `200` and counter reset.
   - Confirm `login_attempt_log` rows are written for each attempt.
3. **Apply reverse-proxy header config** (Nginx/Cloudflare) for the SPA hosts on deploy — documented in `AGENTS.md`.
