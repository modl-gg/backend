package gg.modl.backend.player.service;

import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.backend.player.controller.MinecraftPlayerProtoMapper;
import gg.modl.proto.modl.v1.SyncActiveStaffMember;
import gg.modl.proto.modl.v1.SyncData;
import gg.modl.proto.modl.v1.SyncMigrationTask;
import gg.modl.proto.modl.v1.SyncModifiedPunishment;
import gg.modl.proto.modl.v1.SyncPendingPunishment;
import gg.modl.proto.modl.v1.SyncPendingStatWipe;
import gg.modl.proto.modl.v1.SyncPlayerNotification;
import gg.modl.proto.modl.v1.SyncPunishmentModification;
import gg.modl.proto.modl.v1.SyncPunishmentWithModifications;
import gg.modl.proto.modl.v1.SyncResponse;
import gg.modl.proto.modl.v1.SyncStaff2faVerification;
import gg.modl.proto.modl.v1.SyncStaffNotification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongConsumer;
import org.springframework.stereotype.Component;

/**
 * Converts the {@code Map<String,Object>} sync payload shapes produced by
 * {@link MinecraftSyncService} into the {@code sync.proto} message types. Shared
 * by the V3 sync controller (full {@link SyncResponse}) and the realtime
 * publishers (per-domain messages), so the websocket push payloads always match
 * the HTTP baseline sync shape.
 */
@Component
public class SyncProtoFactory {

    public SyncResponse toSyncResponse(Map<String, Object> body) {
        return SyncResponse.newBuilder()
            .setTimestamp(stringValue(body.get("timestamp")))
            .setData(toSyncData(map(body.get("data"))))
            .build();
    }

    public SyncData toSyncData(Map<String, Object> data) {
        SyncData.Builder builder = SyncData.newBuilder();

        listOfMaps(data.get("pendingPunishments")).stream()
            .map(this::toPendingPunishment)
            .forEach(builder::addPendingPunishments);
        listOfMaps(data.get("recentlyStartedPunishments")).stream()
            .map(this::toPendingPunishment)
            .forEach(builder::addRecentlyStartedPunishments);
        listOfMaps(data.get("recentlyModifiedPunishments")).stream()
            .map(this::toModifiedPunishment)
            .forEach(builder::addRecentlyModifiedPunishments);
        listOfMaps(data.get("playerNotifications")).stream()
            .map(this::toPlayerNotification)
            .forEach(builder::addPlayerNotifications);
        listOfMaps(data.get("activeStaffMembers")).stream()
            .map(this::toActiveStaffMember)
            .forEach(builder::addActiveStaffMembers);
        listOfMaps(data.get("staffNotifications")).stream()
            .map(this::toStaffNotification)
            .forEach(builder::addStaffNotifications);
        listOfMaps(data.get("pendingStatWipes")).stream()
            .map(this::toPendingStatWipe)
            .forEach(builder::addPendingStatWipes);
        listOfMaps(data.get("staff2faVerifications")).stream()
            .map(this::toStaff2faVerification)
            .forEach(builder::addStaff2FaVerifications);

        if (data.get("migrationTask") instanceof Map<?, ?> migrationTask) {
            builder.setMigrationTask(toMigrationTask(stringObjectMap(migrationTask)));
        }
        setOptionalLong(builder::setStaffPermissionsUpdatedAt, data.get("staffPermissionsUpdatedAt"));
        setOptionalLong(builder::setPunishmentTypesUpdatedAt, data.get("punishmentTypesUpdatedAt"));
        return builder.build();
    }

    public SyncPendingPunishment toPendingPunishment(Map<String, Object> entry) {
        return SyncPendingPunishment.newBuilder()
            .setMinecraftUuid(stringValue(entry.get("minecraftUuid")))
            .setUsername(stringValue(entry.get("username")))
            .setPunishment(MinecraftPlayerProtoMapper.toSimplePunishment(map(entry.get("punishment"))))
            .build();
    }

    public SyncModifiedPunishment toModifiedPunishment(Map<String, Object> entry) {
        return SyncModifiedPunishment.newBuilder()
            .setMinecraftUuid(stringValue(entry.get("minecraftUuid")))
            .setUsername(stringValue(entry.get("username")))
            .setPunishment(toPunishmentWithModifications(map(entry.get("punishment"))))
            .build();
    }

