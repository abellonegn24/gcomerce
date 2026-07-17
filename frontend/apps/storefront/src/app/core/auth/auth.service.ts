import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Observable, catchError, map, of, tap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AckResponse,
  AuthResponse,
  ChangePasswordPayload,
  CustomerDto,
  LoginPayload,
  RegisterPayload,
  StoredSession,
  UpdateProfilePayload,
} from './auth.model';

const STORAGE_KEY = 'gadaa.storefront.session';

/**
 * Signal-based auth store for the storefront.
 *
 * Public API:
 *   - `currentCustomer()`  → `Signal<CustomerDto | null>`
 *   - `isAuthenticated()`  → `Signal<boolean>`
 *   - `accessToken()`      → `Signal<string | null>`
 *   - `login(payload)`     → `Observable<AuthResponse>`
 *   - `register(payload)`  → `Observable<AuthResponse>`
 *   - `refresh()`          → `Observable<AuthResponse>` (used by interceptor)
 *   - `logout()`           → `Observable<void>`
 *
 * Session state persists to `localStorage` so page reloads restore identity.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  private readonly _session = signal<StoredSession | null>(
    this.isBrowser ? this.loadFromStorage() : null
  );

  readonly currentCustomer = computed<CustomerDto | null>(
    () => this._session()?.customer ?? null
  );
  readonly isAuthenticated = computed<boolean>(() => this._session() !== null);
  readonly accessToken = computed<string | null>(
    () => this._session()?.accessToken ?? null
  );

  // ---------------------------------------------------------------------------

  login(payload: LoginPayload): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, payload)
      .pipe(tap((res) => this.storeSession(res)));
  }

  register(payload: RegisterPayload): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/register`, payload)
      .pipe(tap((res) => this.storeSession(res)));
  }

  /** Called by the auth interceptor on 401. */
  refresh(): Observable<AuthResponse> {
    const current = this._session();
    if (!current?.refreshToken) {
      return throwError(() => new Error('No refresh token available.'));
    }
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/refresh`, {
        refreshToken: current.refreshToken,
      })
      .pipe(
        tap((res) => this.storeSession(res)),
        catchError((err) => {
          this.clearSession();
          return throwError(() => err);
        })
      );
  }

  // ---------------------------------------------------------------------------
  // Email verification
  // ---------------------------------------------------------------------------

  /**
   * Ask the server to (re-)send the verification email. Always resolves with
   * an ack — the server never reveals whether the email exists.
   */
  resendVerification(email: string): Observable<AckResponse> {
    return this.http.post<AckResponse>(
      `${environment.apiBaseUrl}/auth/resend-verification`,
      { email }
    );
  }

  /**
   * Confirm the email address using the token from the emailed link.
   * On success we refresh the stored session's customer snapshot so the header
   * badge / status immediately reflects the change.
   */
  verifyEmail(token: string): Observable<CustomerDto> {
    return this.http
      .post<CustomerDto>(`${environment.apiBaseUrl}/auth/verify-email`, { token })
      .pipe(
        tap((customer) => {
          const session = this._session();
          if (session && session.customer.id === customer.id) {
            this.storeSession({ ...session, customer });
          }
        })
      );
  }

  // ---------------------------------------------------------------------------
  // Password reset
  // ---------------------------------------------------------------------------

  forgotPassword(email: string): Observable<AckResponse> {
    return this.http.post<AckResponse>(
      `${environment.apiBaseUrl}/auth/forgot-password`,
      { email }
    );
  }

  /**
   * Complete a password reset. Server responds 204; we clear the local session
   * as well because the backend just revoked every refresh token for the user.
   */
  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http
      .post<void>(`${environment.apiBaseUrl}/auth/reset-password`, {
        token,
        newPassword,
      })
      .pipe(
        map(() => void 0),
        tap(() => this.clearSession())
      );
  }

  // ---------------------------------------------------------------------------
  // Profile management
  // ---------------------------------------------------------------------------

  /**
   * PATCH /auth/me — apply a partial profile update. Server ignores null / blank
   * fields; on success we refresh the stored session snapshot.
   */
  updateProfile(payload: Partial<UpdateProfilePayload>): Observable<CustomerDto> {
    return this.http
      .patch<CustomerDto>(`${environment.apiBaseUrl}/auth/me`, payload)
      .pipe(
        tap((customer) => {
          const session = this._session();
          if (session) this.storeSession({ ...session, customer });
        })
      );
  }

  /**
   * POST /auth/me/change-password. Server responds 204 and revokes every
   * refresh token; we clear the local session to force a re-sign-in.
   */
  changePassword(payload: ChangePasswordPayload): Observable<void> {
    return this.http
      .post<void>(`${environment.apiBaseUrl}/auth/me/change-password`, payload)
      .pipe(
        map(() => void 0),
        tap(() => this.clearSession())
      );
  }

  // ---------------------------------------------------------------------------

  logout(): Observable<void> {
    const refreshToken = this._session()?.refreshToken ?? null;
    this.clearSession();
    if (!refreshToken) return of(void 0);
    return this.http
      .post<void>(`${environment.apiBaseUrl}/auth/logout`, { refreshToken })
      .pipe(
        map(() => void 0),
        catchError(() => of(void 0))
      );
  }

  /** Called on app bootstrap to refresh the profile if a session exists. */
  loadCurrentCustomer(): Observable<CustomerDto | null> {
    if (!this.isAuthenticated()) return of(null);
    return this.http
      .get<CustomerDto>(`${environment.apiBaseUrl}/auth/me`)
      .pipe(
        tap((customer) => {
          const session = this._session();
          if (session) this.storeSession({ ...session, customer });
        }),
        catchError(() => {
          // The 401 handler in the interceptor already attempts refresh.
          return of(null);
        })
      );
  }

  // ---------------------------------------------------------------------------

  private storeSession(input: AuthResponse | StoredSession): void {
    const session: StoredSession = {
      accessToken: input.accessToken,
      accessTokenExpiresAt: input.accessTokenExpiresAt,
      refreshToken: input.refreshToken,
      refreshTokenExpiresAt: input.refreshTokenExpiresAt,
      customer: input.customer,
    };
    this._session.set(session);
    if (this.isBrowser) {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
      } catch {
        // ignore quota / privacy-mode errors — session becomes memory-only
      }
    }
  }

  private clearSession(): void {
    this._session.set(null);
    if (this.isBrowser) {
      try {
        localStorage.removeItem(STORAGE_KEY);
      } catch {
        // no-op
      }
    }
  }

  private loadFromStorage(): StoredSession | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as StoredSession;
    } catch {
      return null;
    }
  }
}
