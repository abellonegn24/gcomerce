import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  Address,
  CreateAddressPayload,
  UpdateAddressPayload,
} from './address.model';

/**
 * CRUD wrapper for {@code /me/addresses}. The signal-backed {@code addresses}
 * cache is populated on demand and updated locally after mutations so the
 * checkout page can pick the freshest snapshot without an extra GET.
 */
@Injectable({ providedIn: 'root' })
export class AddressService {
  private readonly http = inject(HttpClient);

  private readonly _addresses = signal<Address[] | null>(null);
  private readonly _loading = signal<boolean>(false);

  readonly addresses = this._addresses.asReadonly();
  readonly loading = this._loading.asReadonly();

  /** The address flagged as {@code isDefault}, or null. */
  readonly defaultAddress = computed<Address | null>(() => {
    const list = this._addresses() ?? [];
    return list.find((a) => a.isDefault) ?? null;
  });

  // ---------------------------------------------------------------------------

  list(force = false): Observable<Address[]> {
    if (!force && this._addresses() !== null) {
      // Return a cached snapshot as a synchronous observable via HttpClient
      // convention — but since we already have the value we can short-circuit.
      // Using of() keeps the API consistent for callers.
      return new Observable<Address[]>((sub) => {
        sub.next(this._addresses() ?? []);
        sub.complete();
      });
    }
    this._loading.set(true);
    return this.http
      .get<Address[]>(`${environment.apiBaseUrl}/me/addresses`)
      .pipe(
        tap({
          next: (list) => {
            this._addresses.set(list);
            this._loading.set(false);
          },
          error: () => this._loading.set(false),
        }),
      );
  }

  create(payload: CreateAddressPayload): Observable<Address> {
    return this.http
      .post<Address>(`${environment.apiBaseUrl}/me/addresses`, payload)
      .pipe(
        tap((created) => {
          const current = this._addresses() ?? [];
          // If the new address is default, clear the flag on siblings locally
          // so the UI reflects the server state without a re-fetch.
          const cleared = created.isDefault
            ? current.map((a) =>
                a.addressType === created.addressType || a.addressType === 'BOTH' || created.addressType === 'BOTH'
                  ? { ...a, isDefault: false }
                  : a,
              )
            : current;
          this._addresses.set([created, ...cleared]);
        }),
      );
  }

  update(id: number, payload: UpdateAddressPayload): Observable<Address> {
    return this.http
      .patch<Address>(`${environment.apiBaseUrl}/me/addresses/${id}`, payload)
      .pipe(
        tap((updated) => {
          const current = this._addresses() ?? [];
          this._addresses.set(current.map((a) => (a.id === updated.id ? updated : a)));
        }),
      );
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<void>(`${environment.apiBaseUrl}/me/addresses/${id}`)
      .pipe(
        tap(() => {
          const current = this._addresses() ?? [];
          this._addresses.set(current.filter((a) => a.id !== id));
        }),
      );
  }

  /** Reset the local cache — used on sign-out. */
  reset(): void {
    this._addresses.set(null);
  }
}
