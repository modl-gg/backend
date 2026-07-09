package gg.modl.backend.realtime.dispatch;

import gg.modl.proto.modl.v1.RealtimeEnvelope;
import gg.modl.proto.modl.v1.Topic;
import java.util.Objects;

public record RealtimeOutboundEvent(
    String serverId,
    Topic topic,
    RealtimeEnvelope envelope
) {
    public RealtimeOutboundEvent {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(envelope, "envelope");
    }
}
