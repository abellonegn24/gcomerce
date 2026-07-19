


/**
 * TS mirrors of the admin catalog DTOs.
 * See @/backend/api-admin/src/main/java/com/gadaaistar/admin/catalog/CatalogDtos.java
 */

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
export type ProductCategoryCode =
  | 'RAW_COFFEE'
  | 'ROASTED_COFFEE'
  | 'WHITE_PRODUCTS'
  | 'SPICY_PRODUCTS'
  | 'OIL_SEED'
  | 'LIVESTOCK'
  | 'MINING';

export type PriceTier = 'RETAIL' | 'WHOLESALE_T1' | 'WHOLESALE_T2' | 'VIP';
export type MediaType = 'IMAGE' | 'VIDEO';

// -----------------------------------------------------------------------------
// Products
// -----------------------------------------------------------------------------

export interface ProductListItem {
  id: number;
  sku: string;
  slug: string;
  name: string;
  category: ProductCategoryCode;
  status: ProductStatus;
  baseCurrency: string;
  basePrice: string;
  available: string;
  primaryImageUrl: string | null;
  updatedAt: string;
}

export interface ProductMedia {
  id: number;
  url: string;
  altText: string | null;
  mediaType: MediaType;
  isPrimary: boolean;
  sortOrder: number;
  cardThumbnailUrl: string | null;
  detailThumbnailUrl: string | null;
}

export interface InventorySummary {
  quantity: string;
  reserved: string;
  available: string;
  restockThreshold: string;
  low: boolean;
}

// -----------------------------------------------------------------------------
// Product variants
// -----------------------------------------------------------------------------

export interface VariantAttribute {
  key: string;
  value: string;
}

