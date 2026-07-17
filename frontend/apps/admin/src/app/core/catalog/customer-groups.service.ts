import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, of, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateCustomerGroupPayload,
  CustomerGroupDto,
  UpdateCustomerGroupPayload,
} from './catalog.model';

/**
 * Cached CRUD wrapper for customer groups. The list is small and changes
 * rarely (typically RETAIL / WHOLESALE_T1 / WHOLESALE_T2 / VIP plus the
 * occasional bespoke B2B tier), so we cache after the first fetch — call
 * {@link list} with {@code force=true} to bust it, or use the mutation
 * methods which auto-invalidate.
 */
@Injectable({ providedIn: 'root' })
export class CustomerGroupsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/customer-groups`;

  private readonly _groups = signal<CustomerGroupDto[] | null>(null);
  readonly groups = this._groups.asReadonly();

  list(force = false): Observable<CustomerGroupDto[]> {
    if (!force && this._groups() !== null) {
      return of(this._groups() ?? []);
    }
    return this.http
      .get<CustomerGroupDto[]>(this.baseUrl)
      .pipe(tap((list) => this._groups.set(list)));
  }

  create(payload: CreateCustomerGroupPayload): Observable<CustomerGroupDto> {
    return this.http
      .post<CustomerGroupDto>(this.baseUrl, payload)
      .pipe(tap(() => this._groups.set(null)));
  }

  update(id: number, payload: UpdateCustomerGroupPayload): Observable<CustomerGroupDto> {
    return this.http
      .patch<CustomerGroupDto>(`${this.baseUrl}/${id}`, payload)
      .pipe(tap(() => this._groups.set(null)));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/${id}`)
      .pipe(tap(() => this._groups.set(null)));
  }
}
