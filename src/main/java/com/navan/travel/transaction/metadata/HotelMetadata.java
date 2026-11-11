package com.navan.travel.transaction.metadata;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HotelMetadata(
        String name,
        Address address,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer nights,
        BigDecimal roomRatePerNight,
        List<Extra> extras
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Address(String street, String city, String country) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Extra(String name, BigDecimal amount) {
    }
}
