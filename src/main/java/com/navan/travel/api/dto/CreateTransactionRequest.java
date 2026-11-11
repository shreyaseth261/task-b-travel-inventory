package com.navan.travel.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.navan.travel.transaction.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateTransactionRequest(
        @NotNull TransactionType type,
        @NotBlank String externalId,
        @NotBlank String currency,
        @NotNull Instant occurredAt,
        @NotNull BigDecimal total,
        @NotNull @Valid List<TaxLineDto> taxLines,
        @NotNull @Valid List<FeeLineDto> feeLines,
        @NotNull JsonNode metadata
) {
}
