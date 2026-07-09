package gg.modl.backend.player.controller;

import gg.modl.backend.player.service.MinecraftSyncService;
import gg.modl.proto.modl.v1.SyncRequest;
import java.util.List;

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
}
