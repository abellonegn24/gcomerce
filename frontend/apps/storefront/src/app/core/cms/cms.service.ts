import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CmsBannerListResponse, CmsPageDto } from './cms.model';

/**
 * Storefront CMS service — public read-only access to published pages
 * and active banners.
 */
@Injectable({ providedIn: 'root' })
export class StorefrontCmsService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return `${environment.apiBaseUrl}/cms`;
  }

  getPage(slug: string): Observable<CmsPageDto> {
    return this.http.get<CmsPageDto>(
      `${this.base()}/pages/${encodeURIComponent(slug)}`
    );
  }

  listPages(): Observable<CmsPageDto[]> {
    return this.http.get<CmsPageDto[]>(`${this.base()}/pages`);
  }

  listBanners(placement: string = 'HOMEPAGE_HERO'): Observable<CmsBannerListResponse> {
    return this.http.get<CmsBannerListResponse>(
      `${this.base()}/banners`,
      { params: { placement } }
    );
  }
}
