package gg.modl.backend.player.controller;

import gg.modl.backend.player.service.MinecraftSyncService;
import gg.modl.proto.modl.v1.SyncActiveStaffMember;
import gg.modl.proto.modl.v1.SyncCommandLogEntry;
import gg.modl.proto.modl.v1.SyncData;
import gg.modl.proto.modl.v1.SyncMigrationTask;
import gg.modl.proto.modl.v1.SyncModifiedPunishment;
import gg.modl.proto.modl.v1.SyncPendingPunishment;
import gg.modl.proto.modl.v1.SyncPendingStatWipe;
import gg.modl.proto.modl.v1.SyncPlayerNotification;
import gg.modl.proto.modl.v1.SyncPunishmentModification;
import gg.modl.proto.modl.v1.SyncPunishmentWithModifications;
import gg.modl.proto.modl.v1.SyncRequest;
import gg.modl.proto.modl.v1.SyncResponse;
import gg.modl.proto.modl.v1.SyncStaff2faVerification;
import gg.modl.proto.modl.v1.SyncStaffNotification;
import gg.modl.proto.modl.v1.StartupResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

final class MinecraftSyncProtoMapper {
    private MinecraftSyncProtoMapper() {
    }

    static List<MinecraftSyncService.OnlinePlayerInput> toOnlinePlayers(SyncRequest request) {
        return request.getOnlinePlayersList().stream()
            .map(player -> new MinecraftSyncService.OnlinePlayerInput(
                player.getUuid(),
                player.getUsername(),
                player.getIpAddress()
            ))
            .toList();
    }

    static List<MinecraftSyncService.ChatLogInput> toChatLogs(SyncRequest request) {
        return request.getChatLogsList().stream()
            .map(log -> new MinecraftSyncService.ChatLogInput(
                log.getUuid(),
                log.getUsername(),
                log.getMessage(),
                log.getTimestamp(),
                log.getServer()
            ))
            .toList();
    }

    static List<MinecraftSyncService.CommandLogInput> toCommandLogs(SyncRequest request) {
        return request.getCommandLogsList().stream()
            .map(log -> new MinecraftSyncService.CommandLogInput(
                log.getUuid(),
                log.getUsername(),
                log.getCommand(),
                log.getTimestamp(),
                log.getServer()
            ))
            .toList();
    }

    static MinecraftSyncService.ServerStatusInput toServerStatus(SyncRequest request) {
        if (!request.hasServerStatus()) {
            return null;
        }
        return new MinecraftSyncService.ServerStatusInput(
            request.getServerStatus().getOnlinePlayerCount(),
            request.getServerStatus().getMaxPlayers(),
            request.getServerStatus().getServerVersion(),
            request.getServerStatus().getPlatformType(),
            request.getServerStatus().getPluginVersion()
        );
    }

    static SyncResponse toSyncResponse(Map<String, Object> body) {
        return SyncResponse.newBuilder()
            .setTimestamp(stringValue(body.get("timestamp")))
            .setData(toSyncData(map(body.get("data"))))
            .build();
    }

    static StartupResponse toStartupResponse(Map<String, Object> body) {
        return StartupResponse.newBuilder()
            .setPanelUrl(stringValue(body.get("panelUrl")))
            .setTimestamp(stringValue(body.get("timestamp")))
            .setServerInstanceId(stringValue(body.get("serverInstanceId")))
            .build();
    }

    private static SyncData toSyncData(Map<String, Object> data) {
        SyncData.Builder builder = SyncData.newBuilder();

        listOfMaps(data.get("pendingPunishments")).stream()
            .map(MinecraftSyncProtoMapper::toPendingPunishment)
            .forEach(builder::addPendingPunishments);
        listOfMaps(data.get("recentlyStartedPunishments")).stream()
            .map(MinecraftSyncProtoMapper::toPendingPunishment)
            .forEach(builder::addRecentlyStartedPunishments);
        listOfMaps(data.get("recentlyModifiedPunishments")).stream()
            .map(MinecraftSyncProtoMapper::toModifiedPunishment)
            .forEach(builder::addRecentlyModifiedPunishments);
        listOfMaps(data.get("playerNotifications")).stream()
            .map(MinecraftSyncProtoMapper::toPlayerNotification)
            .forEach(builder::addPlayerNotifications);
        listOfMaps(data.get("activeStaffMembers")).stream()
            .map(MinecraftSyncProtoMapper::toActiveStaffMember)
            .forEach(builder::addActiveStaffMembers);
        listOfMaps(data.get("staffNotifications")).stream()
            .map(MinecraftSyncProtoMapper::toStaffNotification)
            .forEach(builder::addStaffNotifications);
        listOfMaps(data.get("pendingStatWipes")).stream()
            .map(MinecraftSyncProtoMapper::toPendingStatWipe)
            .forEach(builder::addPendingStatWipes);
        listOfMaps(data.get("staff2faVerifications")).stream()
            .map(MinecraftSyncProtoMapper::toStaff2faVerification)
            .forEach(builder::addStaff2FaVerifications);

        if (data.get("migrationTask") instanceof Map<?, ?> migrationTask) {
            builder.setMigrationTask(toMigrationTask(stringObjectMap(migrationTask)));
        }
        setOptionalLong(builder::setStaffPermissionsUpdatedAt, data.get("staffPermissionsUpdatedAt"));
        setOptionalLong(builder::setPunishmentTypesUpdatedAt, data.get("punishmentTypesUpdatedAt"));
        return builder.build();
    }

