import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CancelOrderPayload,
  OrderDetail,
  PagedOrders,
  PaymentPayload,
  PlaceOrderPayload,
} from './order.model';

/**
 * Wrapper over {@code /me/orders}. No local cache — order state changes on
 * the server without warning (payment webhooks!) so we always re-fetch when
 * a page mounts.
 */
@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);

  list(page = 0, size = 20): Observable<PagedOrders> {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));
    return this.http.get<PagedOrders>(`${environment.apiBaseUrl}/me/orders`, {
      params,
    });
  }

  get(uuid: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(
      `${environment.apiBaseUrl}/me/orders/${uuid}`,
    );
  }

  place(payload: PlaceOrderPayload): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(
      `${environment.apiBaseUrl}/me/orders`,
      payload,
    );
  }

  paymentPayload(uuid: string): Observable<PaymentPayload> {
    return this.http.get<PaymentPayload>(
      `${environment.apiBaseUrl}/me/orders/${uuid}/payment-payload`,
    );
  }

  cancel(uuid: string, payload: CancelOrderPayload = {}): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(
      `${environment.apiBaseUrl}/me/orders/${uuid}/cancel`,
      payload,
    );
  }

  confirmMpgsPayment(uuid: string): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(
      `${environment.apiBaseUrl}/me/orders/${uuid}/confirm-mpgs`,
      {},
    );
  }
}
