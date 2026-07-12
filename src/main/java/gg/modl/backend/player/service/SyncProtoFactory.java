package gg.modl.backend.player.service;

import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.backend.player.controller.MinecraftPlayerProtoMapper;
import gg.modl.backend.player.dto.response.SimplePunishmentView;
import gg.modl.backend.player.dto.response.SyncDataView;
import gg.modl.backend.player.dto.response.SyncPunishmentEntry;
import gg.modl.backend.player.dto.response.SyncResult;
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
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import org.springframework.stereotype.Component;

@Component
public class SyncProtoFactory {

    public SyncResponse toSyncResponse(SyncResult body) {
        return SyncResponse.newBuilder()
            .setTimestamp(stringValue(body.timestamp()))
            .setData(toSyncData(body.data()))
            .build();
    }

    public SyncData toSyncData(SyncDataView data) {
        SyncData.Builder builder = SyncData.newBuilder();

        data.pendingPunishments().stream()
            .map(this::toPendingPunishment)
            .forEach(builder::addPendingPunishments);
        data.recentlyStartedPunishments().stream()
            .map(this::toPendingPunishment)
            .forEach(builder::addRecentlyStartedPunishments);
        data.recentlyModifiedPunishments().stream()
            .map(this::toModifiedPunishment)
            .forEach(builder::addRecentlyModifiedPunishments);
        data.playerNotifications().stream()
            .map(this::toPlayerNotification)
            .forEach(builder::addPlayerNotifications);
        data.activeStaffMembers().stream()
            .map(this::toActiveStaffMember)
            .forEach(builder::addActiveStaffMembers);
        data.staffNotifications().stream()
            .map(this::toStaffNotification)
            .forEach(builder::addStaffNotifications);
        data.pendingStatWipes().stream()
            .map(this::toPendingStatWipe)
            .forEach(builder::addPendingStatWipes);
        if (data.staff2faVerifications() != null) {
            data.staff2faVerifications().stream()
                .map(this::toStaff2faVerification)
                .forEach(builder::addStaff2FaVerifications);
        }

        if (data.migrationTask() != null) {
            builder.setMigrationTask(toMigrationTask(data.migrationTask()));
        }
        setOptionalLong(builder::setStaffPermissionsUpdatedAt, data.staffPermissionsUpdatedAt());
        setOptionalLong(builder::setPunishmentTypesUpdatedAt, data.punishmentTypesUpdatedAt());
        return builder.build();
    }

    public SyncPendingPunishment toPendingPunishment(SyncPunishmentEntry entry) {
        return SyncPendingPunishment.newBuilder()
            .setMinecraftUuid(stringValue(entry.minecraftUuid()))
            .setUsername(stringValue(entry.username()))
            .setPunishment(MinecraftPlayerProtoMapper.toSimplePunishment(entry.punishment()))
            .build();
    }

    public SyncModifiedPunishment toModifiedPunishment(SyncPunishmentEntry entry) {
        return SyncModifiedPunishment.newBuilder()
            .setMinecraftUuid(stringValue(entry.minecraftUuid()))
            .setUsername(stringValue(entry.username()))
            .setPunishment(toPunishmentWithModifications(entry.punishment()))
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

    private SyncPunishmentWithModifications toPunishmentWithModifications(SimplePunishmentView punishment) {
        SyncPunishmentWithModifications.Builder builder = SyncPunishmentWithModifications.newBuilder();
        if (punishment == null) {
            return builder.build();
        }

        builder.setId(stringValue(punishment.id()));
        punishment.modifications().stream()
            .map(this::toPunishmentModification)
            .forEach(builder::addModifications);
        return builder.build();
    }

    private SyncPunishmentModification toPunishmentModification(SimplePunishmentView.Modification modification) {
        SyncPunishmentModification.Builder builder = SyncPunishmentModification.newBuilder()
            .setType(stringValue(modification.type()));

        setOptionalLong(builder::setTimestamp, modification.timestamp());
        setOptionalLong(builder::setEffectiveDuration, modification.effectiveDuration());
        return builder.build();
    }

    private static List<?> list(Object object) {
        if (object instanceof List<?> values) {
            return values;
        }
        return List.of();
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

    private static void setOptionalString(Consumer<String> setter, Object value) {
        if (value != null) {
            setter.accept(Objects.toString(value));
        }
    }

    private static void setOptionalLong(LongConsumer setter, Object value) {
        if (value != null) {
            setter.accept(longValue(value));
        }
    }

    private static void setOptionalBoolean(Consumer<Boolean> setter, Object value) {
        if (value instanceof Boolean bool) {
            setter.accept(bool);
        }
    }
}
