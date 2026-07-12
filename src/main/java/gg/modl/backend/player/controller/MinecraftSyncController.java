package gg.modl.backend.player.controller;

import gg.modl.backend.player.service.MinecraftSyncService;
import gg.modl.backend.player.dto.response.SyncResult;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.infrastructure.validation.RegExpConstants;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.infrastructure.validation.ValidIpAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_PLAYERS)
@RequiredArgsConstructor
public class MinecraftSyncController {
    private final MinecraftSyncService minecraftSyncService;

    @PostMapping("/sync")
    public ResponseEntity<SyncResult> sync(
        @RequestBody @Valid SyncRequest syncRequest,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        String clientIp = RequestUtil.getClientIp(httpRequest);
        return ResponseEntity.ok(minecraftSyncService.sync(
            server,
            syncRequest.lastSyncTimestamp(),
            SyncRequestMapper.toOnlinePlayers(syncRequest.onlinePlayers()),
            syncRequest.serverName(),
            SyncRequestMapper.toChatLogs(syncRequest.chatLogs()),
            SyncRequestMapper.toCommandLogs(syncRequest.commandLogs()),
            SyncRequestMapper.toServerStatus(syncRequest.serverStatus()),
            clientIp
        ));
    }

    public record SyncRequest(
        @Size(max = RequestValidationLimits.TIMESTAMP_MAX_LENGTH) String lastSyncTimestamp,
        @Size(max = RequestValidationLimits.CHAT_LOG_BATCH_MAX_ENTRIES) List<@Valid OnlinePlayer> onlinePlayers,
        @Valid ServerStatus serverStatus,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String serverName,
        @Size(max = RequestValidationLimits.CHAT_LOG_BATCH_MAX_ENTRIES) List<@Valid ChatLogEntry> chatLogs,
        @Size(max = RequestValidationLimits.CHAT_LOG_BATCH_MAX_ENTRIES) List<@Valid CommandLogEntry> commandLogs,
        String serverInstanceId
    ) {
    }

    public record ChatLogEntry(
        @NotBlank @Pattern(regexp = RegExpConstants.UUID) String uuid,
        @NotBlank @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String username,
        @NotBlank @Size(max = RequestValidationLimits.CHAT_LOG_MESSAGE_MAX_LENGTH) String message,
        @PositiveOrZero long timestamp,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String server
    ) {
    }

    public record CommandLogEntry(
        @NotBlank @Pattern(regexp = RegExpConstants.UUID) String uuid,
        @NotBlank @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String username,
        @NotBlank @Size(max = RequestValidationLimits.COMMAND_LOG_MAX_LENGTH) String command,
        @PositiveOrZero long timestamp,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String server
    ) {
    }

    public record OnlinePlayer(
        @NotBlank @Pattern(regexp = RegExpConstants.UUID) String uuid,
        @NotBlank @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String username,
        @ValidIpAddress String ipAddress
    ) {
    }

    public record ServerStatus(
        @Min(0) int onlinePlayerCount,
        @Min(0) int maxPlayers,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String serverVersion,
        @Size(max = RequestValidationLimits.TIMESTAMP_MAX_LENGTH) String timestamp,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String platformType,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String pluginVersion
    ) {
    }
}
