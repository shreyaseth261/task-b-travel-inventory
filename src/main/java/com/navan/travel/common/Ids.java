package com.navan.travel.common;

import java.util.UUID;

public final class Ids {
    private Ids() {
    }

    public static String transaction() {
        return "txn_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String invoice() {
        return "inv_" + UUID.randomUUID().toString().replace("-", "");
    }
}
