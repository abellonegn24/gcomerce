/**
 * Development environment configuration for the admin app.
 * Selected automatically by angular.json when `ng serve` runs.
 */
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8081/api/admin/v1',
  adminBaseUrl: 'http://localhost:4300',
  brand: 'Gadaa Istar Admin',
} as const;
