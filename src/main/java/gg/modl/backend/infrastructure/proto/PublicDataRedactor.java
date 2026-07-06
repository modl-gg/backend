package gg.modl.backend.infrastructure.proto;

import com.google.protobuf.Struct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class PublicDataRedactor {
    public static final Set<String> SYSTEM_DATA_ALLOWLIST = Set.of(
        "subject", "status", "type", "category", "priority");

    private PublicDataRedactor() {
    }

    public static Map<String, Object> retainAllowed(Map<String, Object> data, Set<String> allowedKeys) {
        if (data == null || data.isEmpty() || allowedKeys == null || allowedKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> retained = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (allowedKeys.contains(entry.getKey())) {
                retained.put(entry.getKey(), entry.getValue());
            }
        }
        return retained;
    }

    public static Struct toPublicStruct(Map<String, Object> data, Set<String> allowedKeys) {
        return ProtoMapperSupport.toStruct(retainAllowed(data, allowedKeys));
    }
}
