import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  ChangeStatusPayload,
  CustomerActionResponse,
  CustomerDetail,
  CustomerStatus,
  CustomerType,
  PagedCustomers,
  SetGroupPayload,
} from './customer.model';

/**
 * Admin-side customer data access. Read endpoints allow any admin; write
 * endpoints require SUPERADMIN or CUSTOMER_MANAGER (enforced server-side).
 */
@Injectable({ providedIn: 'root' })
export class CustomersService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return `${environment.apiBaseUrl}/customers`;
  }

  // -------------------------------------------------------------------------
  // Read
  // -------------------------------------------------------------------------

  list(opts: {
    q?: string;
    type?: CustomerType | null;
    status?: CustomerStatus | null;
    page?: number;
    size?: number;
  } = {}): Observable<PagedCustomers> {
    let params = new HttpParams();
    if (opts.q)      params = params.set('q', opts.q);
    if (opts.type)   params = params.set('type', opts.type);
    if (opts.status) params = params.set('status', opts.status);
    if (opts.page !== undefined) params = params.set('page', String(opts.page));
    if (opts.size !== undefined) params = params.set('size', String(opts.size));

    return this.http.get<PagedCustomers>(this.base(), { params });
  }

  get(id: number): Observable<CustomerDetail> {
    return this.http.get<CustomerDetail>(`${this.base()}/${id}`);
  }

  // -------------------------------------------------------------------------
  // Write
  // -------------------------------------------------------------------------

  changeStatus(id: number, payload: ChangeStatusPayload): Observable<CustomerActionResponse> {
    return this.http.post<CustomerActionResponse>(`${this.base()}/${id}/status`, payload);
  }

  approveB2B(id: number): Observable<CustomerActionResponse> {
    return this.http.post<CustomerActionResponse>(`${this.base()}/${id}/approve-b2b`, {});
  }

  forceVerifyEmail(id: number): Observable<CustomerActionResponse> {
    return this.http.post<CustomerActionResponse>(`${this.base()}/${id}/verify-email`, {});
  }

  revokeSessions(id: number): Observable<CustomerActionResponse> {
    return this.http.post<CustomerActionResponse>(`${this.base()}/${id}/revoke-sessions`, {});
  }

  /**
   * Issue a one-shot password-reset link and email it to the customer.
   * The reset link points at the storefront (not the admin) — the customer
   * completes the flow themselves on the same UI as the normal
   * "Forgot password". Also revokes all live sessions server-side.
   */
  sendPasswordReset(id: number): Observable<CustomerActionResponse> {
    return this.http.post<CustomerActionResponse>(`${this.base()}/${id}/reset-password`, {});
  }

  setGroup(id: number, payload: SetGroupPayload): Observable<CustomerActionResponse> {
    return this.http.post<CustomerActionResponse>(`${this.base()}/${id}/group`, payload);
  }
}
