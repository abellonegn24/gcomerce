/**
 * TS mirrors of the storefront CMS DTOs.
 * See @/backend/api-storefront/src/main/java/com/gadaaistar/storefront/cms/StorefrontCmsDtos.java
 */

export interface CmsPageDto {
  slug: string;
  title: string;
  bodyHtml: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
}

export interface CmsBannerDto {
  id: number;
  title: string | null;
  imageUrl: string;
  altText: string | null;
  linkUrl: string | null;
}

export interface CmsBannerListResponse {
  placement: string;
  banners: CmsBannerDto[];
}
