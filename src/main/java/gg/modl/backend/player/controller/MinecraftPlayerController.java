package gg.modl.backend.player.controller;

import gg.modl.backend.player.dto.request.CreateNoteRequest;
import gg.modl.backend.player.dto.response.CreateNoteResult;
import gg.modl.backend.player.dto.response.LinkedAccountsResult;
import gg.modl.backend.player.dto.response.PaginatedNotesResult;
import gg.modl.backend.player.dto.response.PaginatedPunishmentsResult;
import gg.modl.backend.player.dto.response.PardonResult;
import gg.modl.backend.player.dto.response.PlayerFetchResult;
import gg.modl.backend.player.dto.response.PlayerLookupResult;
import gg.modl.backend.player.dto.response.PlayerLoginResult;
import gg.modl.backend.player.dto.response.PlayerProfileResult;
import gg.modl.backend.player.service.MinecraftPlayerService;
import gg.modl.backend.player.service.PlayerLookupService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.infrastructure.validation.RegExpConstants;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.infrastructure.validation.ValidIpAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_PLAYERS)
@RequiredArgsConstructor
@Validated
public class MinecraftPlayerController {
    private final MinecraftPlayerService minecraftPlayerService;
    private final PlayerLookupService playerLookupService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
        @RequestBody @Valid LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PlayerLoginResult result = minecraftPlayerService.login(
            server,
            UUID.fromString(request.minecraftUUID()),
            request.username(),
            request.ip(),
            request.ipInfo(),
            request.skinHash(),
            request.serverName()
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", result.status());
        body.put("activePunishments", result.activePunishments());
        body.put("pendingNotifications", result.pendingNotifications());
        if (!result.pendingIpLookups().isEmpty()) {
            body.put("pendingIpLookups", result.pendingIpLookups());
        }
        if (!result.pendingStatWipes().isEmpty()) {
            body.put("pendingStatWipes", result.pendingStatWipes());
        }
        return ResponseEntity.status(result.status()).body(body);
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(
        @RequestBody @Valid DisconnectRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        boolean success = minecraftPlayerService.disconnect(server, request.minecraftUuid(), request.sessionDurationMs()).success();
        return ResponseEntity.ok(successBody(success));
    }

    @PostMapping("/update-server")
    public ResponseEntity<Map<String, Object>> updateServer(
        @RequestBody @Valid UpdateServerRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        boolean success = minecraftPlayerService.updateServer(server, request.minecraftUuid(), request.serverName()).success();
        return ResponseEntity.ok(successBody(success));
    }

    @GetMapping("/online")
    public ResponseEntity<Map<String, Object>> getOnlinePlayers(HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return ResponseEntity.ok(Map.of(
            "status", 200,
            "players", minecraftPlayerService.getOnlinePlayers(server).players()
        ));
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<Map<String, Object>> getPlayerByUuid(
        @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
        @RequestParam(required = false) Integer punishmentLimit,
        @RequestParam(required = false) Integer noteLimit,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return renderProfile(playerLookupService.getPlayerByUuid(server, uuid, punishmentLimit, noteLimit));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPlayerByQuery(
        @RequestParam(required = false) @Pattern(regexp = RegExpConstants.UUID) String minecraftUuid,
        @RequestParam(defaultValue = "true") boolean queryMojang,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return renderFetch(playerLookupService.getPlayerByMinecraftUuid(server, minecraftUuid, queryMojang));
    }

    @GetMapping("/by-name")
    public ResponseEntity<Map<String, Object>> getPlayerByUsername(
        @RequestParam String username,
        @RequestParam(defaultValue = "true") boolean queryMojang,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return renderFetch(playerLookupService.getPlayerByUsername(server, username, queryMojang));
    }

    @PostMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookupPlayer(
        @RequestBody @Valid LookupRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return switch (playerLookupService.lookupPlayer(server, request.query(), request.shouldQueryMojang())) {
            case PlayerLookupResult.Found found -> ResponseEntity.ok(Map.of(
                "status", 200,
                "message", found.message(),
                "data", found.data()
            ));
            case PlayerLookupResult.NotFound notFound -> notFound(notFound.message());
        };
    }

    @PostMapping("/lookup-profile")
    public ResponseEntity<Map<String, Object>> lookupProfile(
        @RequestBody @Valid LookupRequest request,
        @RequestParam(required = false) Integer punishmentLimit,
        @RequestParam(required = false) Integer noteLimit,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return renderProfile(playerLookupService.lookupProfile(server, request.query(), request.shouldQueryMojang(),
            punishmentLimit, noteLimit));
    }

    @PostMapping("/{uuid}/notes")
    public ResponseEntity<Map<String, Object>> createPlayerNote(
        @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
        @RequestBody @Valid CreateNoteRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return switch (minecraftPlayerService.createNote(server, uuid, request.text(), request.issuerName(),
            request.issuerId())) {
            case CreateNoteResult.Created created -> ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", created.message()
            ));
            case CreateNoteResult.NotFound notFound -> notFound(notFound.message());
        };
    }

    @GetMapping("/{uuid}/linked-accounts")
    public ResponseEntity<Map<String, Object>> getLinkedAccounts(
        @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
        @RequestParam(required = false) @Min(RequestValidationLimits.PAGINATION_PAGE_MIN) Integer page,
        @RequestParam(required = false) @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) Integer limit,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return switch (playerLookupService.getLinkedAccounts(server, uuid, page, limit)) {
            case LinkedAccountsResult.Found found -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", 200);
                body.put("linkedAccounts", found.linkedAccounts());
                if (found.totalCount() != null) {
                    body.put("totalCount", found.totalCount());
                    body.put("page", found.page());
                    body.put("hasMore", found.hasMore());
                }
                yield ResponseEntity.ok(body);
            }
            case LinkedAccountsResult.NotFound notFound -> notFound(notFound.message());
        };
    }

    @GetMapping("/{uuid}/punishments")
    public ResponseEntity<Map<String, Object>> getPlayerPunishments(
        @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
        @RequestParam(defaultValue = "1") @Min(RequestValidationLimits.PAGINATION_PAGE_MIN) int page,
        @RequestParam(defaultValue = "7") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return switch (minecraftPlayerService.getPlayerPunishments(server, uuid, page, limit)) {
            case PaginatedPunishmentsResult.Found found -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", 200);
                body.put("punishments", found.punishments());
                body.put("totalCount", found.totalCount());
                body.put("page", found.page());
                body.put("hasMore", found.hasMore());
                yield ResponseEntity.ok(body);
            }
            case PaginatedPunishmentsResult.NotFound notFound -> notFound(notFound.message());
        };
    }

    @GetMapping("/{uuid}/notes")
    public ResponseEntity<Map<String, Object>> getPlayerNotes(
        @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
        @RequestParam(defaultValue = "1") @Min(RequestValidationLimits.PAGINATION_PAGE_MIN) int page,
        @RequestParam(defaultValue = "7") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return switch (minecraftPlayerService.getPlayerNotes(server, uuid, page, limit)) {
            case PaginatedNotesResult.Found found -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", 200);
                body.put("notes", found.notes());
                body.put("totalCount", found.totalCount());
                body.put("page", found.page());
                body.put("hasMore", found.hasMore());
                yield ResponseEntity.ok(body);
            }
            case PaginatedNotesResult.NotFound notFound -> notFound(notFound.message());
        };
    }

    @GetMapping("/{uuid}/reports")
    public ResponseEntity<Map<String, Object>> getPlayerReports(
        @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return ResponseEntity.ok(Map.of(
            "status", 200,
            "reports", minecraftPlayerService.getPlayerReports(server, uuid).reports()
        ));
    }

    @PostMapping("/submit-ip-info")
    public ResponseEntity<Map<String, Object>> submitIpInfo(
        @RequestBody @Valid SubmitIpInfoRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        boolean success = minecraftPlayerService.submitIpInfo(
            server,
            request.minecraftUUID(),
            request.ip(),
            request.country(),
            request.region(),
            request.asn(),
            request.proxy(),
            request.hosting()
        ).success();
        return ResponseEntity.ok(successBody(success));
    }

    @PostMapping("/pardon")
    public ResponseEntity<Map<String, Object>> pardonPlayer(
        @RequestBody @Valid PardonPlayerRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return switch (minecraftPlayerService.pardonPlayer(
            server,
            request.playerName(),
            request.punishmentType(),
            request.issuerName(),
            request.issuerId(),
            request.reason()
        )) {
            case PardonResult.Pardoned pardoned -> ResponseEntity.ok(Map.of(
                "status", 200,
                "success", pardoned.success(),
                "pardonedCount", pardoned.pardonedCount(),
                "message", pardoned.message()
            ));
            case PardonResult.PlayerNotFound notFound -> notFound(notFound.message());
        };
    }

    private ResponseEntity<Map<String, Object>> renderProfile(PlayerProfileResult result) {
        return switch (result) {
            case PlayerProfileResult.Found found -> ResponseEntity.ok(Map.of(
                "status", 200,
                "profile", found.profile()
            ));
            case PlayerProfileResult.NotFound notFound -> notFound(notFound.message());
        };
    }

    private ResponseEntity<Map<String, Object>> renderFetch(PlayerFetchResult result) {
        return switch (result) {
            case PlayerFetchResult.Found found -> ResponseEntity.ok(Map.of(
                "status", 200,
                "message", found.message(),
                "player", found.player()
            ));
            case PlayerFetchResult.NotFound notFound -> notFound(notFound.message());
            case PlayerFetchResult.InvalidRequest invalid -> ResponseEntity.status(400).body(Map.of(
                "status", 400,
                "message", invalid.message()
            ));
        };
    }

    private ResponseEntity<Map<String, Object>> notFound(String message) {
        return ResponseEntity.status(404).body(Map.of(
            "status", 404,
            "message", message
        ));
    }

    private Map<String, Object> successBody(boolean success) {
        return Map.of("status", 200, "success", success);
    }

    public record LoginRequest(
        @NotBlank @Pattern(regexp = RegExpConstants.UUID) String minecraftUUID,
        @NotBlank @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String username,
        @NotBlank @ValidIpAddress String ip,
        Map<String, Object> ipInfo,
        @Size(max = RequestValidationLimits.ID_MAX_LENGTH) String skinHash,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String serverName,
        String serverInstanceId
    ) {
    }

    public record SubmitIpInfoRequest(
        @NotBlank @Pattern(regexp = RegExpConstants.UUID) String minecraftUUID,
        @NotBlank @ValidIpAddress String ip,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String country,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String region,
        @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String asn,
        boolean proxy,
        boolean hosting
    ) {
    }

    public record DisconnectRequest(
        @NotBlank @Pattern(regexp = RegExpConstants.UUID) String minecraftUuid,
        @Min(0) long sessionDurationMs,
        String serverInstanceId
    ) {
    }

    public record UpdateServerRequest(
        @NotBlank @Pattern(regexp = RegExpConstants.UUID) String minecraftUuid,
        @NotBlank @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH) String serverName,
        String serverInstanceId
    ) {
    }

    public record LookupRequest(
        @NotBlank @Size(max = RequestValidationLimits.ADMIN_SEARCH_QUERY_MAX_LENGTH) String query,
        Boolean queryMojang
    ) {
        public boolean shouldQueryMojang() {
            return queryMojang == null || queryMojang;
        }
    }

    public record PardonPlayerRequest(
        @NotBlank @Size(max = RequestValidationLimits.LOG_USERNAME_MAX_LENGTH) String playerName,
        @Size(max = RequestValidationLimits.PLAYER_ISSUER_NAME_MAX_LENGTH) String issuerName,
        @Size(max = RequestValidationLimits.PLAYER_ISSUER_ID_MAX_LENGTH) String issuerId,
        @Size(max = RequestValidationLimits.PUNISHMENT_TYPE_NAME_MAX_LENGTH) String punishmentType,
        @Size(max = RequestValidationLimits.PLAYER_MODIFICATION_REASON_MAX_LENGTH) String reason
    ) {
    }
}
