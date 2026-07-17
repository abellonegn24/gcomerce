import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CreateRefundPayload, RefundEntry } from './refund.model';

/**
 * Admin refund API. Refunds hang off an order — see
 * {@code /api/admin/v1/orders/{uuid}/refunds}.
 *
 * <p>The list endpoint is exposed for completeness (audit exports,
 * future dashboards); the order-detail response embeds the full history
 * so the order page itself doesn't need to call this at all.
 */
@Injectable({ providedIn: 'root' })
export class RefundsService {
  private readonly http = inject(HttpClient);

  list(orderUuid: string): Observable<RefundEntry[]> {
    return this.http.get<RefundEntry[]>(this.base(orderUuid));
  }

  create(orderUuid: string, payload: CreateRefundPayload): Observable<RefundEntry> {
    return this.http.post<RefundEntry>(this.base(orderUuid), payload);
  }

  private base(orderUuid: string): string {
    return `${environment.apiBaseUrl}/orders/${encodeURIComponent(orderUuid)}/refunds`;
  }
}
