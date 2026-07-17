import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreatePriceListPayload,
  PriceListDetail,
  PriceListDto,
  PriceListEntryDto,
  UpdatePriceListPayload,
  UpsertPriceEntryPayload,
} from './catalog.model';

/**
 * Admin-side price-list data access.
 *
 * A price list is a per-customer-group + currency price sheet holding many
 * {@link PriceListEntryDto} rows keyed by (product, min quantity). The
 * storefront picks the entry with the largest {@code minQuantity} that is
 * still &lt;= the requested cart quantity.
 */
@Injectable({ providedIn: 'root' })
export class PriceListsService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return `${environment.apiBaseUrl}/price-lists`;
  }

  // ---------------------------------------------------------------------------
  // Price list CRUD
  // ---------------------------------------------------------------------------

  list(): Observable<PriceListDto[]> {
    return this.http.get<PriceListDto[]>(this.base());
  }

  get(id: number): Observable<PriceListDetail> {
    return this.http.get<PriceListDetail>(`${this.base()}/${id}`);
  }

  create(payload: CreatePriceListPayload): Observable<PriceListDto> {
    return this.http.post<PriceListDto>(this.base(), payload);
  }

  /**
   * Partial update of an existing price list. The bound customer group is
   * server-enforced-immutable — omit it from the payload.
   */
  update(id: number, payload: UpdatePriceListPayload): Observable<PriceListDto> {
    return this.http.patch<PriceListDto>(`${this.base()}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }

  // ---------------------------------------------------------------------------
  // Entry CRUD (nested under a price list)
  // ---------------------------------------------------------------------------

  upsertEntry(priceListId: number, payload: UpsertPriceEntryPayload): Observable<PriceListEntryDto> {
    return this.http.put<PriceListEntryDto>(`${this.base()}/${priceListId}/entries`, payload);
  }

  deleteEntry(priceListId: number, entryId: number): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${priceListId}/entries/${entryId}`);
  }
}
