/*
 * Brand color constants exposed to TypeScript.
 * These mirror the values in `styles/_tokens.scss`. Prefer the SCSS
 * custom properties (`var(--gi-navy-700)`) in templates; use these
 * constants in .ts code (e.g., for chart theming).
 */

export const BRAND_COLORS = Object.freeze({
  navy900: '#12294A',
  navy700: '#1B3A6B',
  navy500: '#2C5391',
  navy100: '#E4EBF5',

  red600: '#C93327',
  red500: '#D63A2F',
  red100: '#FBE4E1',

  gold500: '#E9B44C',
  gold300: '#F4CE7A',

  cream50: '#FAF6F0',
  espresso900: '#3B2A20',

  textPrimary: '#0F172A',
  textSecondary: '#64748B',

  success: '#16A34A',
  warning: '#F59E0B',
  error: '#DC2626',
} as const);

export type GadaaBrand = typeof BRAND_COLORS;
