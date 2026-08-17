import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Observable, Subject, catchError, map, of, tap, throwError } from 'rxjs';

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
 * Reasons the auth service may emit a forced logout. Consumed by the app
 * shell to show an appropriate toast / notice on the login screen.
 */
export type ForcedLogoutReason = 'expired' | 'idle' | 'refresh-failed';

/**
 * Signal-based auth store for the admin app.
 *
 * <p>Session is persisted in {@code sessionStorage} (not {@code localStorage})
 * so that closing the browser tab automatically ends the admin session — a
 * common hardening measure for back-office portals. Additionally, the
 * {@link #isAuthenticated} signal only returns {@code true} while the stored
 * refresh token has not yet passed its {@code refreshTokenExpiresAt} instant.
 *
 * Public API:
 *   - `currentAdmin()`   → `Signal<AdminUserDto | null>`
 *   - `isAuthenticated()`→ `Signal<boolean>` (false once refresh token is expired)
 *   - `accessToken()`    → `Signal<string | null>`
 *   - `hasRole(name)`    → boolean helper
 *   - `pendingChallenge()`→ `Signal<PendingMfaChallenge | null>`
 *   - `login(payload)`   → `Observable<LoginChallengeResponse>`
 *   - `verifyMfa(code)`  → `Observable<AuthResponse>` (uses the stored challenge)
 *   - `refresh()`        → `Observable<AuthResponse>` (interceptor)
 *   - `logout()`         → `Observable<void>`
 *   - `forceLogout(r)`   → invalidates the local session and emits {@link forcedLogout$}
 *   - `forcedLogout$`    → Subject the app shell subscribes to for redirect + toast
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

  /**
   * Tick every 30 s to force `isAuthenticated` to re-evaluate against wall
   * clock — otherwise a session that expires while the tab sits idle would
   * still read as authenticated until the next HTTP call.
   */
  private readonly _now = signal<number>(Date.now());

  private readonly _forcedLogout = new Subject<ForcedLogoutReason>();
  readonly forcedLogout$ = this._forcedLogout.asObservable();

  readonly currentAdmin = computed<AdminUserDto | null>(
    () => this._session()?.admin ?? null
  );
  readonly isAuthenticated = computed<boolean>(() => {
    const session = this._session();
    if (!session) return false;
    // Consumers should see the session as expired the moment the refresh
    // token wall-clock passes, even between HTTP calls.
    return !this.isExpired(session.refreshTokenExpiresAt, this._now());
  });
  readonly accessToken = computed<string | null>(
    () => this._session()?.accessToken ?? null
  );
  readonly pendingChallenge = computed<PendingMfaChallenge | null>(
    () => this._pending()
  );

  constructor() {
    if (this.isBrowser) {
      // Purge a stale session that was written by an older build (localStorage)
      // or by a previous run whose refresh token has since expired.
      this.migrateLegacyStorageIfNeeded();
      this.pruneIfExpired('expired');

      // Cheap wall-clock tick so `isAuthenticated` becomes false the moment
      // the refresh token expires, without waiting for the next request.
      setInterval(() => {
        this._now.set(Date.now());
        this.pruneIfExpired('expired');
      }, 30_000);
    }
  }

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
    if (this.isExpired(current.refreshTokenExpiresAt)) {
      // Don't even try — the token is provably dead.
      this.forceLogout('expired');
      return throwError(() => new Error('Refresh token has expired.'));
    }
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/refresh`, {
        refreshToken: current.refreshToken,
      })
      .pipe(
        tap((res) => this.storeSession(res)),
        catchError((err) => {
          this.forceLogout('refresh-failed');
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

  /**
   * Invalidates the local session without hitting the server and emits a
   * {@link forcedLogout$} event. Called when the session expired on the
   * client side (idle timeout, refresh-token wall-clock elapsed, refresh
   * request failed). The server-side refresh row is either already dead or
   * unreachable, so we don't attempt to notify it.
   */
  forceLogout(reason: ForcedLogoutReason): void {
    const wasSignedIn = this._session() !== null;
    this.clearSession();
    this.clearPending();
    if (wasSignedIn) {
      this._forcedLogout.next(reason);
    }
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
        sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
      } catch {
        // memory-only fallback on quota / private-mode
      }
    }
  }

  private clearSession(): void {
    this._session.set(null);
    if (this.isBrowser) {
      try {
        sessionStorage.removeItem(SESSION_KEY);
      } catch {
        /* no-op */
      }
    }
  }

  private loadSession(): StoredAdminSession | null {
    try {
      const raw = sessionStorage.getItem(SESSION_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as StoredAdminSession;
    } catch {
      return null;
    }
  }

  /**
   * Older builds stored the session in {@code localStorage}, so a user
   * upgrading to this build would appear "logged in forever". Purge any
   * legacy blob on first load; the user will simply be asked to sign in
   * again under the new (stricter) policy.
   */
  private migrateLegacyStorageIfNeeded(): void {
    try {
      if (localStorage.getItem(SESSION_KEY) !== null) {
        localStorage.removeItem(SESSION_KEY);
      }
    } catch {
      /* no-op */
    }
  }

  private pruneIfExpired(reason: ForcedLogoutReason): void {
    const session = this._session();
    if (!session) return;
    if (this.isExpired(session.refreshTokenExpiresAt)) {
      this.forceLogout(reason);
    }
  }

  private isExpired(iso: string | undefined | null, nowMs: number = Date.now()): boolean {
    if (!iso) return true;
    const t = Date.parse(iso);
    if (Number.isNaN(t)) return true;
    return t <= nowMs;
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
