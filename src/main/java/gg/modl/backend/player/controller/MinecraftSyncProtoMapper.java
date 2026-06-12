package gg.modl.backend.player.controller;

import gg.modl.backend.player.service.MinecraftSyncService;
import gg.modl.proto.modl.v1.SyncRequest;
import gg.modl.proto.modl.v1.StartupResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    static StartupResponse toStartupResponse(Map<String, Object> body) {
        StartupResponse.Builder builder = StartupResponse.newBuilder()
            .setPanelUrl(stringValue(body.get("panelUrl")))
            .setTimestamp(stringValue(body.get("timestamp")))
            .setServerInstanceId(stringValue(body.get("serverInstanceId")));

        setBooleanIfPresent(builder, "setRealtimeEnabled", body.get("realtimeEnabled"));
        setIntIfPresent(builder, "setRealtimeProtocolVersion", body.get("realtimeProtocolVersion"));
        setStringIfPresent(builder, "setRealtimeUrl", body.get("realtimeUrl"));
        list(body.get("realtimeTopics")).stream()
            .map(Objects::toString)
            .forEach(topic -> setStringIfPresent(builder, "addRealtimeTopics", topic));

        return builder.build();
    }

    private static List<?> list(Object object) {
        if (object instanceof List<?> values) {
            return values;
        }
        return List.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : Objects.toString(value);
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

    private static void setStringIfPresent(Object target, String methodName, Object value) {
        if (value != null) {
            invokeIfPresent(target, methodName, String.class, Objects.toString(value));
        }
    }

    private static void setIntIfPresent(Object target, String methodName, Object value) {
        invokeIfPresent(target, methodName, int.class, intValue(value));
    }

    private static void setBooleanIfPresent(Object target, String methodName, Object value) {
        invokeIfPresent(target, methodName, boolean.class, booleanValue(value));
    }

    private static void invokeIfPresent(Object target, String methodName, Class<?> parameterType, Object value) {
        try {
            target.getClass().getMethod(methodName, parameterType).invoke(target, value);
        } catch (NoSuchMethodException ignored) {
            // Published proto artifacts can lag additive local schema fields during rollout.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set protobuf field with " + methodName, e);
        }
    }
}
