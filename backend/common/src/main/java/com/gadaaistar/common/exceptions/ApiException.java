package com.gadaaistar.common.exceptions;

import java.util.Map;

/**
 * Base for every business-level exception thrown from Gadaa Istar services.
 * The {@code code} field ends up in the {@code error.code} slot of the
 * standard JSON error envelope; keep values SHORT and SCREAMING_SNAKE_CASE.
 */
public abstract class ApiException extends RuntimeException {

    private final String code;
    private final int status;
    private final Map<String, Object> details;

    protected ApiException(String code, int status, String message) {
        this(code, status, message, Map.of());
    }

    protected ApiException(String code, int status, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String code()               { return code; }
    public int status()                { return status; }
    public Map<String, Object> details() { return details; }
}
