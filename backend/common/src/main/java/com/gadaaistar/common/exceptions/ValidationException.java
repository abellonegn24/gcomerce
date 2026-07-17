package com.gadaaistar.common.exceptions;

import java.util.Map;

public class ValidationException extends ApiException {
    public ValidationException(String message) {
        super("VALIDATION_FAILED", 400, message);
    }

    public ValidationException(String message, Map<String, Object> fieldErrors) {
        super("VALIDATION_FAILED", 400, message, fieldErrors);
    }
}
