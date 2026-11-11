package com.navan.travel.transaction.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FlightMetadata(
        String origin,
        String destination,
        BigDecimal fare,
        List<Coupon> coupons
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Coupon(
            String flightNumber,
            String carrier,
            @JsonProperty("from") String from,
            @JsonProperty("to") String to,
            Instant departure,
            Instant arrival
    ) {
    }
}
