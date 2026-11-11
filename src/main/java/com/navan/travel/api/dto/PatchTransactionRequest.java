package com.navan.travel.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PatchTransactionRequest {
    private BigDecimal total;
    private String currency;
    private Instant occurredAt;
    private List<@Valid TaxLineDto> taxLines;
    private List<@Valid FeeLineDto> feeLines;
    private JsonNode metadata;

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public List<TaxLineDto> getTaxLines() {
        return taxLines;
    }

    public void setTaxLines(List<TaxLineDto> taxLines) {
        this.taxLines = taxLines;
    }

    public List<FeeLineDto> getFeeLines() {
        return feeLines;
    }

    public void setFeeLines(List<FeeLineDto> feeLines) {
        this.feeLines = feeLines;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }
}
