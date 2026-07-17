import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  ConvertQuotePayload,
  ConvertedOrder,
  PagedQuotes,
  QuoteDetail,
  QuoteStatus,
  RejectQuotePayload,
  SendQuotePayload,
  UpdateQuoteItemPayload,
} from './quote.model';

@Injectable({ providedIn: 'root' })
export class AdminQuotesService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return `${environment.apiBaseUrl}/quotes`;
  }

  list(opts: {
    status?: QuoteStatus | null;
    page?: number;
    size?: number;
  } = {}): Observable<PagedQuotes> {
    let params = new HttpParams();
    if (opts.status) params = params.set('status', opts.status);
    if (opts.page !== undefined) params = params.set('page', String(opts.page));
    if (opts.size !== undefined) params = params.set('size', String(opts.size));
    return this.http.get<PagedQuotes>(this.base(), { params });
  }

  get(uuid: string): Observable<QuoteDetail> {
    return this.http.get<QuoteDetail>(`${this.base()}/${uuid}`);
  }

  startReview(uuid: string): Observable<QuoteDetail> {
    return this.http.post<QuoteDetail>(`${this.base()}/${uuid}/review`, {});
  }

  updateLine(uuid: string, lineId: number, payload: UpdateQuoteItemPayload): Observable<QuoteDetail> {
    return this.http.patch<QuoteDetail>(
      `${this.base()}/${uuid}/items/${lineId}`,
      payload,
    );
  }

  send(uuid: string, payload: SendQuotePayload): Observable<QuoteDetail> {
    return this.http.post<QuoteDetail>(`${this.base()}/${uuid}/send`, payload);
  }

  reject(uuid: string, payload: RejectQuotePayload): Observable<QuoteDetail> {
    return this.http.post<QuoteDetail>(`${this.base()}/${uuid}/reject`, payload);
  }

  convert(uuid: string, payload: ConvertQuotePayload): Observable<ConvertedOrder> {
    return this.http.post<ConvertedOrder>(`${this.base()}/${uuid}/convert`, payload);
  }
}
