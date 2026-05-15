package gg.modl.backend.infrastructure.proto;

import org.springframework.http.MediaType;

public final class ProtobufMediaTypes {
    public static final String APPLICATION_X_PROTOBUF_VALUE = "application/x-protobuf";
    public static final String APPLICATION_PROTOBUF_VALUE = "application/protobuf";

    public static final MediaType APPLICATION_X_PROTOBUF = new MediaType("application", "x-protobuf");
    public static final MediaType APPLICATION_PROTOBUF = new MediaType("application", "protobuf");

    private ProtobufMediaTypes() {
    }
}
