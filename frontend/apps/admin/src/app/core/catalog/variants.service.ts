import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AdjustInventoryPayload,
  CreateVariantPayload,
  SetInventoryPayload,
  UpdateVariantPayload,
  VariantDto,
  VariantInventoryDto,
} from './catalog.model';

/**
 * Admin product-variant data access.
 * Corresponds to {@code /api/admin/v1/products/{productId}/variants}.
 */
@Injectable({ providedIn: 'root' })
export class VariantsService {
  private readonly http = inject(HttpClient);

  private base(productId: number): string {
    return `${environment.apiBaseUrl}/products/${productId}/variants`;
  }

  list(productId: number): Observable<VariantDto[]> {
    return this.http.get<VariantDto[]>(this.base(productId));
  }

  create(productId: number, payload: CreateVariantPayload): Observable<VariantDto> {
    return this.http.post<VariantDto>(this.base(productId), payload);
  }

  update(productId: number, variantId: number, payload: UpdateVariantPayload): Observable<VariantDto> {
    return this.http.patch<VariantDto>(`${this.base(productId)}/${variantId}`, payload);
  }

  delete(productId: number, variantId: number): Observable<void> {
    return this.http.delete<void>(`${this.base(productId)}/${variantId}`);
  }

  // -------------------------------------------------------------------------
  // Variant-level inventory
  // -------------------------------------------------------------------------

  listInventory(productId: number): Observable<VariantInventoryDto[]> {
    return this.http.get<VariantInventoryDto[]>(`${this.base(productId)}/inventory`);
  }

  adjustInventory(productId: number, variantId: number, payload: AdjustInventoryPayload): Observable<VariantInventoryDto> {
    return this.http.post<VariantInventoryDto>(
      `${this.base(productId)}/${variantId}/inventory/adjust`,
      payload,
    );
  }

  setInventory(productId: number, variantId: number, payload: SetInventoryPayload): Observable<VariantInventoryDto> {
    return this.http.put<VariantInventoryDto>(
      `${this.base(productId)}/${variantId}/inventory`,
      payload,
    );
  }
}
