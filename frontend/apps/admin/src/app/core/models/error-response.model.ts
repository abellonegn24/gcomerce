/**
 * TypeScript mirror of the JSON error envelope emitted by
 * {@code com.gadaaistar.web.GlobalExceptionMapper} on the backend.
 */
export interface ApiError {
  code: string;
  message: string;
  details: Record<string, unknown>;
  correlationId?: string | null;
  timestamp?: string;
}

export interface ApiErrorEnvelope {
  error: ApiError;
}

/** Type guard usable in HTTP interceptors and toast handlers. */
export function isApiErrorEnvelope(value: unknown): value is ApiErrorEnvelope {
  return (
    !!value &&
    typeof value === 'object' &&
    'error' in value &&
    !!(value as ApiErrorEnvelope).error &&
    typeof (value as ApiErrorEnvelope).error.code === 'string'
  );
}
