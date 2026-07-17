package com.gadaaistar.common.exceptions;

public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", 401, message);
    }

    public UnauthorizedException(String code, String message) {
        super(code, 401, message);
    }
}
