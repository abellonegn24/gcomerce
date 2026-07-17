package com.gadaaistar.common.exceptions;

public class NotFoundException extends ApiException {
    public NotFoundException(String resource, Object id) {
        super("NOT_FOUND", 404, resource + " with id '" + id + "' not found.");
    }

    public NotFoundException(String message) {
        super("NOT_FOUND", 404, message);
    }
}
