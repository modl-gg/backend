package gg.modl.backend.player.controller;

import gg.modl.backend.player.service.MinecraftSyncService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.validation.RegExpConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_PLAYERS)
@RequiredArgsConstructor
@Slf4j
public class MinecraftSyncController {
    private final MinecraftSyncService minecraftSyncService;

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @RequestBody @Valid SyncRequest syncRequest,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return ResponseEntity.ok(minecraftSyncService.sync(
                server,
                syncRequest.lastSyncTimestamp(),
                syncRequest.onlinePlayers() == null ? List.of() : syncRequest.onlinePlayers().stream()
                        .map(player -> new MinecraftSyncService.OnlinePlayerInput(player.uuid(), player.username(), player.ipAddress()))
                        .toList(),
                syncRequest.serverName(),
                syncRequest.chatLogs() == null ? List.of() : syncRequest.chatLogs().stream()
                        .map(log -> new MinecraftSyncService.ChatLogInput(log.uuid(), log.username(), log.message(), log.timestamp(), log.server()))
                        .toList(),
                syncRequest.commandLogs() == null ? List.of() : syncRequest.commandLogs().stream()
                        .map(log -> new MinecraftSyncService.CommandLogInput(log.uuid(), log.username(), log.command(), log.timestamp(), log.server()))
                        .toList()
        ));
    }

    public record SyncRequest(
            String lastSyncTimestamp,
            @Valid List<OnlinePlayer> onlinePlayers,
            ServerStatus serverStatus,
            String serverName,
            List<ChatLogEntry> chatLogs,
            List<CommandLogEntry> commandLogs
    ) {
    }

    public record ChatLogEntry(String uuid, String username, String message, long timestamp, String server) {
    }

    public record CommandLogEntry(String uuid, String username, String command, long timestamp, String server) {
    }

    public record OnlinePlayer(
            @NotBlank @Pattern(regexp = RegExpConstants.UUID) String uuid,
            @NotBlank String username,
            String ipAddress
    ) {
    }

    public record ServerStatus(
            int onlinePlayerCount,
            int maxPlayers,
            String serverVersion,
            String timestamp
    ) {
    }
}
