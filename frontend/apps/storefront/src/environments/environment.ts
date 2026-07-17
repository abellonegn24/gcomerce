/**
 * Production environment configuration.
 *
 * Values here are the "safe" defaults baked into the production bundle.
 * Runtime overrides come from the reverse proxy setting real domains in
 * server-rendered index.html injection (Phase 2).
 */
export const environment = {
  production: true,
  apiBaseUrl: 'https://api.gadaaistar.com/api/storefront/v1',
  storefrontBaseUrl: 'https://www.gadaaistar.com',
  cybersource: {
    // Production Secure Acceptance endpoint. The frontend never touches the
    // access-key or secret — those live on the server. This URL is here so
    // the checkout page can display a "You will be redirected to..." message
    // that references the actual host the browser is about to visit.
    endpoint: 'https://secureacceptance.cybersource.com/pay',
  },
} as const;
