import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CmsBannerDto,
  CmsPageDto,
  CreateCmsBannerPayload,
  CreateCmsPagePayload,
  UpdateCmsBannerPayload,
  UpdateCmsPagePayload,
} from './cms.model';

/**
 * Admin CMS service — manages static pages and homepage banners.
 */
@Injectable({ providedIn: 'root' })
export class CmsService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return `${environment.apiBaseUrl}/cms`;
  }

  // -- Pages --

  listPages(): Observable<CmsPageDto[]> {
    return this.http.get<CmsPageDto[]>(`${this.base()}/pages`);
  }

  getPage(id: number): Observable<CmsPageDto> {
    return this.http.get<CmsPageDto>(`${this.base()}/pages/${id}`);
  }

  createPage(payload: CreateCmsPagePayload): Observable<CmsPageDto> {
    return this.http.post<CmsPageDto>(`${this.base()}/pages`, payload);
  }

  updatePage(id: number, payload: UpdateCmsPagePayload): Observable<CmsPageDto> {
    return this.http.patch<CmsPageDto>(`${this.base()}/pages/${id}`, payload);
  }

  deletePage(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base()}/pages/${id}`);
  }

  // -- Banners --

  listBanners(): Observable<CmsBannerDto[]> {
    return this.http.get<CmsBannerDto[]>(`${this.base()}/banners`);
  }

  createBanner(payload: CreateCmsBannerPayload): Observable<CmsBannerDto> {
    return this.http.post<CmsBannerDto>(`${this.base()}/banners`, payload);
  }

  updateBanner(id: number, payload: UpdateCmsBannerPayload): Observable<CmsBannerDto> {
    return this.http.patch<CmsBannerDto>(`${this.base()}/banners/${id}`, payload);
  }

  deleteBanner(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base()}/banners/${id}`);
  }
}
