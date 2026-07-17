/**
 * TS mirrors of the storefront catalog DTOs. See:
 * @/backend/api-storefront/src/main/java/com/gadaaistar/storefront/catalog/StorefrontCatalogDtos.java
 */

export type ProductCategorySlug =
  | 'raw-coffee'
  | 'processed-coffee'
  | 'white-products'
  | 'spicy-products';

/** Enum value stored in the DB; the API returns this on read models. */
export type ProductCategoryCode =
  | 'RAW_COFFEE'
  | 'PROCESSED_COFFEE'
  | 'WHITE_PRODUCTS'
  | 'SPICY_PRODUCTS';

export interface ProductCard {
  id: number;
  sku: string;
  slug: string;
  name: string;
  shortDescription: string | null;
  category: ProductCategoryCode;
  currency: string;
  basePrice: string;         // BigDecimal → string (avoid float rounding)
  displayPrice: string;
  discountPercent: string;   // 0 for retail, positive for B2B tiers
  minOrderQuantity: string;
  inStock: boolean;
  primaryImageUrl: string | null;
  cardThumbnailUrl: string | null;
}

export interface TieredPrice {
  minQuantity: string;
  unitPrice: string;
}

export interface VariantAttribute {
  key: string;
  value: string;
}

export interface VariantSummary {
  id: number;
  sku: string;
  name: string;
  priceOverride: string | null;
  weightKgOverride: string | null;
  active: boolean;
  sortOrder: number;
  attributes: VariantAttribute[];
  inStock: boolean;
  availableQuantity: string;
}

export interface ProductDetail {
  id: number;
  sku: string;
  slug: string;
  name: string;
  shortDescription: string | null;
  description: string | null;
  category: ProductCategoryCode;
  currency: string;
  basePrice: string;
  displayPrice: string;
  discountPercent: string;
  tiers: TieredPrice[];
  weightKg: string | null;
  originCountry: string;
  hsCode: string | null;
  minOrderQuantity: string;
  packagingNotes: string | null;
  mediaUrls: string[];
  primaryImageUrl: string | null;
  detailThumbnailUrl: string | null;
  inStock: boolean;
  availableQuantity: string;
  variants: VariantSummary[];
}

export interface PagedMeta {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

export interface PagedResponse<T> {
  data: T[];
  meta: PagedMeta;
}

export interface CatalogListResponse {
  page: PagedResponse<ProductCard>;
}

// -----------------------------------------------------------------------------
// Category metadata (slug ↔ display) — kept in sync with backend enum.
// -----------------------------------------------------------------------------
export interface CategoryMeta {
  slug: ProductCategorySlug;
  code: ProductCategoryCode;
  label: string;
  blurb: string;
}

export const CATEGORIES: readonly CategoryMeta[] = [
  {
    slug: 'raw-coffee',
    code: 'RAW_COFFEE',
    label: 'Raw Coffee',
    blurb: 'Single-origin green beans from Yirgacheffe, Sidamo, and beyond.',
  },
  {
    slug: 'processed-coffee',
    code: 'PROCESSED_COFFEE',
    label: 'Processed Coffee',
    blurb: 'Small-batch roasts, whole bean and ground, ready to brew.',
  },
  {
    slug: 'white-products',
    code: 'WHITE_PRODUCTS',
    label: 'White Products',
    blurb: 'Teff, honey, and heritage grains milled from Ethiopian farms.',
  },
  {
    slug: 'spicy-products',
    code: 'SPICY_PRODUCTS',
    label: 'Spicy Products',
    blurb: 'Berbere, mitmita, and traditional spice blends from the highlands.',
  },
] as const;

export function categoryBySlug(slug: string | null | undefined): CategoryMeta | null {
  if (!slug) return null;
  return CATEGORIES.find((c) => c.slug === slug) ?? null;
}

export function categoryByCode(code: ProductCategoryCode): CategoryMeta | null {
  return CATEGORIES.find((c) => c.code === code) ?? null;
}
