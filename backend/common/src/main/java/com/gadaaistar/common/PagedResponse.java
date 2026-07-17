package com.gadaaistar.common;

import java.util.List;

/**
 * Standard envelope returned by every list endpoint.
 * Matches the shape documented in Section 11.1 of the delivery plan.
 */
public record PagedResponse<T>(
        List<T> data,
        Meta meta
) {
    public record Meta(int page, int pageSize, long total, int totalPages) {
        public static Meta of(int page, int pageSize, long total) {
            int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / pageSize);
            return new Meta(page, pageSize, total, totalPages);
        }
    }

    public static <T> PagedResponse<T> of(List<T> data, int page, int pageSize, long total) {
        return new PagedResponse<>(data, Meta.of(page, pageSize, total));
    }
}
