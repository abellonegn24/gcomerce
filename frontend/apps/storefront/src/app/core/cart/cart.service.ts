import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import {
  AddCartItemPayload,
  CartResponse,
  UpdateCartItemPayload,
} from './cart.model';

const GUEST_SESSION_KEY = 'gi-guest-session-key';

/**
 * Signal-based cart store.
 *
 * Supports both authenticated and guest carts:
 * - When signed in, uses /cart endpoints with Bearer auth.
 * - When anonymous, uses /guest-cart endpoints with X-Guest-Session-Key header.
 * - On sign-in, automatically merges the guest cart into the customer cart.
 *
 * The store watches {@link AuthService.isAuthenticated} via an {@link effect}
 * and reloads / merges / clears itself on sign-in / sign-out.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly _cart = signal<CartResponse | null>(null);
  private readonly _loading = signal<boolean>(false);

  /** Read-only view of the current cart snapshot (or null before first load). */
  readonly cart = this._cart.asReadonly();
  readonly loading = this._loading.asReadonly();

  /** Distinct items count. Bound to the header badge. */
  readonly itemCount = computed<number>(() => this._cart()?.distinctItems ?? 0);

  /** Sum of quantities across every line — used by the /cart page. */
  readonly totalUnits = computed<number>(
    () => Number(this._cart()?.totalUnits ?? 0),
  );

  readonly subtotal = computed<number>(() => Number(this._cart()?.subtotal ?? 0));

  constructor() {
    // Auto-refresh / merge / clear on auth state changes.
    effect(() => {
      if (this.auth.isAuthenticated()) {
        // If we have a guest session key, merge the guest cart first.
        const guestKey = this.getGuestSessionKey();
        if (guestKey) {
          this.mergeGuestCart(guestKey).subscribe({
            next: () => this.clearGuestSessionKey(),
            error: () => {
              // If merge fails, still try to load the customer cart.
              this.reload().subscribe({ error: () => void 0 });
            },
          });
        } else {
          this.reload().subscribe({ error: () => void 0 });
        }
      } else {
        // Signed out — load guest cart.
        this.ensureGuestSessionKey();
        this.reload().subscribe({ error: () => void 0 });
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Session key management
  // ---------------------------------------------------------------------------

  private getGuestSessionKey(): string | null {
    if (typeof localStorage === 'undefined') return null;
    return localStorage.getItem(GUEST_SESSION_KEY);
  }

  private ensureGuestSessionKey(): string {
    let key = this.getGuestSessionKey();
    if (!key) {
      key = this.generateUuid();
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(GUEST_SESSION_KEY, key);
      }
    }
    return key;
  }

  private clearGuestSessionKey(): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(GUEST_SESSION_KEY);
    }
  }

  private generateUuid(): string {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  private guestHeaders(): HttpHeaders {
    const key = this.ensureGuestSessionKey();
    return new HttpHeaders({ 'X-Guest-Session-Key': key });
  }

  // ---------------------------------------------------------------------------
  // Read
  // ---------------------------------------------------------------------------

  /** Fetch the caller's cart (authenticated or guest). */
  reload(): Observable<CartResponse | null> {
    if (this.auth.isAuthenticated()) {
      this._loading.set(true);
      return this.http
        .get<CartResponse>(`${environment.apiBaseUrl}/cart`)
        .pipe(
          tap({
            next: (res) => {
              this._cart.set(res);
              this._loading.set(false);
            },
            error: () => this._loading.set(false),
          }),
        );
    }

    // Guest cart
    this._loading.set(true);
    return this.http
      .get<CartResponse>(`${environment.apiBaseUrl}/guest-cart`, {
        headers: this.guestHeaders(),
      })
      .pipe(
        tap({
          next: (res) => {
            this._cart.set(res);
            this._loading.set(false);
          },
          error: () => this._loading.set(false),
        }),
      );
  }

  // ---------------------------------------------------------------------------
  // Mutations — every response replaces the snapshot.
  // ---------------------------------------------------------------------------

  addItem(payload: AddCartItemPayload): Observable<CartResponse> {
    if (this.auth.isAuthenticated()) {
      return this.http
        .post<CartResponse>(`${environment.apiBaseUrl}/cart/items`, payload)
        .pipe(tap((res) => this._cart.set(res)));
    }
    return this.http
      .post<CartResponse>(`${environment.apiBaseUrl}/guest-cart/items`, payload, {
        headers: this.guestHeaders(),
      })
      .pipe(tap((res) => this._cart.set(res)));
  }

  updateItem(productId: number, payload: UpdateCartItemPayload, variantId?: number): Observable<CartResponse> {
    if (this.auth.isAuthenticated()) {
      let url = `${environment.apiBaseUrl}/cart/items/${productId}`;
      if (variantId != null) url += `?variantId=${variantId}`;
      return this.http
        .patch<CartResponse>(url, payload)
        .pipe(tap((res) => this._cart.set(res)));
    }
    let url = `${environment.apiBaseUrl}/guest-cart/items/${productId}`;
    if (variantId != null) url += `?variantId=${variantId}`;
    return this.http
      .patch<CartResponse>(url, payload, { headers: this.guestHeaders() })
      .pipe(tap((res) => this._cart.set(res)));
  }

  removeItem(productId: number, variantId?: number): Observable<CartResponse> {
    if (this.auth.isAuthenticated()) {
      let url = `${environment.apiBaseUrl}/cart/items/${productId}`;
      if (variantId != null) url += `?variantId=${variantId}`;
      return this.http
        .delete<CartResponse>(url)
        .pipe(tap((res) => this._cart.set(res)));
    }
    let url = `${environment.apiBaseUrl}/guest-cart/items/${productId}`;
    if (variantId != null) url += `?variantId=${variantId}`;
    return this.http
      .delete<CartResponse>(url, { headers: this.guestHeaders() })
      .pipe(tap((res) => this._cart.set(res)));
  }

  clearCart(): Observable<CartResponse> {
    if (this.auth.isAuthenticated()) {
      return this.http
        .delete<CartResponse>(`${environment.apiBaseUrl}/cart`)
        .pipe(tap((res) => this._cart.set(res)));
    }
    return this.http
      .delete<CartResponse>(`${environment.apiBaseUrl}/guest-cart`, {
        headers: this.guestHeaders(),
      })
      .pipe(tap((res) => this._cart.set(res)));
  }

  /** Merge guest cart into customer cart on login. */
  private mergeGuestCart(sessionKey: string): Observable<CartResponse> {
    return this.http
      .post<CartResponse>(
        `${environment.apiBaseUrl}/cart/merge`,
        {},
        { headers: new HttpHeaders({ 'X-Guest-Session-Key': sessionKey }) },
      )
      .pipe(tap((res) => this._cart.set(res)));
  }

  /** Locally clear the store — used by the checkout flow after a successful order. */
  reset(): void {
    this._cart.set(null);
  }
}
