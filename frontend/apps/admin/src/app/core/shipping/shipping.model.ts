/**
 * TS mirrors of the admin shipping DTOs.
 * See @/backend/api-admin/src/main/java/com/gadaaistar/admin/shipping/ShippingDtos.java
 *
 * A shipping method is a per-currency rate card: the storefront computes
 *   cost = baseCost + (perKgRate * totalCartWeightKg)
 * for the customer at checkout. Historical orders keep a snapshot of the
 * chosen method's code + name so deleting a method doesn't corrupt
 * invoicing.
 */
export interface ShippingMethodDto {
  id: number;
  code: string;
  name: string;
  description: string | null;
  currency: string;
  baseCost: string | number;
  perKgRate: string | number;
  minDays: number | null;
  maxDays: number | null;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateShippingMethodPayload {
  code: string;
  name: string;
  description?: string;
  currency: string;
  baseCost: string | number;
  perKgRate: string | number;
  minDays?: number;
  maxDays?: number;
  active?: boolean;
  sortOrder?: number;
}

/**
 * Partial update. Any omitted field is left untouched.
 * Use the two clearXxx booleans to explicitly wipe the description or the
 * transit-day range — setting the value to null in the body is ambiguous
 * for a PATCH ("leave alone" vs "clear"?).
 */
export interface UpdateShippingMethodPayload {
  code?: string;
  name?: string;
  description?: string;
  currency?: string;
  baseCost?: string | number;
  perKgRate?: string | number;
  minDays?: number;
  maxDays?: number;
  active?: boolean;
  sortOrder?: number;
  clearDescription?: boolean;
  clearDeliveryDays?: boolean;
}
