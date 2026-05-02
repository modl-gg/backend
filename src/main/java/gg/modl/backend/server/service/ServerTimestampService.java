package gg.modl.backend.server.service;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.realtime.dispatch.RealtimeEventDispatcher;
import gg.modl.backend.realtime.dispatch.RealtimeOutboundEvent;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.PermissionInvalidatedEvent;
import gg.modl.proto.modl.v1.PunishmentTypeInvalidatedEvent;
import gg.modl.proto.modl.v1.RealtimeEnvelope;
import gg.modl.proto.modl.v1.Topic;
import java.util.Date;
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

    private final ServerMongoRepository serverRepository;
    private final RealtimeEventDispatcher realtimeEventDispatcher;
    private long lastTimestampMillis;

    public void updateStaffPermissionsTimestamp(@NotNull Server server) {
        Date timestamp = nextTimestamp();
        serverRepository.updateStaffPermissionsTimestamp(server.getId(), timestamp);
        try {
            realtimeEventDispatcher.publish(new RealtimeOutboundEvent(
                server.getId(),
                Topic.TOPIC_MINECRAFT_PERMISSIONS,
                RealtimeEnvelope.newBuilder()
                    .setEventId(eventId(server, STAFF_PERMISSIONS_TIMESTAMP_FIELD, timestamp))
                    .setPermissionInvalidated(PermissionInvalidatedEvent.newBuilder())
                    .build()
            ));
        } catch (Exception e) {
            log.warn("Failed to dispatch realtime staff permissions invalidation", e);
        }
    }

    public void updatePunishmentTypesTimestamp(@NotNull Server server) {
        Date timestamp = nextTimestamp();
        serverRepository.updatePunishmentTypesTimestamp(server.getId(), timestamp);
        try {
            realtimeEventDispatcher.publish(new RealtimeOutboundEvent(
                server.getId(),
                Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES,
                RealtimeEnvelope.newBuilder()
                    .setEventId(eventId(server, PUNISHMENT_TYPES_TIMESTAMP_FIELD, timestamp))
                    .setPunishmentTypeInvalidated(PunishmentTypeInvalidatedEvent.newBuilder())
                    .build()
            ));
        } catch (Exception e) {
            log.warn("Failed to dispatch realtime punishment type invalidation", e);
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
