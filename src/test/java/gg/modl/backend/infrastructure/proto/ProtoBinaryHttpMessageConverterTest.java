package gg.modl.backend.infrastructure.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.modl.proto.modl.v1.SimpleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;

class ProtoBinaryHttpMessageConverterTest {

    private final ProtoBinaryHttpMessageConverter converter = new ProtoBinaryHttpMessageConverter();

    @Test
    void roundTripsGeneratedMessagesAsBinaryProtobuf() throws Exception {
        SimpleResponse original = SimpleResponse.newBuilder()
            .setSuccess(true)
            .build();

        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(original, ProtobufMediaTypes.APPLICATION_X_PROTOBUF, output);

        assertEquals(ProtobufMediaTypes.APPLICATION_X_PROTOBUF, output.getHeaders().getContentType());

        MockHttpInputMessage input = new MockHttpInputMessage(output.getBodyAsBytes());
        input.getHeaders().setContentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF);

        SimpleResponse decoded = (SimpleResponse) converter.read(SimpleResponse.class, input);

        assertEquals(original, decoded);
    }

    @Test
    void acceptsApplicationProtobufCompatibilityMediaType() {
        assertTrue(converter.canRead(SimpleResponse.class, ProtobufMediaTypes.APPLICATION_PROTOBUF));
        assertTrue(converter.canWrite(SimpleResponse.class, ProtobufMediaTypes.APPLICATION_PROTOBUF));
    }

    @Test
    void leavesJsonMediaTypesToProtoJsonConverter() {
        assertFalse(converter.canRead(SimpleResponse.class, MediaType.APPLICATION_JSON));
        assertFalse(converter.canWrite(SimpleResponse.class, MediaType.APPLICATION_JSON));
    }
}
