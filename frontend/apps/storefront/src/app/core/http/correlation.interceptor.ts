import { HttpInterceptorFn } from '@angular/common/http';

import { environment } from '../../../environments/environment';

/**
 * Attaches an `X-Correlation-Id` header to every same-origin API call so the
 * backend can echo it back and stitch logs together.
 *
 * The ID is a compact time+random string — no need for a real UUID here since
 * the backend will generate one if this header isn't present.
 */
export const correlationInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiBaseUrl)) return next(req);
  const id = `gi-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return next(req.clone({ setHeaders: { 'X-Correlation-Id': id } }));
};
