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
- **Deploy requirement:** the reverse proxy (Nginx / Cloudflare) serving the SPA hosts (`gedaaistar.et`, `admin.gedaaistar.et`) MUST also emit `Content-Security-Policy: frame-ancestors 'self'` and `X-Frame-Options: DENY` on HTML responses. The Quarkus filter only covers the API hosts (`api.*`, `admin-api.*`).
- **PoC test file:** `docs/security/clickjacking-poc.html` — open in a browser to verify iframes are blocked.

## Login Brute-Force Protection (INSA CWE-307)

- Two layers: per-IP `@RateLimit` (first layer) + per-account lockout via `LoginAttemptService` (second layer).
- Policy: 5 consecutive failures → account locked. Progressive: 15 min → 30 min → 60 min (cap). Reset on success.
- `AccountLockedException` → HTTP 429 + `Retry-After` header.
- Config: `gadaa.security.login.*` in both `application.properties`.
- Admin unlock endpoints: `POST /api/admin/v1/customers/{id}/unlock`, `POST /api/admin/v1/admin-users/{id}/unlock`.
- Audit log: `login_attempt_log` table (every attempt recorded).

## Rate Limit Infrastructure

- `@RateLimit` annotation + `RateLimitFilter` + `RateLimitService` + `InMemoryRateLimitBackend` live in `web-common` (package `com.gadaaistar.web.ratelimit`) so both runtimes share them.
- `RedisRateLimitBackend` lives in `api-storefront` (only module with `quarkus-redis-client`); discovered generically via `Instance<RateLimitBackend>` + `label()`.
- Config: `gadaa.ratelimit.*` in both `application.properties`.
