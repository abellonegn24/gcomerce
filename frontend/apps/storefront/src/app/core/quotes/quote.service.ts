import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PagedQuotes,
  QuoteDetail,
  QuoteSummary,
  RequestQuotePayload,
} from './quote.model';

/**
 * Wrapper over {@code /me/quotes}. No local cache — quote state changes on
 * the server (admin review, pricing edits) so we always re-fetch on page mount.
 */
@Injectable({ providedIn: 'root' })
export class QuoteService {
  private readonly http = inject(HttpClient);

  list(page = 0, size = 20): Observable<PagedQuotes> {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));
    return this.http.get<PagedQuotes>(`${environment.apiBaseUrl}/me/quotes`, {
      params,
    });
  }

  get(uuid: string): Observable<QuoteDetail> {
    return this.http.get<QuoteDetail>(
      `${environment.apiBaseUrl}/me/quotes/${uuid}`,
    );
  }

  create(payload: RequestQuotePayload): Observable<QuoteDetail> {
    return this.http.post<QuoteDetail>(
      `${environment.apiBaseUrl}/me/quotes`,
      payload,
    );
  }

  accept(uuid: string): Observable<QuoteDetail> {
    return this.http.post<QuoteDetail>(
      `${environment.apiBaseUrl}/me/quotes/${uuid}/accept`,
      {},
    );
  }
}
