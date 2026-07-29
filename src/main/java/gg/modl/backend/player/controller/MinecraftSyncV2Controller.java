package gg.modl.backend.player.controller;

import gg.modl.backend.player.controller.MinecraftSyncController.ChatLogEntry;
import gg.modl.backend.player.controller.MinecraftSyncController.CommandLogEntry;
import gg.modl.backend.player.controller.MinecraftSyncController.OnlinePlayer;
import gg.modl.backend.player.service.MinecraftSyncService;
import gg.modl.backend.player.dto.response.SyncResult;
import gg.modl.backend.infrastructure.rest.RESTMappingV2;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV2.MINECRAFT_PLAYERS)
@RequiredArgsConstructor
public class MinecraftSyncV2Controller {
    private final MinecraftSyncService minecraftSyncService;

    @PostMapping("/sync")
    public ResponseEntity<SyncResult> sync(
        @RequestBody @Valid V2SyncRequest syncRequest,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        String clientIp = RequestUtil.getClientIp(httpRequest);
        return ResponseEntity.ok(minecraftSyncService.syncV2(
            server,
            syncRequest.lastSyncTimestamp(),
            SyncRequestMapper.toOnlinePlayers(syncRequest.onlinePlayers()),
            syncRequest.serverName(),
            SyncRequestMapper.toChatLogs(syncRequest.chatLogs()),
            SyncRequestMapper.toCommandLogs(syncRequest.commandLogs()),
            clientIp
        ));
    }

    public record V2SyncRequest(
        @Size(max = RequestValidationLimits.TIMESTAMP_MAX_LENGTH) String lastSyncTimestamp,
        @Size(max = RequestValidationLimits.CHAT_LOG_BATCH_MAX_ENTRIES) List<@Valid OnlinePlayer> onlinePlayers,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String serverName,
        @Size(max = RequestValidationLimits.CHAT_LOG_BATCH_MAX_ENTRIES) List<@Valid ChatLogEntry> chatLogs,
        @Size(max = RequestValidationLimits.CHAT_LOG_BATCH_MAX_ENTRIES) List<@Valid CommandLogEntry> commandLogs,
        String serverInstanceId
    ) {
    }
}
