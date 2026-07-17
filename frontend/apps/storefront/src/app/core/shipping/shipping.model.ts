/**
 * TS mirrors of the storefront shipping-quote DTOs.
 * See @/backend/api-storefront/src/main/java/com/gadaaistar/storefront/shipping/ShippingDtos.java
 *
 * The customer never posts to the shipping API — they just fetch quotes
 * pre-computed against their own cart. The chosen method's id is then
 * passed to the place-order call, which re-derives the cost server-side.
 */
export interface ShippingQuote {
  id: number;
  code: string;
  name: string;
  description: string | null;
  currency: string;
  /** Already computed for the caller's current cart (weight-aware). */
  cost: string | number;
  minDays: number | null;
  maxDays: number | null;
  sortOrder: number;
}

export interface ShippingQuoteListResponse {
  currency: string;
  /** Cart's total weight in kilograms, so the UI can show "for 3.20 kg". */
  totalWeightKg: string | number;
  quotes: ShippingQuote[];
}
