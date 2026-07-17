import { HttpInterceptorFn } from '@angular/common/http';

import { environment } from '../../../environments/environment';

/**
 * Attaches an `X-Correlation-Id` header to every admin-API call so the backend
 * can echo it back and stitch logs together across services.
 */
export const correlationInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiBaseUrl)) return next(req);
  const id = `ga-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return next(req.clone({ setHeaders: { 'X-Correlation-Id': id } }));
};
