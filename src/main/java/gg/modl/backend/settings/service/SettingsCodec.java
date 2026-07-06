package gg.modl.backend.settings.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SettingsCodec<T> {
    private static final Logger log = LoggerFactory.getLogger(SettingsCodec.class);
    private static final JavaType MAP_TYPE =
        TypeFactory.defaultInstance().constructMapType(Map.class, String.class, Object.class);

    private final ObjectMapper objectMapper;
    private final JavaType type;
    private final Supplier<T> defaults;

    private SettingsCodec(ObjectMapper objectMapper, JavaType type, Supplier<T> defaults) {
        this.objectMapper = objectMapper;
        this.type = type;
        this.defaults = defaults;
    }

    public static <T> SettingsCodec<T> of(ObjectMapper objectMapper, Class<T> type, Supplier<T> defaults) {
        return new SettingsCodec<>(objectMapper, TypeFactory.defaultInstance().constructType(type), defaults);
    }

    public static <T> SettingsCodec<T> of(ObjectMapper objectMapper, TypeReference<T> type, Supplier<T> defaults) {
        return new SettingsCodec<>(objectMapper, TypeFactory.defaultInstance().constructType(type), defaults);
    }

    public T decode(Object data) {
        if (data == null || (data instanceof Map<?, ?> map && map.isEmpty())) {
            return defaults.get();
        }
        try {
            return objectMapper.convertValue(data, type);
        } catch (IllegalArgumentException exception) {
            log.warn("Failed to decode settings of type {}, using defaults: {}", type, exception.getMessage());
            return defaults.get();
        }
    }

    public Map<String, Object> encode(T value) {
        return objectMapper.convertValue(value, MAP_TYPE);
    }
}
