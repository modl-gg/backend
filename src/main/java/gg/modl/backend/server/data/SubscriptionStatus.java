package gg.modl.backend.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SubscriptionStatus {
    ACTIVE("ACTIVE"),
    CANCELED("CANCELED"),
    PAST_DUE("PAST_DUE"),
    INACTIVE("INACTIVE"),
    TRIALING("TRIALING"),
    INCOMPLETE("INCOMPLETE"),
    INCOMPLETE_EXPIRED("INCOMPLETE_EXPIRED"),
    UNPAID("UNPAID"),
    PAUSED("PAUSED");

    private final String value;

    SubscriptionStatus(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static SubscriptionStatus fromValue(String value) {
        for (SubscriptionStatus v : values()) {
            if (v.value.equalsIgnoreCase(value)) return v;
        }
        throw new IllegalArgumentException("Unknown SubscriptionStatus: " + value);
    }
}
