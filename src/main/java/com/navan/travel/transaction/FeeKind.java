package com.navan.travel.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.navan.travel.common.ApiException;

public enum FeeKind {
    TRIP_FEE("trip_fee"),
    AGENT_CALL_FEE("agent_call_fee");

    private final String json;

    FeeKind(String json) {
        this.json = json;
    }

    @JsonValue
    public String toJson() {
        return json;
    }

    @JsonCreator
    public static FeeKind fromJson(String value) {
        if (value == null) {
            throw ApiException.badRequest("UNKNOWN_FEE_KIND", "metadata.fee_kind is required");
        }
        for (FeeKind kind : values()) {
            if (kind.json.equals(value)) {
                return kind;
            }
        }
        throw ApiException.badRequest("UNKNOWN_FEE_KIND", "Unknown navan_fee.fee_kind: " + value);
    }
}
