import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PaymentMethodOption } from './order.model';

/**
 * Fetches the list of payment methods available at checkout based on
 * which gateways are configured on the backend.
 */
@Injectable({ providedIn: 'root' })
export class PaymentMethodsService {
  private readonly http = inject(HttpClient);

  list(): Observable<PaymentMethodOption[]> {
    return this.http.get<PaymentMethodOption[]>(
      `${environment.apiBaseUrl}/me/payment-methods`,
    );
  }
}
