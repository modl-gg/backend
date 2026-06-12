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

        // Panel: already-wired topics keep their domain events and additionally accept the generic invalidation.
        allowedPayloads.put(Topic.TOPIC_PANEL_TICKETS,
            EnumSet.of(RealtimeEnvelope.PayloadCase.TICKET_CHANGED, RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_ASSIGNED_TICKETS,
            EnumSet.of(RealtimeEnvelope.PayloadCase.ASSIGNED_TICKET_SUBSCRIPTION_CHANGED));
        allowedPayloads.put(Topic.TOPIC_PANEL_MIGRATIONS,
            EnumSet.of(RealtimeEnvelope.PayloadCase.MIGRATION_STATUS_CHANGED));
        allowedPayloads.put(Topic.TOPIC_PANEL_PLAYERS, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_PUNISHMENTS, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_STAFF, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_ROLES, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_SETTINGS, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_PUNISHMENT_TYPES, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_KNOWLEDGEBASE, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_AUDIT, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_HOMEPAGE, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_APPEALS, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_DASHBOARD, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_PANEL_NOTIFICATIONS, EnumSet.of(RealtimeEnvelope.PayloadCase.PANEL_INVALIDATED));

        // Minecraft: each topic accepts its existing legacy/hint payload(s) plus the new authoritative push.
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_PERMISSIONS,
            EnumSet.of(RealtimeEnvelope.PayloadCase.PERMISSION_INVALIDATED, RealtimeEnvelope.PayloadCase.ACTIVE_STAFF_PUSH));
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES,
            EnumSet.of(RealtimeEnvelope.PayloadCase.PUNISHMENT_TYPE_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_STAFF_NOTIFICATIONS,
            EnumSet.of(RealtimeEnvelope.PayloadCase.STAFF_NOTIFICATION, RealtimeEnvelope.PayloadCase.STAFF_NOTIFICATION_PUSH));
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_PRESENCE,
            EnumSet.of(RealtimeEnvelope.PayloadCase.PRESENCE_SNAPSHOT, RealtimeEnvelope.PayloadCase.PRESENCE_DELTA,
                RealtimeEnvelope.PayloadCase.PRESENCE_INVALIDATED));
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_PUNISHMENTS,
            EnumSet.of(RealtimeEnvelope.PayloadCase.PUNISHMENT_NOTIFICATION, RealtimeEnvelope.PayloadCase.PUNISHMENT_PUSH));
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_PLAYER_NOTIFICATIONS,
            EnumSet.of(RealtimeEnvelope.PayloadCase.PLAYER_NOTIFICATION, RealtimeEnvelope.PayloadCase.PLAYER_NOTIFICATION_PUSH));
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_STAFF_2FA,
            EnumSet.of(RealtimeEnvelope.PayloadCase.STAFF_TWO_FACTOR, RealtimeEnvelope.PayloadCase.STAFF_2FA_PUSH));
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_MIGRATION_TASKS,
            EnumSet.of(RealtimeEnvelope.PayloadCase.MIGRATION_TASK_HINT, RealtimeEnvelope.PayloadCase.MIGRATION_TASK_PUSH));
        allowedPayloads.put(Topic.TOPIC_MINECRAFT_STAT_WIPES,
            EnumSet.of(RealtimeEnvelope.PayloadCase.STAT_WIPE_PUSH));

        return Map.copyOf(allowedPayloads);
    }
}
