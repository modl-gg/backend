package gg.modl.backend.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProvisioningStatus {
    PENDING("PENDING"),
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    private final String value;

    ProvisioningStatus(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static ProvisioningStatus fromValue(String value) {
        for (ProvisioningStatus v : values()) {
            if (v.value.equalsIgnoreCase(value)) return v;
        }
        throw new IllegalArgumentException("Unknown ProvisioningStatus: " + value);
    }
}
