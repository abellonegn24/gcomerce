import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AdminAuthService } from './admin-auth.service';

const AUTH_URLS = [
  '/auth/login',
  '/auth/verify-mfa',
  '/auth/refresh',
  '/auth/logout',
];

/**
 * Functional HTTP interceptor that:
 *   1. Attaches `Authorization: Bearer <access>` to every admin-API call.
 *   2. On 401, attempts a single refresh + retry.
 *   3. Skips the header on the auth endpoints themselves.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AdminAuthService);

  const isApiCall = req.url.startsWith(environment.apiBaseUrl);
  const isAuthCall = AUTH_URLS.some((suffix) => req.url.includes(suffix));

  const token = auth.accessToken();
  const authedReq = isApiCall && token && !isAuthCall ? attachBearer(req, token) : req;

  return next(authedReq).pipe(
    catchError((err: HttpErrorResponse) => {
      const shouldRetry =
        err.status === 401 && isApiCall && !isAuthCall && !!auth.accessToken();
      if (!shouldRetry) return throwError(() => err);

      return auth.refresh().pipe(
        switchMap((res) => {
          const retried = attachBearer(req, res.accessToken);
          return next(retried) as Observable<HttpEvent<unknown>>;
        }),
        catchError((refreshErr) => throwError(() => refreshErr))
      );
    })
  );
};

function attachBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}
