import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Observable, catchError, map, of, tap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AdminUserDto,
  AuthResponse,
  LoginChallengeResponse,
  LoginPayload,
  PendingMfaChallenge,
  StoredAdminSession,
  VerifyMfaPayload,
} from './admin-auth.model';

const SESSION_KEY = 'gadaa.admin.session';
const PENDING_KEY = 'gadaa.admin.pending-mfa';

/**
 * Signal-based auth store for the admin app.
 *
 * Public API:
 *   - `currentAdmin()`   → `Signal<AdminUserDto | null>`
 *   - `isAuthenticated()`→ `Signal<boolean>`
 *   - `accessToken()`    → `Signal<string | null>`
 *   - `hasRole(name)`    → boolean helper
 *   - `pendingChallenge()`→ `Signal<PendingMfaChallenge | null>`
 *   - `login(payload)`   → `Observable<LoginChallengeResponse>`
 *   - `verifyMfa(code)`  → `Observable<AuthResponse>` (uses the stored challenge)
 *   - `refresh()`        → `Observable<AuthResponse>` (interceptor)
 *   - `logout()`         → `Observable<void>`
 */
@Injectable({ providedIn: 'root' })
export class AdminAuthService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  private readonly _session = signal<StoredAdminSession | null>(
    this.isBrowser ? this.loadSession() : null
  );
  private readonly _pending = signal<PendingMfaChallenge | null>(
    this.isBrowser ? this.loadPending() : null
  );

  readonly currentAdmin = computed<AdminUserDto | null>(
    () => this._session()?.admin ?? null
  );
  readonly isAuthenticated = computed<boolean>(() => this._session() !== null);
  readonly accessToken = computed<string | null>(
    () => this._session()?.accessToken ?? null
  );
  readonly pendingChallenge = computed<PendingMfaChallenge | null>(
    () => this._pending()
  );

  hasRole(role: string): boolean {
    const currentRole = this.currentAdmin()?.role;
    return currentRole === role;
  }

  // ---------------------------------------------------------------------------
  // Step 1 — email + password
  // ---------------------------------------------------------------------------

  login(payload: LoginPayload): Observable<LoginChallengeResponse> {
    return this.http
      .post<LoginChallengeResponse>(`${environment.apiBaseUrl}/auth/login`, payload)
      .pipe(
        tap((res) =>
          this.storePending({
            state: res.state,
            challengeToken: res.challengeToken,
            challengeExpiresAt: res.challengeExpiresAt,
            email: payload.email,
            setup: res.setup,
          })
        )
      );
  }

  // ---------------------------------------------------------------------------
  // Step 2 — 6-digit TOTP code
  // ---------------------------------------------------------------------------

  verifyMfa(code: string): Observable<AuthResponse> {
    const pending = this._pending();
    if (!pending) {
      return throwError(
        () => new Error('No pending MFA challenge. Please sign in again.')
      );
    }
    const body: VerifyMfaPayload = {
      challengeToken: pending.challengeToken,
      code,
    };
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/verify-mfa`, body)
      .pipe(
        tap((res) => {
          this.storeSession(res);
          this.clearPending();
        })
      );
  }

  // ---------------------------------------------------------------------------
  // Refresh — called by the auth interceptor on 401
  // ---------------------------------------------------------------------------

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

  logout(): Observable<void> {
    const refreshToken = this._session()?.refreshToken ?? null;
    this.clearSession();
    this.clearPending();
    if (!refreshToken) return of(void 0);
    return this.http
      .post<void>(`${environment.apiBaseUrl}/auth/logout`, { refreshToken })
      .pipe(
        map(() => void 0),
        catchError(() => of(void 0))
      );
  }

  /** Cancel a half-finished MFA flow — e.g. user clicks "Back to sign in". */
  cancelPendingChallenge(): void {
    this.clearPending();
  }

  /** Refresh the profile of the currently-signed-in admin from the server. */
  loadCurrentAdmin(): Observable<AdminUserDto | null> {
    if (!this.isAuthenticated()) return of(null);
    return this.http
      .get<AdminUserDto>(`${environment.apiBaseUrl}/auth/me`)
      .pipe(
        tap((admin) => {
          const session = this._session();
          if (session) this.storeSession({ ...session, admin });
        }),
        catchError(() => of(null))
      );
  }

  // ---------------------------------------------------------------------------
  // Storage helpers
  // ---------------------------------------------------------------------------

  private storeSession(input: AuthResponse | StoredAdminSession): void {
    const session: StoredAdminSession = {
      accessToken: input.accessToken,
      accessTokenExpiresAt: input.accessTokenExpiresAt,
      refreshToken: input.refreshToken,
      refreshTokenExpiresAt: input.refreshTokenExpiresAt,
      admin: input.admin,
    };
    this._session.set(session);
    if (this.isBrowser) {
      try {
        localStorage.setItem(SESSION_KEY, JSON.stringify(session));
      } catch {
        // memory-only fallback on quota / private-mode
      }
    }
  }

  private clearSession(): void {
    this._session.set(null);
    if (this.isBrowser) {
      try {
        localStorage.removeItem(SESSION_KEY);
      } catch {
        /* no-op */
      }
    }
  }

  private loadSession(): StoredAdminSession | null {
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as StoredAdminSession;
    } catch {
      return null;
    }
  }

  private storePending(input: PendingMfaChallenge): void {
    this._pending.set(input);
    if (this.isBrowser) {
      try {
        sessionStorage.setItem(PENDING_KEY, JSON.stringify(input));
      } catch {
        // memory-only fallback
      }
    }
  }

  private clearPending(): void {
    this._pending.set(null);
    if (this.isBrowser) {
      try {
        sessionStorage.removeItem(PENDING_KEY);
      } catch {
        /* no-op */
      }
    }
  }

  private loadPending(): PendingMfaChallenge | null {
    try {
      const raw = sessionStorage.getItem(PENDING_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as PendingMfaChallenge;
    } catch {
      return null;
    }
  }
}
