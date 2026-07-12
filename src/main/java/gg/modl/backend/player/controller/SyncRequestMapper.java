package gg.modl.backend.player.controller;

import gg.modl.backend.player.controller.MinecraftSyncController.ChatLogEntry;
import gg.modl.backend.player.controller.MinecraftSyncController.CommandLogEntry;
import gg.modl.backend.player.controller.MinecraftSyncController.OnlinePlayer;
import gg.modl.backend.player.controller.MinecraftSyncController.ServerStatus;
import gg.modl.backend.player.service.MinecraftSyncService;
import java.util.List;

final class SyncRequestMapper {
    private SyncRequestMapper() {
    }

    static List<MinecraftSyncService.OnlinePlayerInput> toOnlinePlayers(List<OnlinePlayer> players) {
        if (players == null) {
            return List.of();
        }
        return players.stream()
            .map(player -> new MinecraftSyncService.OnlinePlayerInput(player.uuid(), player.username(), player.ipAddress()))
            .toList();
    }

    static List<MinecraftSyncService.ChatLogInput> toChatLogs(List<ChatLogEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
            .map(entry -> new MinecraftSyncService.ChatLogInput(entry.uuid(), entry.username(), entry.message(), entry.timestamp(), entry.server()))
            .toList();
    }

    static List<MinecraftSyncService.CommandLogInput> toCommandLogs(List<CommandLogEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
            .map(entry -> new MinecraftSyncService.CommandLogInput(entry.uuid(), entry.username(), entry.command(), entry.timestamp(), entry.server()))
            .toList();
    }

    static MinecraftSyncService.ServerStatusInput toServerStatus(ServerStatus serverStatus) {
        if (serverStatus == null) {
            return null;
        }
        return new MinecraftSyncService.ServerStatusInput(
            serverStatus.onlinePlayerCount(),
            serverStatus.maxPlayers(),
            serverStatus.serverVersion(),
            serverStatus.platformType(),
            serverStatus.pluginVersion()
        );
    }
}