export interface VariantDto {
  id: number;
  sku: string;
  name: string;
  priceOverride: string | null;
  weightKgOverride: string | null;
  active: boolean;
  sortOrder: number;
  attributes: VariantAttribute[];
  availableQuantity: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface VariantInventoryDto {
  variantId: number;
  variantSku: string;
  variantName: string;
  quantity: string;
  reserved: string;
  available: string;
  restockThreshold: string | null;
  low: boolean;
  updatedAt: string;
}

export interface CreateVariantPayload {
  sku: string;
  name: string;
  priceOverride?: string | number;
  weightKgOverride?: string | number;
  active?: boolean;
  sortOrder?: number;
  attributes?: VariantAttribute[];
}

export interface UpdateVariantPayload {
  sku?: string;
  name?: string;
  priceOverride?: string | number | null;
  weightKgOverride?: string | number | null;
  active?: boolean;
  sortOrder?: number;
  attributes?: VariantAttribute[];
}

export interface ProductDetail {
  id: number;
  sku: string;
  slug: string;
  name: string;
  shortDescription: string | null;
  description: string | null;
  category: ProductCategoryCode;
  status: ProductStatus;
  weightKg: string | null;
  hsCode: string | null;
  originCountry: string | null;
  taxClassId: number | null;
  baseCurrency: string;
  basePrice: string;
  costPrice: string | null;
  minOrderQuantity: string;
  packagingNotes: string | null;
  media: ProductMedia[];
  variants: VariantDto[];
  inventory: InventorySummary;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProductPayload {
  sku: string;
  slug: string;
  name: string;
  shortDescription?: string;
  description?: string;
  category: ProductCategoryCode;
  status: ProductStatus;
  weightKg?: string | number;
  hsCode?: string;
  originCountry?: string;
  taxClassId?: number | null;
  baseCurrency: string;
  basePrice: string | number;
  costPrice?: string | number;
  minOrderQuantity?: string | number;
  packagingNotes?: string;
}

export type UpdateProductPayload = Partial<Omit<CreateProductPayload, 'sku'>>;

export interface CreateMediaPayload {
  url: string;
  altText?: string;
  mediaType?: MediaType;
  isPrimary?: boolean;
  sortOrder?: number;
  cardThumbnailUrl?: string | null;
  detailThumbnailUrl?: string | null;
}

// -----------------------------------------------------------------------------
// Price lists
// -----------------------------------------------------------------------------

export interface PriceListDto {
  id: number;
  customerGroupId: number;
  customerGroupCode: string;
  name: string;
  currency: string;
  active: boolean;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  entryCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface PriceListEntryDto {
  id: number;
  productId: number;
  productSku: string;
  productName: string;
  price: string;
  minQuantity: string;
}

export interface PriceListDetail {
  priceList: PriceListDto;
  entries: PriceListEntryDto[];
}

export interface CreatePriceListPayload {
  customerGroupId: number;
  name: string;
  currency: string;
  active?: boolean;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
}

/**
 * Partial update. Any omitted field is left untouched.
 * The customer group is intentionally NOT editable — reassigning a list to a
 * different group would silently move every tier and is almost always a bug.
 * Set {@code clearEffectiveFrom / clearEffectiveTo} to explicitly wipe those
 * dates back to null (setting the date field itself to null is treated as
 * "leave as-is" for PATCH semantics).
 */
export interface UpdatePriceListPayload {
  name?: string;
  currency?: string;
  active?: boolean;
  effectiveFrom?: string;
  effectiveTo?: string;
  clearEffectiveFrom?: boolean;
  clearEffectiveTo?: boolean;
}

export interface UpsertPriceEntryPayload {
  productId: number;
  price: string | number;
  minQuantity: string | number;
}

// -----------------------------------------------------------------------------
// Inventory
// -----------------------------------------------------------------------------

export interface InventoryDto {
  productId: number;
  productSku: string;
  productName: string;
  quantity: string;
  reserved: string;
  available: string;
  restockThreshold: string;
  low: boolean;
  updatedAt: string;
}

export interface AdjustInventoryPayload {
  /** Signed delta — negative to decrement. */
  delta: string | number;
  reason?: string;
}

export interface SetInventoryPayload {
  quantity: string | number;
  restockThreshold?: string | number;
}

// -----------------------------------------------------------------------------
// Customer groups
// -----------------------------------------------------------------------------

export interface CustomerGroupDto {
  id: number;
  code: string;
  name: string;
  description: string | null;
  priceTier: PriceTier;
  /** BigDecimal serialized as string — avoid float rounding on the wire. */
  moqMultiplier: string;
  active: boolean;
  /** Number of customers currently in this group. Server-computed on list. */
  customerCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCustomerGroupPayload {
  code: string;
  name: string;
  description?: string;
  priceTier: PriceTier;
  moqMultiplier?: string;
  active?: boolean;
}

export interface UpdateCustomerGroupPayload {
  code?: string;
  name?: string;
  description?: string;
  priceTier?: PriceTier;
  moqMultiplier?: string;
  active?: boolean;
  /** Explicit flag to wipe description back to null. */
  clearDescription?: boolean;
}

export const PRICE_TIER_LABELS: Record<PriceTier, string> = {
  RETAIL:        'Retail',
  WHOLESALE_T1:  'Wholesale — Tier 1',
  WHOLESALE_T2:  'Wholesale — Tier 2',
  VIP:           'VIP',
};

export const PRICE_TIER_OPTIONS: Array<{ code: PriceTier; label: string }> = [
  { code: 'RETAIL',       label: 'Retail' },
  { code: 'WHOLESALE_T1', label: 'Wholesale — Tier 1' },
  { code: 'WHOLESALE_T2', label: 'Wholesale — Tier 2' },
  { code: 'VIP',          label: 'VIP' },
];

// -----------------------------------------------------------------------------
// Shared paged envelope
// -----------------------------------------------------------------------------

export interface PagedResponse<T> {
  data: T[];
  meta: {
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
  };
}

export interface ProductListResponse {
  page: PagedResponse<ProductListItem>;
}

export const CATEGORY_LABELS: Record<ProductCategoryCode, string> = {
  RAW_COFFEE:        'Raw Coffee',
  ROASTED_COFFEE:    'Roasted Coffee',
  WHITE_PRODUCTS:    'White Products',
  SPICY_PRODUCTS:    'Spicy Products',
  OIL_SEED:          'Oil Seed',
  LIVESTOCK:         'Livestock',
  MINING:            'Mining',
};

export const CATEGORY_OPTIONS: Array<{ code: ProductCategoryCode; label: string }> = [
  { code: 'RAW_COFFEE',     label: 'Raw Coffee' },
  { code: 'ROASTED_COFFEE', label: 'Roasted Coffee' },
  { code: 'WHITE_PRODUCTS', label: 'White Products' },
  { code: 'SPICY_PRODUCTS', label: 'Spicy Products' },
  { code: 'OIL_SEED',       label: 'Oil Seed' },
  { code: 'LIVESTOCK',      label: 'Livestock' },
  { code: 'MINING',         label: 'Mining' },
];

export const STATUS_OPTIONS: Array<{ code: ProductStatus; label: string }> = [
  { code: 'DRAFT',    label: 'Draft' },
  { code: 'ACTIVE',   label: 'Active' },
  { code: 'ARCHIVED', label: 'Archived' },
];

// -----------------------------------------------------------------------------
// Uploads (direct file → object storage via /api/admin/v1/uploads)
// -----------------------------------------------------------------------------

/**
 * Server response after a successful direct file upload. The caller feeds
 * {@code url} straight into {@code POST /products/{id}/media} to attach the
 * new file to a product.
 */
export interface UploadResponse {
  url: string;
  objectKey: string;
  contentType: string;
  sizeBytes: number;
  cardThumbnailUrl: string | null;
  detailThumbnailUrl: string | null;
}

/** MIME types the admin upload endpoint will accept. Mirrors the backend allowlist. */
export const UPLOAD_ACCEPTED_TYPES: readonly string[] = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
  'video/mp4',
  'video/quicktime',
  'video/webm',
];

/** `accept` attribute string for {@code <input type="file">}. */
export const UPLOAD_ACCEPT_ATTR = UPLOAD_ACCEPTED_TYPES.join(',');

// -----------------------------------------------------------------------------
// Coupons
// -----------------------------------------------------------------------------

export type CouponDiscountType = 'PERCENT' | 'FIXED_AMOUNT';

export interface CouponDto {
  id: number;
  code: string;
  description: string | null;
  discountType: CouponDiscountType;
  discountValue: string;
  currency: string;
  minSubtotal: string | null;
  freeShipping: boolean;
  maxRedemptions: number | null;
  maxPerCustomer: number | null;
  redeemedCount: number;
  active: boolean;
  startsAt: string | null;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CouponListResponse {
  items: CouponDto[];
  total: number;
  page: number;
  size: number;
}

export interface CreateCouponPayload {
  code: string;
  description?: string;
  discountType: CouponDiscountType;
  discountValue: string;
  currency: string;
  minSubtotal?: string;
  freeShipping?: boolean;
  maxRedemptions?: number;
  maxPerCustomer?: number;
  active?: boolean;
  startsAt?: string;
  expiresAt?: string;
}

export interface UpdateCouponPayload {
  code?: string;
  description?: string;
  discountType?: CouponDiscountType;
  discountValue?: string;
  currency?: string;
  minSubtotal?: string;
  freeShipping?: boolean;
  maxRedemptions?: number;
  maxPerCustomer?: number;
  active?: boolean;
  startsAt?: string;
  expiresAt?: string;
}
