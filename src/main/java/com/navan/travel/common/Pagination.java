package com.navan.travel.common;

public final class Pagination {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private Pagination() {
    }

    public static void validate(int limit, int offset) {
        if (limit < 1) {
            throw ApiException.badRequest("INVALID_LIMIT", "limit must be at least 1");
        }
        if (limit > MAX_LIMIT) {
            throw ApiException.badRequest("INVALID_LIMIT", "limit must be at most " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw ApiException.badRequest("INVALID_OFFSET", "offset must be at least 0");
        }
    }
}
