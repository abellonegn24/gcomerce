import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';

import { isApiErrorEnvelope } from '../models/error-response.model';

/**
 * Normalizes backend errors to a consistent shape so components can subscribe
 * with `error: (e: NormalizedApiError) => ...` and expect uniform fields.
 */
export interface NormalizedApiError {
  status: number;
  code: string;
  message: string;
  details: Record<string, unknown>;
  correlationId: string | null;
}

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((err: HttpErrorResponse): Observable<never> => {
      return throwError(() => normalize(err));
    })
  );
};

function normalize(err: HttpErrorResponse): NormalizedApiError {
  if (err.status === 0) {
    return {
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Could not reach the server. Please check your connection and try again.',
      details: {},
      correlationId: null,
    };
  }
  if (isApiErrorEnvelope(err.error)) {
    const e = err.error.error;
    return {
      status: err.status,
      code: e.code,
      message: e.message,
      details: e.details ?? {},
      correlationId: e.correlationId ?? null,
    };
  }
  return {
    status: err.status,
    code: 'UNEXPECTED_ERROR',
    message: err.message || 'Something went wrong. Please try again.',
    details: {},
    correlationId: null,
  };
}
