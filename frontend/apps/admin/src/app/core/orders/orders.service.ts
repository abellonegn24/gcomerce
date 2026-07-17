import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  OrderDetail,
  OrderStatus,
  PagedOrders,
  TransitionOrderPayload,
} from './order.model';

/**
 * Admin-side order data access.
 * Corresponds to {@code /api/admin/v1/orders}.
 *
 * We keep no local cache — order status changes via customer payment
 * events and other admins, so pages re-fetch on mount.
 */
@Injectable({ providedIn: 'root' })
export class OrdersService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return `${environment.apiBaseUrl}/orders`;
  }

  list(opts: {
    status?: OrderStatus | null;
    q?: string;
    page?: number;
    size?: number;
  } = {}): Observable<PagedOrders> {
    let params = new HttpParams();
    if (opts.status) params = params.set('status', opts.status);
    if (opts.q)      params = params.set('q', opts.q);
    if (opts.page !== undefined) params = params.set('page', String(opts.page));
    if (opts.size !== undefined) params = params.set('size', String(opts.size));
    return this.http.get<PagedOrders>(this.base(), { params });
  }

  get(uuid: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${this.base()}/${uuid}`);
  }

  transition(uuid: string, payload: TransitionOrderPayload): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base()}/${uuid}/transition`, payload);
  }
}
