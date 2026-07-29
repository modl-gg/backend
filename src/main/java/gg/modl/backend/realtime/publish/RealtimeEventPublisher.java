package gg.modl.backend.realtime.publish;

import gg.modl.backend.realtime.dispatch.RealtimeEventDispatcher;
import gg.modl.backend.realtime.dispatch.RealtimeOutboundEvent;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.MigrationTaskPushEvent;
import gg.modl.proto.modl.v1.PanelInvalidatedEvent;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.PlayerNotificationPushEvent;
import gg.modl.proto.modl.v1.PunishmentPushEvent;
import gg.modl.proto.modl.v1.RealtimeEnvelope;
import gg.modl.proto.modl.v1.Staff2faPushEvent;
import gg.modl.proto.modl.v1.StaffNotificationPushEvent;
import gg.modl.proto.modl.v1.SyncMigrationTask;
import gg.modl.proto.modl.v1.SyncModifiedPunishment;
import gg.modl.proto.modl.v1.SyncPendingPunishment;
import gg.modl.proto.modl.v1.SyncPlayerNotification;
import gg.modl.proto.modl.v1.SyncStaff2faVerification;
import gg.modl.proto.modl.v1.SyncStaffNotification;
import gg.modl.proto.modl.v1.Topic;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeEventPublisher {
    private static final String PUNISHMENT_KEY_INFIX = "::pun::";
    private static final String STAFF_2FA_KEY_INFIX = "::2fa::";
    private static final String KEY_FIELD_SEPARATOR = "::";

    private final RealtimeEventDispatcher dispatcher;

    public void invalidatePanel(Server server, PanelResource resource) {
        invalidatePanel(server, resource, null);
    }

    public void invalidatePanel(Server server, PanelResource resource, @Nullable String resourceId) {
        Topic topic = PanelResourceTopics.topicFor(resource);
        publish(server.getId(), topic, envelope -> {
            PanelInvalidatedEvent.Builder event = PanelInvalidatedEvent.newBuilder()
                .setResource(resource)
                .setTenantId(server.getId());
            if (resourceId != null) {
                event.setResourceId(resourceId);
            }
            envelope.setPanelInvalidated(event);
        });
    }

    public void pushPunishments(Server server, List<SyncPendingPunishment> pending, List<SyncModifiedPunishment> modified) {
        PunishmentPushEvent event = PunishmentPushEvent.newBuilder()
            .addAllPending(pending)
            .addAllModified(modified)
            .build();
        publish(server.getId(), Topic.TOPIC_MINECRAFT_PUNISHMENTS,
            punishmentEventId(server.getId(), pending, modified),
            envelope -> envelope.setPunishmentPush(event));
    }

    public void pushPlayerNotifications(Server server, List<SyncPlayerNotification> notifications) {
        PlayerNotificationPushEvent event = PlayerNotificationPushEvent.newBuilder()
            .addAllNotifications(notifications)
            .build();
        publish(server.getId(), Topic.TOPIC_MINECRAFT_PLAYER_NOTIFICATIONS,
            null, envelope -> envelope.setPlayerNotificationPush(event));
    }

    public void pushStaffNotifications(Server server, List<SyncStaffNotification> notifications) {
        StaffNotificationPushEvent event = StaffNotificationPushEvent.newBuilder()
            .addAllNotifications(notifications)
            .build();
        publish(server.getId(), Topic.TOPIC_MINECRAFT_STAFF_NOTIFICATIONS,
            null, envelope -> envelope.setStaffNotificationPush(event));
    }

    public void pushStaff2fa(Server server, List<SyncStaff2faVerification> verifications, @Nullable String discriminator) {
        Staff2faPushEvent event = Staff2faPushEvent.newBuilder()
            .addAllVerifications(verifications)
            .build();
        publish(server.getId(), Topic.TOPIC_MINECRAFT_STAFF_2FA,
            staff2faEventId(server.getId(), verifications, discriminator),
            envelope -> envelope.setStaff2FaPush(event));
    }

    public void pushMigrationTask(Server server, SyncMigrationTask task) {
        MigrationTaskPushEvent event = MigrationTaskPushEvent.newBuilder()
            .setTask(task)
            .build();
        publish(server.getId(), Topic.TOPIC_MINECRAFT_MIGRATION_TASKS,
            null, envelope -> envelope.setMigrationTaskPush(event));
    }

    private void publish(String serverId, Topic topic, Consumer<RealtimeEnvelope.Builder> payload) {
        publish(serverId, topic, null, payload);
    }

    private void publish(String serverId, Topic topic, @Nullable String eventId, Consumer<RealtimeEnvelope.Builder> payload) {
        try {
            RealtimeEnvelope.Builder envelope = RealtimeEnvelope.newBuilder();
            if (eventId != null) {
                envelope.setEventId(eventId);
            }
            payload.accept(envelope);
            dispatcher.publish(new RealtimeOutboundEvent(serverId, topic, envelope.build()));
        } catch (RuntimeException e) {
            log.warn("Failed to dispatch realtime event topic={} server={}", topic, serverId, e);
        }
    }

    @Nullable
    private static String punishmentEventId(String serverId, List<SyncPendingPunishment> pending,
                                            List<SyncModifiedPunishment> modified) {
        if (pending.size() == 1 && modified.isEmpty()) {
            var punishment = pending.get(0).getPunishment();
            return serverId + PUNISHMENT_KEY_INFIX + punishment.getId() + KEY_FIELD_SEPARATOR + punishment.getIssuedAt();
        }
        if (modified.size() == 1 && pending.isEmpty()) {
            var punishment = modified.get(0).getPunishment();
            return serverId + PUNISHMENT_KEY_INFIX + punishment.getId() + KEY_FIELD_SEPARATOR + punishment.getModificationsCount();
        }
        return null;
    }

    @Nullable
    private static String staff2faEventId(String serverId, List<SyncStaff2faVerification> verifications,
                                          @Nullable String discriminator) {
        if (verifications.size() != 1 || discriminator == null) {
            return null;
        }
        return serverId + STAFF_2FA_KEY_INFIX + verifications.get(0).getMinecraftUuid() + KEY_FIELD_SEPARATOR + discriminator;
    }
}
