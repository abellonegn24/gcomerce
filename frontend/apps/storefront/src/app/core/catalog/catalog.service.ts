import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CatalogListResponse,
  ProductDetail,
  ProductCategorySlug,
} from './catalog.model';

/**
 * Thin HTTP wrapper around /api/storefront/v1/catalog.
 * Backend resolves the caller's effective price based on the JWT (if any).
 */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);

  list(opts: {
    category?: ProductCategorySlug | null;
    page?: number;
    size?: number;
  } = {}): Observable<CatalogListResponse> {
    let params = new HttpParams();
    if (opts.category) params = params.set('category', opts.category);
    if (opts.page !== undefined) params = params.set('page', String(opts.page));
    if (opts.size !== undefined) params = params.set('size', String(opts.size));

    return this.http.get<CatalogListResponse>(
      `${environment.apiBaseUrl}/catalog/products`,
      { params }
    );
  }

  search(opts: {
    q: string;
    category?: ProductCategorySlug | null;
    page?: number;
    size?: number;
  }): Observable<CatalogListResponse> {
    let params = new HttpParams().set('q', opts.q);
    if (opts.category) params = params.set('category', opts.category);
    if (opts.page !== undefined) params = params.set('page', String(opts.page));
    if (opts.size !== undefined) params = params.set('size', String(opts.size));

    return this.http.get<CatalogListResponse>(
      `${environment.apiBaseUrl}/catalog/products/search`,
      { params }
    );
  }

  getBySlug(slug: string): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(
      `${environment.apiBaseUrl}/catalog/products/${encodeURIComponent(slug)}`
    );
  }
}
