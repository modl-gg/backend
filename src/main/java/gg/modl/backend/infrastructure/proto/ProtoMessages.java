package gg.modl.backend.infrastructure.proto;

import com.google.protobuf.Message;

public final class ProtoMessages {
    private ProtoMessages() {
    }

    public static Message defaultInstance(Class<? extends Message> clazz) {
        try {
            return (Message) clazz.getMethod("getDefaultInstance").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot get default instance for " + clazz.getName(), e);
        }
    }
}
