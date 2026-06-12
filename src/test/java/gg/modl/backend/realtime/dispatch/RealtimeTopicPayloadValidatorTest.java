package gg.modl.backend.realtime.dispatch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.modl.proto.modl.v1.AssignedTicketSubscriptionChangedEvent;
import gg.modl.proto.modl.v1.MigrationStatusChangedEvent;
import gg.modl.proto.modl.v1.PanelInvalidatedEvent;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.PermissionInvalidatedEvent;
import gg.modl.proto.modl.v1.PunishmentPushEvent;
import gg.modl.proto.modl.v1.PunishmentTypeInvalidatedEvent;
import gg.modl.proto.modl.v1.RealtimeEnvelope;
import gg.modl.proto.modl.v1.StatWipePushEvent;
import gg.modl.proto.modl.v1.TicketChangedEvent;
import gg.modl.proto.modl.v1.Topic;
import org.junit.jupiter.api.Test;

class RealtimeTopicPayloadValidatorTest {
    private final RealtimeTopicPayloadValidator validator = new RealtimeTopicPayloadValidator();

    @Test
    void acceptsPanelTopicPayloadPairs() {
        assertTrue(validator.isValid(event(
            Topic.TOPIC_PANEL_TICKETS,
            RealtimeEnvelope.newBuilder().setTicketChanged(TicketChangedEvent.newBuilder().setTicketId("ticket-1").build()).build()
        )));
        assertTrue(validator.isValid(event(
            Topic.TOPIC_PANEL_ASSIGNED_TICKETS,
            RealtimeEnvelope.newBuilder().setAssignedTicketSubscriptionChanged(AssignedTicketSubscriptionChangedEvent.newBuilder().build()).build()
        )));
        assertTrue(validator.isValid(event(
            Topic.TOPIC_PANEL_MIGRATIONS,
            RealtimeEnvelope.newBuilder().setMigrationStatusChanged(MigrationStatusChangedEvent.newBuilder().setMigrationId("migration-1").build()).build()
        )));
        assertTrue(validator.isValid(event(
            Topic.TOPIC_PANEL_PUNISHMENTS,
            RealtimeEnvelope.newBuilder().setPanelInvalidated(
                PanelInvalidatedEvent.newBuilder().setResource(PanelResource.PANEL_RESOURCE_PUNISHMENTS).build()).build()
        )));
    }

    @Test
    void acceptsEnabledMinecraftTopicPayloadPairs() {
        assertTrue(validator.isValid(event(
            Topic.TOPIC_MINECRAFT_PERMISSIONS,
            RealtimeEnvelope.newBuilder().setPermissionInvalidated(PermissionInvalidatedEvent.newBuilder().build()).build()
        )));
        assertTrue(validator.isValid(event(
            Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES,
            RealtimeEnvelope.newBuilder().setPunishmentTypeInvalidated(PunishmentTypeInvalidatedEvent.newBuilder()).build()
        )));
        assertTrue(validator.isValid(event(
            Topic.TOPIC_MINECRAFT_PUNISHMENTS,
            RealtimeEnvelope.newBuilder().setPunishmentPush(PunishmentPushEvent.newBuilder().build()).build()
        )));
        assertTrue(validator.isValid(event(
            Topic.TOPIC_MINECRAFT_STAT_WIPES,
            RealtimeEnvelope.newBuilder().setStatWipePush(StatWipePushEvent.newBuilder().build()).build()
        )));
    }

    @Test
    void rejectsMismatchedOrNotYetEnabledTopicPayloadPairs() {
        assertFalse(validator.isValid(event(
            Topic.TOPIC_PANEL_TICKETS,
            RealtimeEnvelope.newBuilder().setPermissionInvalidated(PermissionInvalidatedEvent.newBuilder().build()).build()
        )));
        assertFalse(validator.isValid(event(
            Topic.TOPIC_MINECRAFT_PRESENCE,
            RealtimeEnvelope.newBuilder().setPermissionInvalidated(PermissionInvalidatedEvent.newBuilder().build()).build()
        )));
    }

    private RealtimeOutboundEvent event(Topic topic, RealtimeEnvelope envelope) {
        return new RealtimeOutboundEvent("server-a", topic, envelope);
    }
}
