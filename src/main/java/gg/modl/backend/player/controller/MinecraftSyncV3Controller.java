package gg.modl.backend.player.controller;

import gg.modl.backend.infrastructure.proto.ProtobufMediaTypes;
import gg.modl.backend.infrastructure.rest.RESTMappingV3;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.player.service.MinecraftSyncService;
import gg.modl.backend.player.service.SyncProtoFactory;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.SimpleResponse;
import gg.modl.proto.modl.v1.SyncRequest;
import gg.modl.proto.modl.v1.SyncResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV3.PREFIX_MINECRAFT)
@RequiredArgsConstructor
public class MinecraftSyncV3Controller {
    private final MinecraftSyncService minecraftSyncService;
    private final SyncProtoFactory syncProtoFactory;

    @PostMapping(
        value = "/players/sync",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<SyncResponse> sync(
        @RequestBody @Valid SyncRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        String clientIp = RequestUtil.getClientIp(httpRequest);
        Map<String, Object> response = minecraftSyncService.sync(
            server,
            request.getLastSyncTimestamp(),
            MinecraftSyncProtoMapper.toOnlinePlayers(request),
            request.hasServerName() ? request.getServerName() : null,
            MinecraftSyncProtoMapper.toChatLogs(request),
            MinecraftSyncProtoMapper.toCommandLogs(request),
            MinecraftSyncProtoMapper.toServerStatus(request),
            clientIp
        );

        return ResponseEntity.ok(syncProtoFactory.toSyncResponse(response));
    }

    @PostMapping(
        value = "/presence",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<SimpleResponse> presence(
        @RequestBody @Valid SyncRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        minecraftSyncService.applyPresence(
            server,
            MinecraftSyncProtoMapper.toOnlinePlayers(request),
            request.hasServerName() ? request.getServerName() : null,
            Instant.now()
        );

        return ResponseEntity.ok(SimpleResponse.newBuilder().setSuccess(true).build());
    }

    @PostMapping(
        value = "/logs",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<SimpleResponse> logs(
        @RequestBody @Valid SyncRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        minecraftSyncService.submitLogs(
            server,
            MinecraftSyncProtoMapper.toChatLogs(request),
            MinecraftSyncProtoMapper.toCommandLogs(request)
        );

        return ResponseEntity.ok(SimpleResponse.newBuilder().setSuccess(true).build());
    }

    @PostMapping(
        value = "/status",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<SimpleResponse> status(
        @RequestBody @Valid SyncRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        String clientIp = RequestUtil.getClientIp(httpRequest);
        minecraftSyncService.applyServerStatus(
            server,
            MinecraftSyncProtoMapper.toServerStatus(request),
            request.hasServerName() ? request.getServerName() : null,
            clientIp,
            Instant.now()
        );

        return ResponseEntity.ok(SimpleResponse.newBuilder().setSuccess(true).build());
    }
}
