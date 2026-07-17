import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AdjustInventoryPayload,
  InventoryDto,
  SetInventoryPayload,
} from './catalog.model';

/**
 * Admin inventory data access.
 * Corresponds to {@code /api/admin/v1/inventory}.
 */
@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return `${environment.apiBaseUrl}/inventory`;
  }

  /** Full-table dump — used by the future inventory dashboard. */
  list(): Observable<InventoryDto[]> {
    return this.http.get<InventoryDto[]>(this.base());
  }

  getForProduct(productId: number): Observable<InventoryDto> {
    return this.http.get<InventoryDto>(`${this.base()}/products/${productId}`);
  }

  /** Signed delta — negative to decrement, positive to add stock. */
  adjust(productId: number, payload: AdjustInventoryPayload): Observable<InventoryDto> {
    return this.http.post<InventoryDto>(`${this.base()}/products/${productId}/adjust`, payload);
  }

  /** Overwrite quantity + optional restock threshold. */
  set(productId: number, payload: SetInventoryPayload): Observable<InventoryDto> {
    return this.http.put<InventoryDto>(`${this.base()}/products/${productId}`, payload);
  }
}
