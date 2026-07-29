package gg.modl.backend.server.service;

import gg.modl.backend.database.mongo.repository.ServerSettingsTimestampRepository;
import gg.modl.backend.realtime.dispatch.RealtimeEventDispatcher;
import gg.modl.backend.realtime.dispatch.RealtimeOutboundEvent;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.PermissionInvalidatedEvent;
import gg.modl.proto.modl.v1.PunishmentTypeInvalidatedEvent;
import gg.modl.proto.modl.v1.RealtimeEnvelope;
import gg.modl.proto.modl.v1.Topic;
import java.util.Date;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServerTimestampService {
    private static final String STAFF_PERMISSIONS_TIMESTAMP_FIELD = "staffPermissionsUpdatedAt";
    private static final String PUNISHMENT_TYPES_TIMESTAMP_FIELD = "punishmentTypesUpdatedAt";

    private final ServerSettingsTimestampRepository serverSettingsTimestampRepository;
    private final RealtimeEventDispatcher realtimeEventDispatcher;
    private long lastTimestampMillis;

    public void updateStaffPermissionsTimestamp(@NotNull Server server) {
        Date timestamp = nextTimestamp();
        serverSettingsTimestampRepository.updateStaffPermissionsTimestamp(server.getId(), timestamp);
        publishInvalidation(server, timestamp, STAFF_PERMISSIONS_TIMESTAMP_FIELD,
            Topic.TOPIC_MINECRAFT_PERMISSIONS,
            envelope -> envelope.setPermissionInvalidated(PermissionInvalidatedEvent.newBuilder()),
            "Failed to dispatch realtime staff permissions invalidation");
    }

    public void updatePunishmentTypesTimestamp(@NotNull Server server) {
        Date timestamp = nextTimestamp();
        serverSettingsTimestampRepository.updatePunishmentTypesTimestamp(server.getId(), timestamp);
        publishInvalidation(server, timestamp, PUNISHMENT_TYPES_TIMESTAMP_FIELD,
            Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES,
            envelope -> envelope.setPunishmentTypeInvalidated(PunishmentTypeInvalidatedEvent.newBuilder()),
            "Failed to dispatch realtime punishment type invalidation");
    }

    private void publishInvalidation(Server server, Date timestamp, String timestampField, Topic topic,
                                     Consumer<RealtimeEnvelope.Builder> payloadSetter, String failureMessage) {
        try {
            RealtimeEnvelope.Builder envelope = RealtimeEnvelope.newBuilder()
                .setEventId(eventId(server, timestampField, timestamp));
            payloadSetter.accept(envelope);
            realtimeEventDispatcher.publish(new RealtimeOutboundEvent(server.getId(), topic, envelope.build()));
        } catch (Exception e) {
            log.warn(failureMessage, e);
        }
    }

    private synchronized Date nextTimestamp() {
        long timestampMillis = System.currentTimeMillis();
        if (timestampMillis <= lastTimestampMillis) {
            timestampMillis = lastTimestampMillis + 1;
        }
        lastTimestampMillis = timestampMillis;
        return new Date(timestampMillis);
    }

    private String eventId(Server server, String timestampField, Date timestamp) {
        return server.getId() + "::" + timestampField + "::" + timestamp.toInstant();
    }
}
