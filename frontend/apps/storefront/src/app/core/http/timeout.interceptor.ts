import { HttpInterceptorFn } from '@angular/common/http';
import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformServer } from '@angular/common';
import { timeout, catchError, throwError, Observable } from 'rxjs';

import { NormalizedApiError } from './error.interceptor';

/**
 * Applies a timeout to every HTTP request so SSR doesn't hang indefinitely
 * when the backend is slow or unreachable.
 *
 * During SSR the timeout is short (5s) so the page still renders with
 * placeholder content. In the browser it's more generous (30s) so
 * legitimate long-running requests (file uploads, etc.) aren't aborted.
 */
const SSR_TIMEOUT_MS = 5_000;
const BROWSER_TIMEOUT_MS = 30_000;

export const timeoutInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);
  const ms = isPlatformServer(platformId) ? SSR_TIMEOUT_MS : BROWSER_TIMEOUT_MS;

  return next(req).pipe(
    timeout(ms),
    catchError((err): Observable<never> => {
      if (err && err.name === 'TimeoutError') {
        return throwError(() => ({
          status: 0,
          code: 'TIMEOUT',
          message: 'The request took too long. Please try again.',
          details: {},
          correlationId: null,
        }) as NormalizedApiError);
      }
      return throwError(() => err);
    })
  );
};
