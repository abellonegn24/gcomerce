# -----------------------------------------------------------------------------
# Admin (Angular 20 SPA) — Multi-stage Docker build
# Build context must be the frontend/ workspace root.
# -----------------------------------------------------------------------------

FROM node:20-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY tsconfig.json angular.json ./
COPY libs libs
COPY apps/admin apps/admin

RUN npx ng build admin --configuration production

# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime
LABEL org.opencontainers.image.title="gadaa-istar-admin"

COPY --from=build /workspace/dist/apps/admin/browser /usr/share/nginx/html
COPY apps/admin/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://localhost/ >/dev/null || exit 1
