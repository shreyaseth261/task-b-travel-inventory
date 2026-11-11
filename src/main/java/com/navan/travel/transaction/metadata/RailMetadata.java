package com.navan.travel.transaction.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RailMetadata(List<Leg> legs) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Leg(
            String origin,
            String destination,
            Instant departure,
            Instant arrival,
            String trainNumber,
            @JsonProperty("class") String travelClass
    ) {
    }
}
