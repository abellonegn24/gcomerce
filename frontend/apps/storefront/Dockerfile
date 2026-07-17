# -----------------------------------------------------------------------------
# Storefront (Angular 20 SSR) — Multi-stage Docker build
# Build context must be the frontend/ workspace root.
# -----------------------------------------------------------------------------

FROM node:20-alpine AS build
WORKDIR /workspace

# Install dependencies (workspace root)
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

# Copy sources
COPY tsconfig.json angular.json ./
COPY libs libs
COPY apps/storefront apps/storefront

# Build for production (browser + server bundles)
RUN npx ng build storefront --configuration production

# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime
LABEL org.opencontainers.image.title="gadaa-istar-storefront"
LABEL org.opencontainers.image.source="https://github.com/gadaa-istar/ecommerce"

# Static SPA fallback config; for SSR change to Node runtime.
COPY --from=build /workspace/dist/apps/storefront/browser /usr/share/nginx/html
COPY apps/storefront/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://localhost/ >/dev/null || exit 1
