import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CouponDto,
  CouponListResponse,
  CreateCouponPayload,
  UpdateCouponPayload,
} from './catalog.model';

@Injectable({ providedIn: 'root' })
export class CouponsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/coupons`;

  list(page = 0, size = 20): Observable<CouponListResponse> {
    return this.http.get<CouponListResponse>(this.base, {
      params: { page, size },
    });
  }

  get(id: number): Observable<CouponDto> {
    return this.http.get<CouponDto>(`${this.base}/${id}`);
  }

  create(payload: CreateCouponPayload): Observable<CouponDto> {
    return this.http.post<CouponDto>(this.base, payload);
  }

  update(id: number, payload: UpdateCouponPayload): Observable<CouponDto> {
    return this.http.patch<CouponDto>(`${this.base}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
