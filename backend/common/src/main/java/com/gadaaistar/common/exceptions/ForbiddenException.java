package com.gadaaistar.common.exceptions;

public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", 403, message);
    }
}
