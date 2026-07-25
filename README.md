# Gadaa Istar Export E-Commerce Platform

Hybrid **B2B + B2C** e-commerce platform for exporting Ethiopian coffee (raw & processed) and specialty agricultural products (white products, spicy products) to international buyers, integrated with **CyberSource Secure Acceptance Hosted Checkout**.

**Stack**

- **Frontend:** Angular 20 (standalone components, signals, SSR)
- **Backend:** Java 21 + Quarkus 3.x (multi-module Gradle)
- **Database:** PostgreSQL 16 with Flyway migrations
- **Payment:** CyberSource Secure Acceptance Hosted Checkout
- **Hosting:** Ethio Telecom Cloud + Cloudflare edge
- **Compliance:** PCI DSS SAQ-A, Ethiopian VAT (ERCA)

See the full plan in `../ai-progress/gadaa-istar-ecommerce-plan.md`.

---

## Repository Layout

```
gadaa-istar-ecom/
  backend/                      # Java 21 + Quarkus multi-module Gradle project
    settings.gradle
    build.gradle                # root build config, shared dependencies
    common/                     # shared DTOs, enums, exceptions
    persistence/                # JPA entities, Panache repos, Flyway migrations
    api-storefront/             # public + customer REST API (Quarkus runtime here)
    # Future modules: catalog, customer, order, payment, shipping,
    # notification, api-admin, workers
  frontend/
    package.json                # Angular workspace root
    angular.json
    apps/
      storefront/               # public storefront + customer portal (Angular 20 SSR)
      admin/                    # admin back-office (Angular 20 SPA)
    libs/
      ui-kit/                   # shared brand theme, tokens, common components
  infra/
    docker-compose.yml          # local dev stack (PostgreSQL, MinIO, MailHog, apps)
    nginx/                      # reverse proxy configs
  docs/                         # developer guides
```

---

## Quick Start (Local Development)

### Prerequisites

- **JDK 21** (Temurin recommended)
- **Node.js 20 LTS** and **npm 10+**
- **Docker Desktop** (or Docker Engine + Compose)
- **Git**

### 1. Start infrastructure services

```powershell
cd infra
docker compose up -d postgres minio mailhog
```

- **PostgreSQL** on `localhost:5432` (db: `GCOMERCE`, user: `gadaa`, pw: `gadaa_dev_pw`)
- **MinIO** console: <http://localhost:9001> (user: `minio`, pw: `minio_dev_pw`)
- **MailHog** UI: <http://localhost:8025>

### 2. Run the backend

```powershell
cd backend
./gradlew :api-storefront:quarkusDev
```

- API base URL: <http://localhost:8080/api/storefront/v1>
- Health check: <http://localhost:8080/q/health>
- Swagger UI: <http://localhost:8080/q/swagger-ui>
- Flyway migrations run automatically on startup.

Verify with:

```powershell
curl http://localhost:8080/api/storefront/v1/system/hello
```

### 3. Run the frontends

Install dependencies once at the workspace root:

```powershell
cd frontend
npm install
```

Then, in two separate terminals:

```powershell
# Storefront on https://gedaaistar.et
npm run start:storefront

# Admin on https://admin.gedaaistar.et
npm run start:admin
```

Both apps use the shared `@gadaa/ui-kit` library and the Gadaa Istar brand theme.

---

## Environment Variables

Copy `.env.example` to `.env` and adjust values as needed. Never commit `.env`.

Sensitive configuration (CyberSource keys, SMTP credentials, JWT secrets) is loaded via environment variables in dev and via **HashiCorp Vault** in staging/production.

---

## Brand Identity

Colors are derived directly from the Gadaa Istar logo (`../ai-progress/logo.png`):

| Token       | Hex       | Usage                           |
| ----------- | --------- | ------------------------------- |
| Navy 900    | `#12294A` | Headers, primary text           |
| Navy 700    | `#1B3A6B` | Primary brand, buttons, links   |
| Red 600     | `#C93327` | CTA buttons, price highlights   |
| Gold 500    | `#E9B44C` | Star/accent, "Featured" markers |
| Cream 50    | `#FAF6F0` | Page background                 |
| Espresso 900| `#3B2A20` | Coffee-themed dark accent       |

Full palette in `frontend/libs/ui-kit/src/styles/_tokens.scss`.

---

## Contributing

1. Create a feature branch from `main`: `git checkout -b feature/GI-<ticket>-<slug>`
2. Ensure `./gradlew check` and `npm run lint && npm run test` pass locally
3. Open a PR; CI must be green before merge

---

## License

Proprietary — © Gadaa Istar Business Group S.C.
