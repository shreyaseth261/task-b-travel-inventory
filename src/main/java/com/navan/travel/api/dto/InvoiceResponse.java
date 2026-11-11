package com.navan.travel.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InvoiceResponse(
        String id,
        String currency,
        BigDecimal grandTotal,
        List<Line> lines,
        List<TaxLineDto> taxRollup,
        List<FeeLineDto> feeRollup
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Line(
            String transactionId,
            String type,
            String externalId,
            String currency,
            Instant occurredAt,
            BigDecimal total,
            List<TaxLineDto> taxLines,
            List<FeeLineDto> feeLines,
            JsonNode metadata
    ) {
    }
}
