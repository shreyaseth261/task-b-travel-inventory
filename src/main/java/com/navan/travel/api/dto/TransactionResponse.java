package com.navan.travel.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.navan.travel.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TransactionResponse(
        String id,
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLineDto> taxLines,
        List<FeeLineDto> feeLines,
        JsonNode metadata
) {
}
