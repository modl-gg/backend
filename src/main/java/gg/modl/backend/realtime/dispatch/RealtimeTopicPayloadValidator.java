package gg.modl.backend.realtime.dispatch;

import gg.modl.proto.modl.v1.RealtimeEnvelope;
import gg.modl.proto.modl.v1.Topic;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RealtimeTopicPayloadValidator {
    private static final Map<Topic, Set<RealtimeEnvelope.PayloadCase>> ALLOWED_PAYLOADS = allowedPayloads();

    public boolean isValid(RealtimeOutboundEvent event) {
        Set<RealtimeEnvelope.PayloadCase> allowedPayloads = ALLOWED_PAYLOADS.get(event.topic());
        return allowedPayloads != null && allowedPayloads.contains(event.envelope().getPayloadCase());
    }

    private static Map<Topic, Set<RealtimeEnvelope.PayloadCase>> allowedPayloads() {
        Map<Topic, Set<RealtimeEnvelope.PayloadCase>> allowedPayloads = new EnumMap<>(Topic.class);
        allowedPayloads.put(Topic.TOPIC_PANEL_TICKETS, EnumSet.of(RealtimeEnvelope.PayloadCase.TICKET_CHANGED));
        allowedPayloads.put(Topic.TOPIC_PANEL_ASSIGNED_TICKETS, EnumSet.of(RealtimeEnvelope.PayloadCase.ASSIGNED_TICKET_SUBSCRIPTION_CHANGED));
        allowedPayloads.put(Topic.TOPIC_PANEL_MIGRATIONS, EnumSet.of(RealtimeEnvelope.PayloadCase.MIGRATION_STATUS_CHANGED));
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_PERMISSIONS, EnumSet.of(RealtimeEnvelope.PayloadCase.PERMISSION_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES, EnumSet.of(RealtimeEnvelope.PayloadCase.PUNISHMENT_TYPE_INVALIDATED));
        return Map.copyOf(allowedPayloads);
    }
}
