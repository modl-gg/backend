package gg.modl.backend.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ServerPlan {
    FREE("FREE"),
    PREMIUM("PREMIUM");

    private final String value;

    ServerPlan(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static ServerPlan fromValue(String value) {
        for (ServerPlan v : values()) {
            if (v.value.equalsIgnoreCase(value)) return v;
        }
        throw new IllegalArgumentException("Unknown ServerPlan: " + value);
    }
}
