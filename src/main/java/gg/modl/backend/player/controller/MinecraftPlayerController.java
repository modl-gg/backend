package gg.modl.backend.player.controller;

import gg.modl.backend.player.service.MinecraftPlayerService;
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
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_PLAYERS)
@RequiredArgsConstructor
@Slf4j
public class MinecraftPlayerController {
    private final MinecraftPlayerService minecraftPlayerService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftPlayerService.ServiceResponse response = minecraftPlayerService.login(
                server,
                UUID.fromString(request.minecraftUUID()),
                request.username(),
                request.ip(),
                request.ipInfo(),
                request.skinHash(),
                request.serverName()
        );
        return ResponseEntity.status(response.status()).body(response.body());
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(
            @RequestBody @Valid DisconnectRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return ResponseEntity.ok(minecraftPlayerService.disconnect(server, request.minecraftUuid(), request.sessionDurationMs()));
    }

    @PostMapping("/update-server")
    public ResponseEntity<Map<String, Object>> updateServer(
            @RequestBody @Valid UpdateServerRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return ResponseEntity.ok(minecraftPlayerService.updateServer(server, request.minecraftUuid(), request.serverName()));
    }

    @GetMapping("/online")
    public ResponseEntity<Map<String, Object>> getOnlinePlayers(HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return ResponseEntity.ok(minecraftPlayerService.getOnlinePlayers(server));
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<Map<String, Object>> getPlayerByUuid(
            @PathVariable String uuid,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftPlayerService.ServiceResponse response = minecraftPlayerService.getPlayerByUuid(server, uuid);
        return ResponseEntity.status(response.status()).body(response.body());
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPlayerByQuery(
            @RequestParam(required = false) String minecraftUuid,
            @RequestParam(defaultValue = "true") boolean queryMojang,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftPlayerService.ServiceResponse response = minecraftPlayerService.getPlayerByMinecraftUuid(server, minecraftUuid, queryMojang);
        return ResponseEntity.status(response.status()).body(response.body());
    }

    @GetMapping("/by-name")
    public ResponseEntity<Map<String, Object>> getPlayerByUsername(
            @RequestParam String username,
            @RequestParam(defaultValue = "true") boolean queryMojang,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftPlayerService.ServiceResponse response = minecraftPlayerService.getPlayerByUsername(server, username, queryMojang);
        return ResponseEntity.status(response.status()).body(response.body());
    }

    @PostMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookupPlayer(
            @RequestBody @Valid LookupRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftPlayerService.ServiceResponse response = minecraftPlayerService.lookupPlayer(server, request.query(), request.shouldQueryMojang());
        return ResponseEntity.status(response.status()).body(response.body());
    }

    @PostMapping("/{uuid}/notes")
    public ResponseEntity<Map<String, Object>> createPlayerNote(
            @PathVariable String uuid,
            @RequestBody @Valid CreateNoteRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftPlayerService.ServiceResponse response = minecraftPlayerService.createNote(server, uuid, request.text(), request.issuerName(), request.issuerId());
        return ResponseEntity.status(response.status()).body(response.body());
    }

    @GetMapping("/{uuid}/linked-accounts")
    public ResponseEntity<Map<String, Object>> getLinkedAccounts(
            @PathVariable String uuid,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftPlayerService.ServiceResponse response = minecraftPlayerService.getLinkedAccounts(server, uuid);
        return ResponseEntity.status(response.status()).body(response.body());
    }

    @GetMapping("/{uuid}/reports")
    public ResponseEntity<Map<String, Object>> getPlayerReports(
            @PathVariable String uuid,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return ResponseEntity.ok(minecraftPlayerService.getPlayerReports(server, uuid));
    }

    @PostMapping("/submit-ip-info")
    public ResponseEntity<Map<String, Object>> submitIpInfo(
            @RequestBody @Valid SubmitIpInfoRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return ResponseEntity.ok(minecraftPlayerService.submitIpInfo(
                server,
                request.minecraftUUID(),
                request.ip(),
                request.country(),
                request.region(),
                request.asn(),
                request.proxy(),
                request.hosting()
        ));
    }

    @PostMapping("/pardon")
    public ResponseEntity<Map<String, Object>> pardonPlayer(
            @RequestBody @Valid PardonPlayerRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        Map<String, Object> response = minecraftPlayerService.pardonPlayer(
                server,
                request.playerName(),
                request.punishmentType(),
                request.issuerName(),
                request.issuerId(),
                request.reason()
        );

        if (Objects.equals(response.get("status"), 404)) {
            return ResponseEntity.status(404).body(response);
        }
        return ResponseEntity.ok(response);
    }

    public record LoginRequest(
            @Pattern(regexp = RegExpConstants.UUID) String minecraftUUID,
            @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String username,
            @Pattern(regexp = RegExpConstants.IP) String ip,
            Map<String, Object> ipInfo,
            String skinHash,
            String serverName
    ) {
    }

    public record SubmitIpInfoRequest(
            @Pattern(regexp = RegExpConstants.UUID) String minecraftUUID,
            @Pattern(regexp = RegExpConstants.IP) String ip,
            String country,
            String region,
            String asn,
            boolean proxy,
            boolean hosting
    ) {
    }

    public record DisconnectRequest(
            @NotBlank @Pattern(regexp = RegExpConstants.UUID) String minecraftUuid,
            long sessionDurationMs
    ) {
    }

    public record UpdateServerRequest(
            @NotBlank @Pattern(regexp = RegExpConstants.UUID) String minecraftUuid,
            @NotBlank String serverName
    ) {
    }

    public record LookupRequest(@NotBlank String query, Boolean queryMojang) {
        public boolean shouldQueryMojang() {
            return queryMojang == null || queryMojang;
        }
    }

    public record CreateNoteRequest(
            @NotBlank String text,
            String issuerName,
            String issuerId
    ) {
    }

    public record PardonPlayerRequest(
            @NotBlank String playerName,
            String issuerName,
            String issuerId,
            String punishmentType,
            String reason
    ) {
    }
}
