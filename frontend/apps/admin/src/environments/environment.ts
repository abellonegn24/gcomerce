/**
 * Production environment configuration for the admin app.
 *
 * Baked into the prod bundle. Dev builds swap this file for
 * environment.development.ts via angular.json fileReplacements.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'https://api.gadaaistar.com/api/admin/v1',
  adminBaseUrl: 'https://admin.gadaaistar.com',
  brand: 'Gadaa Istar Admin',
} as const;
