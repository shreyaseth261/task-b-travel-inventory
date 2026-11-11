package com.navan.travel.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.navan.travel.common.ApiException;

public enum TransactionType {
    FLIGHT("flight"),
    HOTEL("hotel"),
    RAIL("rail"),
    NAVAN_FEE("navan_fee");

    private final String json;

    TransactionType(String json) {
        this.json = json;
    }

    @JsonValue
    public String toJson() {
        return json;
    }

    @JsonCreator
    public static TransactionType fromJson(String value) {
        if (value == null) {
            throw ApiException.badRequest("UNKNOWN_TYPE", "type is required");
        }
        for (TransactionType type : values()) {
            if (type.json.equals(value)) {
                return type;
            }
        }
        throw ApiException.badRequest("UNKNOWN_TYPE", "Unknown type: " + value);
    }
}
