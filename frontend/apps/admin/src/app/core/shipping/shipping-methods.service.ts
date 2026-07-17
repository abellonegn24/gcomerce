import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateShippingMethodPayload,
  ShippingMethodDto,
  UpdateShippingMethodPayload,
} from './shipping.model';

/**
 * Admin-side shipping-method CRUD.
 *
 * The customer-facing quote (with cost pre-computed for the current cart)
 * lives on the storefront service — this admin service only manages the
 * rate cards themselves.
 */
@Injectable({ providedIn: 'root' })
export class ShippingMethodsService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return `${environment.apiBaseUrl}/shipping-methods`;
  }

  list(): Observable<ShippingMethodDto[]> {
    return this.http.get<ShippingMethodDto[]>(this.base());
  }

  get(id: number): Observable<ShippingMethodDto> {
    return this.http.get<ShippingMethodDto>(`${this.base()}/${id}`);
  }

  create(payload: CreateShippingMethodPayload): Observable<ShippingMethodDto> {
    return this.http.post<ShippingMethodDto>(this.base(), payload);
  }

  update(id: number, payload: UpdateShippingMethodPayload): Observable<ShippingMethodDto> {
    return this.http.patch<ShippingMethodDto>(`${this.base()}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
}
