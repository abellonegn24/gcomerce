/**
 * TS mirrors of the admin CMS DTOs.
 * See @/backend/api-admin/src/main/java/com/gadaaistar/admin/cms/CmsDtos.java
 */

export interface CmsPageDto {
  id: number;
  slug: string;
  title: string;
  bodyHtml: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
  status: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCmsPagePayload {
  slug: string;
  title: string;
  bodyHtml?: string;
  seoTitle?: string;
  seoDescription?: string;
  status?: string;
  sortOrder?: number;
}

export interface UpdateCmsPagePayload {
  slug?: string;
  title?: string;
  bodyHtml?: string;
  seoTitle?: string;
  seoDescription?: string;
  status?: string;
  sortOrder?: number;
  clearBody?: boolean;
  clearSeoTitle?: boolean;
  clearSeoDescription?: boolean;
}

export interface CmsBannerDto {
  id: number;
  placement: string;
  title: string | null;
  imageUrl: string;
  altText: string | null;
  linkUrl: string | null;
  activeFrom: string | null;
  activeTo: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCmsBannerPayload {
  placement: string;
  title?: string;
  imageUrl: string;
  altText?: string;
  linkUrl?: string;
  activeFrom?: string;
  activeTo?: string;
  sortOrder?: number;
  active?: boolean;
}

export interface UpdateCmsBannerPayload {
  placement?: string;
  title?: string;
  imageUrl?: string;
  altText?: string;
  linkUrl?: string;
  activeFrom?: string;
  activeTo?: string;
  sortOrder?: number;
  active?: boolean;
  clearTitle?: boolean;
  clearLinkUrl?: boolean;
  clearSchedule?: boolean;
}