    public SyncPlayerNotification toPlayerNotification(Map<String, Object> notification) {
        SyncPlayerNotification.Builder builder = SyncPlayerNotification.newBuilder()
            .setId(stringValue(notification.get("id")))
            .setMessage(stringValue(notification.get("message")))
            .setType(stringValue(notification.get("type")));

        setOptionalString(builder::setTargetPlayerUuid, notification.get("targetPlayerUuid"));
        setOptionalLong(builder::setTimestamp, notification.get("timestamp"));
        Object data = notification.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            builder.setData(ProtoMapperSupport.legacyStruct(stringObjectMap(dataMap)));
        }
        return builder.build();
    }

    public SyncStaffNotification toStaffNotification(Map<String, Object> notification) {
        SyncStaffNotification.Builder builder = SyncStaffNotification.newBuilder()
            .setId(stringValue(notification.get("id")))
            .setType(stringValue(notification.get("type")))
            .setMessage(stringValue(notification.get("message")));

        setOptionalLong(builder::setTimestamp, notification.get("timestamp"));
        Object data = notification.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            builder.setData(ProtoMapperSupport.legacyStruct(stringObjectMap(dataMap)));
        }
        return builder.build();
    }

    public SyncActiveStaffMember toActiveStaffMember(Map<String, Object> staff) {
        SyncActiveStaffMember.Builder builder = SyncActiveStaffMember.newBuilder()
            .setMinecraftUuid(stringValue(staff.get("minecraftUuid")))
            .setMinecraftUsername(stringValue(staff.get("minecraftUsername")))
            .setStaffUsername(stringValue(staff.get("staffUsername")))
            .setStaffRole(stringValue(staff.get("staffRole")))
            .setEmail(stringValue(staff.get("email")))
            .setStaffId(stringValue(staff.get("staffId")));

        list(staff.get("permissions")).stream()
            .map(Objects::toString)
            .forEach(builder::addPermissions);
        setOptionalBoolean(builder::setTwoFactorSessionValid, staff.get("twoFactorSessionValid"));
        return builder.build();
    }

    public SyncPendingStatWipe toPendingStatWipe(Map<String, Object> statWipe) {
        return SyncPendingStatWipe.newBuilder()
            .setMinecraftUuid(stringValue(statWipe.get("minecraftUuid")))
            .setUsername(stringValue(statWipe.get("username")))
            .setPunishmentId(stringValue(statWipe.get("punishmentId")))
            .build();
    }

    public SyncStaff2faVerification toStaff2faVerification(Map<String, Object> verification) {
        return SyncStaff2faVerification.newBuilder()
            .setMinecraftUuid(stringValue(verification.get("minecraftUuid")))
            .build();
    }

    public SyncMigrationTask toMigrationTask(Map<String, Object> task) {
        return SyncMigrationTask.newBuilder()
            .setTaskId(stringValue(task.get("taskId")))
            .setType(stringValue(task.get("type")))
            .build();
    }

    private SyncPunishmentWithModifications toPunishmentWithModifications(Map<String, Object> punishment) {
        SyncPunishmentWithModifications.Builder builder = SyncPunishmentWithModifications.newBuilder()
            .setId(stringValue(punishment.get("id")));

        listOfMaps(punishment.get("modifications")).stream()
            .map(this::toPunishmentModification)
            .forEach(builder::addModifications);
        return builder.build();
    }

    private SyncPunishmentModification toPunishmentModification(Map<String, Object> modification) {
        SyncPunishmentModification.Builder builder = SyncPunishmentModification.newBuilder()
            .setType(stringValue(modification.get("type")));

        setOptionalLong(builder::setTimestamp, modification.get("timestamp"));
        setOptionalLong(builder::setEffectiveDuration, modification.get("effectiveDuration"));
        return builder.build();
    }

    private static List<?> list(Object object) {
        if (object instanceof List<?> values) {
            return values;
        }
        return List.of();
    }

    private static List<Map<String, Object>> listOfMaps(Object object) {
        return list(object).stream()
            .filter(Map.class::isInstance)
            .map(value -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                return map;
            })
            .toList();
    }

    private static Map<String, Object> map(Object object) {
        if (object instanceof Map<?, ?> rawMap) {
            return stringObjectMap(rawMap);
        }
        return Map.of();
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(Objects.toString(key), value));
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : Objects.toString(value);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return 0L;
    }

    private static void setOptionalString(java.util.function.Consumer<String> setter, Object value) {
        if (value != null) {
            setter.accept(Objects.toString(value));
        }
    }

    private static void setOptionalLong(LongConsumer setter, Object value) {
        if (value != null) {
            setter.accept(longValue(value));
        }
    }

    private static void setOptionalBoolean(java.util.function.Consumer<Boolean> setter, Object value) {
        if (value instanceof Boolean bool) {
            setter.accept(bool);
        }
    }
}
