import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, NgZone, PLATFORM_ID, inject } from '@angular/core';

import { AdminAuthService } from './admin-auth.service';

/**
 * Idle-timeout watchdog for the admin portal.
 *
 * <p>Bound at the app shell (see {@code AppComponent.ngOnInit}). While the
 * admin is signed in, we listen for coarse user-activity events at the
 * document level and reset a wall-clock deadline whenever we see one.
 *
 * <p>If {@code IDLE_TIMEOUT_MS} elapses with no activity we call
 * {@link AdminAuthService#forceLogout} with reason {@code 'idle'}. The app
 * shell picks up that signal and redirects to {@code /login} with a notice.
 *
 * <h3>Background-tab handling</h3>
 * Browsers aggressively throttle {@code setInterval} in hidden tabs (often
 * to once per minute or less), so the periodic {@link #check} may not fire
 * while the user is away. To close that gap we:
 * <ul>
 *   <li>do <strong>not</strong> treat {@code visibilitychange} as user
 *       activity (it fires both when leaving <em>and</em> returning, which
 *       would reset the timer right when we need it most);</li>
 *   <li>run an immediate {@link #check} when the tab becomes visible again,
 *       so a session that should have expired while hidden is caught the
 *       instant the user comes back.</li>
 * </ul>
 *
 * <h3>Why this is independent of the backend idle timeout</h3>
 * The backend rotates the refresh token (and resets its expiry) on every
 * {@code /auth/refresh} call. Background polling (e.g. the notification
 * bell) triggers refreshes, which would keep the backend session alive
 * indefinitely. This service is the <em>primary</em> idle enforcement —
 * it tracks real human interaction (mouse, keyboard, touch) and cannot be
 * defeated by background API traffic.
 */
@Injectable({ providedIn: 'root' })
export class IdleTimeoutService {
  /** Wall-clock cap on continuous inactivity for admin sessions (10 min). */
  private static readonly IDLE_TIMEOUT_MS = 10 * 60 * 1000;
  /** Poll interval — we tick this often to notice the deadline crossing. */
  private static readonly CHECK_INTERVAL_MS = 15 * 1000;

  private readonly auth = inject(AdminAuthService);
  private readonly zone = inject(NgZone);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly doc = inject(DOCUMENT);

  private lastActivityAt = Date.now();
  private timerId: ReturnType<typeof setInterval> | null = null;
  private listenersAttached = false;

  /**
   * Genuine user-interaction events. {@code visibilitychange} is
   * intentionally excluded — see class docs.
   */
  private static readonly ACTIVITY_EVENTS: (keyof DocumentEventMap)[] = [
    'mousemove',
    'mousedown',
    'keydown',
    'touchstart',
    'scroll',
    'wheel',
  ];

  private readonly onActivity = (): void => {
    this.lastActivityAt = Date.now();
  };

  private readonly onVisibilityChange = (): void => {
    if (this.doc.visibilityState === 'visible') {
      // Tab just became visible — run an immediate check in case the
      // interval was throttled while hidden and the session should have
      // already expired.
      this.check();
    }
  };

  /** Idempotent — call from AppComponent on init. */
  start(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    if (this.listenersAttached) return;

    this.zone.runOutsideAngular(() => {
      for (const type of IdleTimeoutService.ACTIVITY_EVENTS) {
        this.doc.addEventListener(type, this.onActivity, { passive: true });
      }
      this.doc.addEventListener('visibilitychange', this.onVisibilityChange);
      this.timerId = setInterval(
        () => this.check(),
        IdleTimeoutService.CHECK_INTERVAL_MS
      );
    });
    this.listenersAttached = true;
    this.lastActivityAt = Date.now();
  }

  /** Symmetric teardown — currently only used by tests. */
  stop(): void {
    if (!this.listenersAttached) return;
    for (const type of IdleTimeoutService.ACTIVITY_EVENTS) {
      this.doc.removeEventListener(type, this.onActivity);
    }
    this.doc.removeEventListener('visibilitychange', this.onVisibilityChange);
    if (this.timerId !== null) {
      clearInterval(this.timerId);
      this.timerId = null;
    }
    this.listenersAttached = false;
  }

  private check(): void {
    // Only the app shell's job to watch while signed in — unauthenticated
    // pages (login screen) shouldn't get bounced.
    if (!this.auth.isAuthenticated()) {
      this.lastActivityAt = Date.now();
      return;
    }
    const idleFor = Date.now() - this.lastActivityAt;
    if (idleFor >= IdleTimeoutService.IDLE_TIMEOUT_MS) {
      // Bounce back into the zone so the redirect + signals update cleanly.
      this.zone.run(() => this.auth.forceLogout('idle'));
      this.lastActivityAt = Date.now();
    }
  }
}
