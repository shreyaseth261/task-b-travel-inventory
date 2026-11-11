package com.navan.travel.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return of(left).add(of(right));
    }

    public static void requireNonNegative(BigDecimal value, String field) {
        if (value == null) {
            throw ApiException.badRequest("INVALID_MONEY", field + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("INVALID_MONEY", field + " must not be negative");
        }
    }
}
