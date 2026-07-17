package com.gadaaistar.common.exceptions;

import java.util.Map;

public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super("CONFLICT", 409, message);
    }

    public ConflictException(String code, String message, Map<String, Object> details) {
        super(code, 409, message, details);
    }
}
