# AGENTS.md — Gadaa Istar gcommerce

## Build & Test

- Backend (Quarkus, Gradle): `cd backend && ./gradlew build` (builds all modules + runs tests).
- Compile only (fast iteration): `cd backend && ./gradlew compileJava`.
- Frontend (Angular, Nx): `cd frontend && npm run build:admin` / `npm run build:storefront`.

## Module Layout

- `backend/common` — shared enums, exception types, DTOs (no Quarkus plugin).
- `backend/persistence` — JPA entities, Panache repos, Flyway migrations. Owns the DB schema.
- `backend/web-common` — shared JAX-RS / CDI infra: JWT, filters, exception mapper, rate-limit, security headers, login-attempt service. Plain library; both Quarkus runtimes index it via `quarkus.index-dependency.web-common.*`.
- `backend/api-storefront` — public + customer-facing REST API (port 8080). Owns Flyway migrations at boot.
- `backend/api-admin` — admin REST API (port 8081). Validates schema only (`hibernate-orm.database.generation=validate`); does not run Flyway.

## Security Headers (INSA CWE-1021)

- `SecurityHeadersFilter` (web-common) emits `Content-Security-Policy: frame-ancestors 'self'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, and `Referrer-Policy: strict-origin-when-cross-origin` on every API response (including error envelopes).
- The Angular SPA `index.html` files have a supplementary CSP `<meta>` tag. **Note:** `frame-ancestors` is NOT honored in `<meta>` tags per the CSP spec — it must be delivered as an HTTP response header. The meta tag is supplementary only.
- **Dev server:** `angular.json` `serve` options include `headers` config that emits the four security headers on dev-server responses (ports 4200/4300). This makes clickjacking protection testable locally.
- **Nginx SPA containers** (`frontend/apps/storefront/nginx.conf`, `frontend/apps/admin/nginx.conf`): emit all security headers including `Content-Security-Policy: frame-ancestors 'self'`. **Critical Nginx gotcha:** `add_header` directives at the `server` level are NOT inherited by `location` blocks that have their own `add_header`. All security headers must be repeated in every `location` block that uses `add_header` (e.g. `location = /index.html`, static asset locations). Without this, SPA routes like `/login` (which internally redirect to `/index.html` via `try_files`) lose their security headers.
- **Reverse proxy** (`infra/nginx/gadaa-istar.conf.example`): emits all security headers at both the `server` and `location` level for the same reason.
- **PoC test file:** `docs/security/clickjacking-poc.html` — open in a browser to verify iframes are blocked. Tests both production and local URLs.

## Login Brute-Force Protection (INSA CWE-307)

- Two layers: per-IP `@RateLimit` (first layer) + per-account lockout via `LoginAttemptService` (second layer).
- Policy: 5 consecutive failures → account locked. Progressive: 15 min → 30 min → 60 min (cap). Reset on success.
- `AccountLockedException` → HTTP 429 + `Retry-After` header.
- Config: `gadaa.security.login.*` in both `application.properties`.
- Admin unlock endpoints: `POST /api/admin/v1/customers/{id}/unlock`, `POST /api/admin/v1/admin-users/{id}/unlock`.
- Audit log: `login_attempt_log` table (every attempt recorded).

## Password Strength Policy (INSA CWE-521)

- `PasswordPolicy` (web-common) enforces server-side password strength on register, change-password, and reset-password endpoints.
- Rules (all configurable): min length 8, max 128, require uppercase + lowercase + digit + special character, block commonly-used passwords.
- Config: `gadaa.security.password.*` in both `application.properties`.
- Applied in: `AuthService.register()`, `AuthService.changePassword()`, `PasswordResetService.complete()`.
- Bean Validation `@Size(min=10)` on DTOs provides a first layer; `PasswordPolicy.validate()` is the authoritative server-side check.

## Rate Limit Infrastructure

- `@RateLimit` annotation + `RateLimitFilter` + `RateLimitService` + `InMemoryRateLimitBackend` live in `web-common` (package `com.gadaaistar.web.ratelimit`) so both runtimes share them.
- `RedisRateLimitBackend` lives in `api-storefront` (only module with `quarkus-redis-client`); discovered generically via `Instance<RateLimitBackend>` + `label()`.
- Config: `gadaa.ratelimit.*` in both `application.properties`.
