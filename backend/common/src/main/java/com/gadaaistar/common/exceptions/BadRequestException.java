package com.gadaaistar.common.exceptions;

import java.util.Map;

public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super("BAD_REQUEST", 400, message);
    }

    public BadRequestException(String code, String message) {
        super(code, 400, message);
    }

    public BadRequestException(String code, String message, Map<String, Object> details) {
        super(code, 400, message, details);
    }
}
