/**
 * Shape of the /api/storefront/v1/cart responses. Kept in one place so the
 * component templates can rely on stable field names as the backend evolves.
 *
 * Note: money values arrive as strings from the backend (Java BigDecimal
 * serialization) — always parse via Number(...) before doing math.
 */

export interface CartLine {
  id: number;
  productId: number;
  variantId: number | null;
  sku: string;
  slug: string;
  name: string;
  variantSku: string | null;
  variantName: string | null;
  primaryImageUrl: string | null;
  currency: string;
  /** Price per unit captured when the line was added (snapshot). */
  unitPrice: string;
  /** Freshly-resolved unit price for this caller — differs from unitPrice if the
   *  price list changed since the line was added. */
  currentUnitPrice: string;
  priceChanged: boolean;
  quantity: string;
  lineTotal: string;
  availableStock: string;
  inStock: boolean;
  addedAt: string;
}

export interface CartResponse {
  id: number | null;
  currency: string;
  items: CartLine[];
  distinctItems: number;
  totalUnits: string;
  subtotal: string;
  createdAt: string | null;
  updatedAt: string | null;
}

// -----------------------------------------------------------------------------
// Request payloads
// -----------------------------------------------------------------------------

export interface AddCartItemPayload {
  productId: number;
  /** Optional variant id — required when the product has variants. */
  variantId?: number;
  /** Server accepts decimals — send integers where MOQ is 1. */
  quantity: number;
}

export interface UpdateCartItemPayload {
  /** Set to 0 to remove the line. */
  quantity: number;
}
