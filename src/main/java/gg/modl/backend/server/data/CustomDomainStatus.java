package gg.modl.backend.server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CustomDomainStatus {
    PENDING("PENDING"),
    ERROR("ERROR"),
    ACTIVE("ACTIVE"),
    VERIFYING("VERIFYING");

    private final String value;

    CustomDomainStatus(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static CustomDomainStatus fromValue(String value) {
        for (CustomDomainStatus v : values()) {
            if (v.value.equalsIgnoreCase(value)) return v;
        }
        throw new IllegalArgumentException("Unknown CustomDomainStatus: " + value);
    }
}
