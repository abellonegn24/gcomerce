import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateMediaPayload,
  CreateProductPayload,
  ProductCategoryCode,
  ProductDetail,
  ProductListResponse,
  ProductMedia,
  ProductStatus,
  UpdateProductPayload,
} from './catalog.model';

/**
 * Admin-side catalog data access.
 * Read endpoints allow any admin; write endpoints require SUPERADMIN or
 * PRODUCT_MANAGER (enforced server-side).
 */
@Injectable({ providedIn: 'root' })
export class ProductsService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return `${environment.apiBaseUrl}/products`;
  }

  // -------------------------------------------------------------------------
  // List + get
  // -------------------------------------------------------------------------

  list(opts: {
    q?: string;
    status?: ProductStatus | null;
    category?: ProductCategoryCode | null;
    page?: number;
    size?: number;
  } = {}): Observable<ProductListResponse> {
    let params = new HttpParams();
    if (opts.q)        params = params.set('q', opts.q);
    if (opts.status)   params = params.set('status', opts.status);
    if (opts.category) params = params.set('category', opts.category);
    if (opts.page !== undefined) params = params.set('page', String(opts.page));
    if (opts.size !== undefined) params = params.set('size', String(opts.size));

    return this.http.get<ProductListResponse>(this.base(), { params });
  }

  get(id: number): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`${this.base()}/${id}`);
  }

  // -------------------------------------------------------------------------
  // Create / update / archive
  // -------------------------------------------------------------------------

  create(payload: CreateProductPayload): Observable<ProductDetail> {
    return this.http.post<ProductDetail>(this.base(), payload);
  }

  update(id: number, payload: UpdateProductPayload): Observable<ProductDetail> {
    return this.http.patch<ProductDetail>(`${this.base()}/${id}`, payload);
  }

  archive(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }

  // -------------------------------------------------------------------------
  // Media
  // -------------------------------------------------------------------------

  addMedia(productId: number, payload: CreateMediaPayload): Observable<ProductMedia[]> {
    return this.http.post<ProductMedia[]>(`${this.base()}/${productId}/media`, payload);
  }

  removeMedia(productId: number, mediaId: number): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${productId}/media/${mediaId}`);
  }
}