    private static SyncPendingPunishment toPendingPunishment(Map<String, Object> entry) {
        return SyncPendingPunishment.newBuilder()
            .setMinecraftUuid(stringValue(entry.get("minecraftUuid")))
            .setUsername(stringValue(entry.get("username")))
            .setPunishment(MinecraftPlayerProtoMapper.toSimplePunishment(map(entry.get("punishment"))))
            .build();
    }

    private static SyncModifiedPunishment toModifiedPunishment(Map<String, Object> entry) {
        return SyncModifiedPunishment.newBuilder()
            .setMinecraftUuid(stringValue(entry.get("minecraftUuid")))
            .setUsername(stringValue(entry.get("username")))
            .setPunishment(toPunishmentWithModifications(map(entry.get("punishment"))))
            .build();
    }

    private static SyncPunishmentWithModifications toPunishmentWithModifications(Map<String, Object> punishment) {
        SyncPunishmentWithModifications.Builder builder = SyncPunishmentWithModifications.newBuilder()
            .setId(stringValue(punishment.get("id")));

        listOfMaps(punishment.get("modifications")).stream()
            .map(MinecraftSyncProtoMapper::toPunishmentModification)
            .forEach(builder::addModifications);
        return builder.build();
    }

    private static SyncPunishmentModification toPunishmentModification(Map<String, Object> modification) {
        SyncPunishmentModification.Builder builder = SyncPunishmentModification.newBuilder()
            .setType(stringValue(modification.get("type")));

        setOptionalLong(builder::setTimestamp, modification.get("timestamp"));
        setOptionalLong(builder::setEffectiveDuration, modification.get("effectiveDuration"));
        return builder.build();
    }

    private static SyncPlayerNotification toPlayerNotification(Map<String, Object> notification) {
        SyncPlayerNotification.Builder builder = SyncPlayerNotification.newBuilder()
            .setId(stringValue(notification.get("id")))
            .setMessage(stringValue(notification.get("message")))
            .setType(stringValue(notification.get("type")));

        setOptionalString(builder::setTargetPlayerUuid, notification.get("targetPlayerUuid"));
        setOptionalLong(builder::setTimestamp, notification.get("timestamp"));
        Object data = notification.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            builder.setData(MinecraftPlayerProtoMapper.toStruct(stringObjectMap(dataMap)));
        }
        return builder.build();
    }

    private static SyncActiveStaffMember toActiveStaffMember(Map<String, Object> staff) {
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

    private static SyncStaffNotification toStaffNotification(Map<String, Object> notification) {
        SyncStaffNotification.Builder builder = SyncStaffNotification.newBuilder()
            .setId(stringValue(notification.get("id")))
            .setType(stringValue(notification.get("type")))
            .setMessage(stringValue(notification.get("message")));

        setOptionalLong(builder::setTimestamp, notification.get("timestamp"));
        Object data = notification.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            builder.setData(MinecraftPlayerProtoMapper.toStruct(stringObjectMap(dataMap)));
        }
        return builder.build();
    }

    private static SyncPendingStatWipe toPendingStatWipe(Map<String, Object> statWipe) {
        return SyncPendingStatWipe.newBuilder()
            .setMinecraftUuid(stringValue(statWipe.get("minecraftUuid")))
            .setUsername(stringValue(statWipe.get("username")))
            .setPunishmentId(stringValue(statWipe.get("punishmentId")))
            .build();
    }

    private static SyncStaff2faVerification toStaff2faVerification(Map<String, Object> verification) {
        return SyncStaff2faVerification.newBuilder()
            .setMinecraftUuid(stringValue(verification.get("minecraftUuid")))
            .build();
    }

    private static SyncMigrationTask toMigrationTask(Map<String, Object> task) {
        return SyncMigrationTask.newBuilder()
            .setTaskId(stringValue(task.get("taskId")))
            .setType(stringValue(task.get("type")))
            .build();
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
        return rawMap.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> Objects.toString(entry.getKey()),
                Map.Entry::getValue,
                (first, second) -> second,
                LinkedHashMap::new
            ));
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

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.parseInt(string);
        }
        return 0;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return false;
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
