import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ShippingQuoteListResponse } from './shipping.model';

/**
 * Storefront shipping quotes.
 *
 * The endpoint takes no query params — the server reads the caller's cart
 * to compute weight + currency and returns rates already priced against
 * that specific cart. This way the picker and the placement call cannot
 * disagree about weight (the customer never edits the cart between them
 * without a re-fetch).
 */
@Injectable({ providedIn: 'root' })
export class ShippingService {
  private readonly http = inject(HttpClient);

  getQuotes(): Observable<ShippingQuoteListResponse> {
    return this.http.get<ShippingQuoteListResponse>(
      `${environment.apiBaseUrl}/me/shipping/quotes`,
    );
  }
}
