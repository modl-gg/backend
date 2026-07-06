package gg.modl.backend.infrastructure.proto;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProtoMapperSupport {

    // 2^53 is the largest integer a double represents exactly; integral values beyond it must travel as
    // strings inside free-form Structs to survive the double-backed google.protobuf.Value.
    private static final BigInteger MAX_SAFE_DOUBLE_INTEGER = BigInteger.valueOf(2).pow(53);

    private static final Logger log = LoggerFactory.getLogger(ProtoMapperSupport.class);
    private static final Set<Class<?>> WARNED_UNEXPECTED_TYPES = ConcurrentHashMap.newKeySet();

    private ProtoMapperSupport() {
    }

    public enum StructEncoding {
        CANONICAL,
        LEGACY_DOUBLE_ISO
    }

    public static String stringValue(Object value) {
        return value == null ? "" : Objects.toString(value);
    }

    public static String dateAwareString(Object value) {
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        return stringValue(value);
    }

    public static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.parseInt(string.trim());
        }
        return 0;
    }

    public static int intValueOrZero(Object value) {
        try {
            return intValue(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static long longValue(Object value) {
        if (value instanceof Date date) {
            return date.getTime();
        }
        if (value instanceof Instant instant) {
            return instant.toEpochMilli();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string.trim());
        }
        return 0L;
    }

    public static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Double.parseDouble(string.trim());
        }
        return 0d;
    }

    public static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return false;
    }

    public static void setOptionalString(Consumer<String> setter, Object value) {
        if (value != null) {
            setter.accept(Objects.toString(value));
        }
    }

    public static void setOptionalInt(IntConsumer setter, Object value) {
        if (value != null) {
            setter.accept(intValue(value));
        }
    }

    public static void setOptionalLong(LongConsumer setter, Object value) {
        if (value != null) {
            setter.accept(longValue(value));
        }
    }

    public static void setOptionalBoolean(Consumer<Boolean> setter, Object value) {
        if (value != null) {
            setter.accept(booleanValue(value));
        }
    }

    public static void setOptionalDouble(Consumer<Double> setter, Object value) {
        if (value != null) {
            setter.accept(doubleValue(value));
        }
    }

    public static Struct toStruct(Map<String, Object> map) {
        return toStruct(map, StructEncoding.CANONICAL);
    }

    public static Struct toStruct(Map<String, Object> map, StructEncoding encoding) {
        Struct.Builder builder = Struct.newBuilder();
        if (map != null) {
            map.forEach((key, value) -> builder.putFields(Objects.toString(key), objectToValue(value, encoding)));
        }
        return builder.build();
    }

    public static Struct legacyStruct(Map<String, Object> map) {
        return toStruct(map, StructEncoding.LEGACY_DOUBLE_ISO);
    }

    public static Map<String, Object> structToMap(Struct struct) {
        Map<String, Object> result = new LinkedHashMap<>();
        struct.getFieldsMap().forEach((key, value) -> result.put(key, valueToObject(value)));
        return result;
    }

    public static Timestamp toTimestamp(Object value) {
        long millis = longValue(value);
        return Timestamp.newBuilder()
            .setSeconds(Math.floorDiv(millis, 1000L))
            .setNanos((int) (Math.floorMod(millis, 1000L) * 1_000_000L))
            .build();
    }

    public static List<?> list(Object object) {
        if (object instanceof List<?> values) {
            return values;
        }
        return List.of();
    }

    public static List<Map<String, Object>> listOfMaps(Object object) {
        return list(object).stream()
            .filter(Map.class::isInstance)
            .map(value -> stringObjectMap((Map<?, ?>) value))
            .toList();
    }

    public static Map<String, Object> map(Object object) {
        if (object instanceof Map<?, ?> rawMap) {
            return stringObjectMap(rawMap);
        }
        return Map.of();
    }

    public static Map<String, Object> stringObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(Objects.toString(key), value));
        return result;
    }

    /**
     * Maps a nullable domain collection to a repeated proto field. {@code adder} is the builder's {@code addX}
     * method; {@code converter} maps each element to its proto type. Null/empty collections add nothing.
     */
    public static <T, P> void addAll(Collection<T> source,
                                     Function<? super T, ? extends P> converter,
                                     Consumer<? super P> adder) {
        if (source == null) {
            return;
        }
        source.forEach(element -> adder.accept(converter.apply(element)));
    }

    /**
     * Maps a domain {@code Map} to a proto {@code map<string, V>} field. {@code putter} is the builder's
     * {@code putX(key, value)} method; {@code converter} maps each value to its proto type.
     */
    public static <V, P> void putAll(Map<String, V> source,
                                     Function<? super V, ? extends P> converter,
                                     BiConsumer<String, ? super P> putter) {
        if (source == null) {
            return;
        }
        source.forEach((key, value) -> putter.accept(key, converter.apply(value)));
    }

    private static Value objectToValue(Object object, StructEncoding encoding) {
        Value.Builder builder = Value.newBuilder();
        if (object == null) {
            return builder.setNullValue(NullValue.NULL_VALUE).build();
        }
        if (object instanceof String string) {
            return builder.setStringValue(string).build();
        }
        if (object instanceof Number number) {
            return numberValue(builder, number, encoding);
        }
        if (object instanceof Boolean bool) {
            return builder.setBoolValue(bool).build();
        }
        if (object instanceof Map<?, ?> rawMap) {
            Struct.Builder struct = Struct.newBuilder();
            rawMap.forEach((key, value) -> struct.putFields(Objects.toString(key), objectToValue(value, encoding)));
            return builder.setStructValue(struct).build();
        }
        if (object instanceof Iterable<?> iterable) {
            ListValue.Builder listValue = ListValue.newBuilder();
            iterable.forEach(item -> listValue.addValues(objectToValue(item, encoding)));
            return builder.setListValue(listValue).build();
        }
        if (object instanceof Date date) {
            return encoding == StructEncoding.LEGACY_DOUBLE_ISO
                ? builder.setStringValue(date.toInstant().toString()).build()
                : integralValue(builder, BigInteger.valueOf(date.getTime()));
        }
        if (encoding == StructEncoding.LEGACY_DOUBLE_ISO && object instanceof Instant instant) {
            return builder.setStringValue(instant.toString()).build();
        }
        if (object instanceof ObjectId objectId) {
            return builder.setStringValue(objectId.toHexString()).build();
        }
        if (object instanceof UUID uuid) {
            return builder.setStringValue(uuid.toString()).build();
        }
        if (object instanceof Binary binary) {
            return builder.setStringValue(Base64.getEncoder().encodeToString(binary.getData())).build();
        }
        return builder.setStringValue(coerceUnexpectedToString(object)).build();
    }

    /**
     * Coerces a free-form Struct value of an unexpected type to its {@code toString()} form, logging
     * a warning once per offending class so a POJO-into-Struct mistake is observable rather than
     * silently producing an opaque string on the wire. Never throws (toStruct is on hot read paths).
     */
    public static String coerceUnexpectedToString(Object object) {
        Class<?> type = object.getClass();
        if (WARNED_UNEXPECTED_TYPES.add(type)) {
            log.warn("ProtoMapperSupport: free-form Struct value of unexpected type {} coerced via "
                + "toString(); value will reach clients as an opaque string. Callers must pass "
                + "structured Maps, not raw beans.", type.getName());
        }
        return Objects.toString(object);
    }

    // google.protobuf.Value carries numbers as a double, so an integral 64-bit value (epoch millis,
    // snowflake-style IDs) above 2^53 loses precision once coerced to double. Emit those as a string so
    // the panel's fromJson rehydration preserves the exact value; genuine floating-point and
    // small integers stay number values.
    private static Value numberValue(Value.Builder builder, Number number, StructEncoding encoding) {
        if (encoding == StructEncoding.LEGACY_DOUBLE_ISO) {
            return builder.setNumberValue(number.doubleValue()).build();
        }
        if (number instanceof Double || number instanceof Float) {
            return builder.setNumberValue(number.doubleValue()).build();
        }
        if (number instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().scale() <= 0
                ? integralValue(builder, decimal.toBigInteger())
                : builder.setNumberValue(decimal.doubleValue()).build();
        }
        if (number instanceof BigInteger bigInteger) {
            return integralValue(builder, bigInteger);
        }
        if (number instanceof Long || number instanceof Integer || number instanceof Short || number instanceof Byte) {
            return integralValue(builder, BigInteger.valueOf(number.longValue()));
        }
        return builder.setNumberValue(number.doubleValue()).build();
    }

    private static Value integralValue(Value.Builder builder, BigInteger value) {
        return value.abs().compareTo(MAX_SAFE_DOUBLE_INTEGER) > 0
            ? builder.setStringValue(value.toString()).build()
            : builder.setNumberValue(value.doubleValue()).build();
    }

    private static Object valueToObject(Value value) {
        return switch (value.getKindCase()) {
            case NULL_VALUE, KIND_NOT_SET -> null;
            case NUMBER_VALUE -> value.getNumberValue();
            case STRING_VALUE -> value.getStringValue();
            case BOOL_VALUE -> value.getBoolValue();
            case STRUCT_VALUE -> structToMap(value.getStructValue());
            case LIST_VALUE -> value.getListValue().getValuesList().stream()
                .map(ProtoMapperSupport::valueToObject)
                .toList();
        };
    }
}
