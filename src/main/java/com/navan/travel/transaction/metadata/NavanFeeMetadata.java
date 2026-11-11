package com.navan.travel.transaction.metadata;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.navan.travel.transaction.FeeKind;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NavanFeeMetadata(
        FeeKind feeKind,
        String description,
        String relatedExternalId
) {
}
