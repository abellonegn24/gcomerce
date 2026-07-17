package com.gadaaistar.common;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error envelope returned by every API endpoint on failure.
 * Matches the shape documented in Section 11.1 of the delivery plan.
 *
 * Example JSON:
 * <pre>
 * {
 *   "error": {
 *     "code": "VALIDATION_FAILED",
 *     "message": "One or more fields are invalid.",
 *     "details": { "email": "must be a well-formed email address" },
 *     "correlationId": "b7b1a3c8-...",
 *     "timestamp": "2026-07-14T18:03:22Z"
 *   }
 * }
 * </pre>
 */
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            Map<String, Object> details,
            String correlationId,
            Instant timestamp
    ) {}

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message, Map.of(), null, Instant.now()));
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(new ErrorBody(code, message, details, null, Instant.now()));
    }
}
