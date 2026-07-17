/**
 * Development environment configuration.
 * Selected automatically by angular.json when `ng serve` runs.
 */
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api/storefront/v1',
  storefrontBaseUrl: 'http://localhost:4200',
  cybersource: {
    endpoint: 'https://testsecureacceptance.cybersource.com/pay',
  },
} as const;
