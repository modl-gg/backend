package gg.modl.backend.infrastructure.proto;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class ProtoMapperSupportTest {

    @Test
    void objectIdSerializesToHexString() {
        Struct struct = ProtoMapperSupport.toStruct(Map.of("id", new ObjectId("507f1f77bcf86cd799439011")));
        assertThat(struct.getFieldsMap().get("id").getStringValue()).isEqualTo("507f1f77bcf86cd799439011");
    }

    @Test
    void binarySerializesToBase64NotDebugString() {
        byte[] bytes = {1, 2, 3, 4};
        Struct struct = ProtoMapperSupport.toStruct(Map.of("blob", new Binary(bytes)));
        String value = struct.getFieldsMap().get("blob").getStringValue();
        assertThat(value).isEqualTo(Base64.getEncoder().encodeToString(bytes));
        assertThat(value).doesNotContain("Binary{");
    }

    @Test
    void uuidRoundTripsToCanonicalString() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Struct struct = ProtoMapperSupport.toStruct(Map.of("uuid", uuid));
        assertThat(struct.getFieldsMap().get("uuid").getStringValue()).isEqualTo(uuid.toString());
    }

    @Test
    void scalarsAndNestedStructuresAreUnchanged() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("str", "hello");
        input.put("lng", 42L);
        input.put("dbl", 3.5);
        input.put("bool", true);
        input.put("date", new Date(1000L));
        input.put("nested", Map.of("inner", "x"));
        input.put("list", List.of("a", "b"));

        Struct struct = ProtoMapperSupport.toStruct(input);
        Map<String, Value> fields = struct.getFieldsMap();

        assertThat(fields.get("str").getStringValue()).isEqualTo("hello");
        assertThat(fields.get("lng").getNumberValue()).isEqualTo(42.0);
        assertThat(fields.get("dbl").getNumberValue()).isEqualTo(3.5);
        assertThat(fields.get("bool").getBoolValue()).isTrue();
        assertThat(fields.get("date").getNumberValue()).isEqualTo(1000.0);
        assertThat(fields.get("nested").getStructValue().getFieldsMap().get("inner").getStringValue()).isEqualTo("x");
        assertThat(fields.get("list").getListValue().getValuesCount()).isEqualTo(2);
    }

    @Test
    void unexpectedTypeDegradesToStringWithoutThrowing() {
        Object pojo = new Object() {
            @Override
            public String toString() {
                return "pojo-value";
            }
        };
        Struct struct = ProtoMapperSupport.toStruct(Map.of("weird", pojo));
        assertThat(struct.getFieldsMap().get("weird").getStringValue()).isEqualTo("pojo-value");
    }
}
