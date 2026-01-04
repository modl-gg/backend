package gg.modl.backend.player.controller;

import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.dto.request.*;
import gg.modl.backend.player.dto.response.*;
import gg.modl.backend.player.service.AccountLinkingService;
import gg.modl.backend.player.service.PunishmentService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.validation.RegExpConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(RESTMappingV1.PANEL_PLAYERS)
@RequiredArgsConstructor
public class PanelPlayerController {
    private final PlayerService playerService;
    private final PunishmentService punishmentService;
    private final AccountLinkingService accountLinkingService;

    @GetMapping
    public ResponseEntity<List<PlayerSearchResult>> searchPlayers(
            @RequestParam @Size(min = 2) String search,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<PlayerSearchResult> results = playerService.searchPlayers(server, search);

        return ResponseEntity.ok(results);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<PlayerDetailResponse> getPlayer(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return playerService.getPlayerDetails(server, UUID.fromString(uuid))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SimpleResponse> createPlayer(
            @RequestBody @Valid CreatePlayerRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        playerService.createPlayer(
                server,
                UUID.fromString(createRequest.minecraftUuid()),
                createRequest.username()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(new SimpleResponse(true));
    }

    @PostMapping("/{uuid}/usernames")
    public ResponseEntity<SimpleResponse> addUsername(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            @RequestBody @Valid AddUsernameRequest addRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        Player player = playerService.addUsername(server, UUID.fromString(uuid), addRequest.username());
        if (player == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new SimpleResponse(true));
    }

    @PostMapping("/{uuid}/notes")
    public ResponseEntity<SimpleResponse> addNote(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            @RequestBody @Valid AddNoteRequest addRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        Player player = playerService.addNote(
                server,
                UUID.fromString(uuid),
                addRequest.text(),
                addRequest.issuerName(),
                addRequest.issuerId()
        );
        if (player == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new SimpleResponse(true));
    }

    @PostMapping("/{uuid}/ips")
    public ResponseEntity<SimpleResponse> addIp(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            @RequestBody @Valid AddIpRequest addRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        Player player = playerService.addIp(server, UUID.fromString(uuid), addRequest.ipAddress());
        if (player == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new SimpleResponse(true));
    }

    @PostMapping("/{uuid}/punishments")
    public ResponseEntity<SimpleResponse> createPunishment(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            @RequestBody @Valid CreatePunishmentRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        Player player = punishmentService.createPunishment(server, UUID.fromString(uuid), createRequest);
        if (player == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new SimpleResponse(true));
    }

    @PostMapping("/{uuid}/punishments/{punishmentId}/modifications")
    public ResponseEntity<SimpleResponse> addModification(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            @PathVariable String punishmentId,
            @RequestBody @Valid AddModificationRequest modRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        Player player = punishmentService.addModification(
                server,
                UUID.fromString(uuid),
                punishmentId,
                modRequest
        );
        if (player == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new SimpleResponse(true));
    }

    @GetMapping("/{uuid}/punishments/active")
    public ResponseEntity<List<PunishmentResponse>> getActivePunishments(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentResponse> punishments = punishmentService.getActivePunishments(
                server,
                UUID.fromString(uuid)
        );

        return ResponseEntity.ok(punishments);
    }

    @GetMapping("/punishments/{punishmentId}")
    public ResponseEntity<PunishmentResponse> getPunishmentById(
            @PathVariable String punishmentId,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return punishmentService.getPunishmentById(server, punishmentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/punishments/search")
    public ResponseEntity<List<PunishmentSearchResult>> searchPunishments(
            @RequestParam @Size(min = 2) String q,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentSearchResult> results = punishmentService.searchPunishments(server, q, activeOnly);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/{uuid}/punishments/{punishmentId}/notes")
    public ResponseEntity<SimpleResponse> addPunishmentNote(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            @PathVariable String punishmentId,
            @RequestBody @Valid AddPunishmentNoteRequest noteRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        Player player = punishmentService.addPunishmentNote(
                server,
                UUID.fromString(uuid),
                punishmentId,
                noteRequest.text(),
                noteRequest.issuerName()
        );
        if (player == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new SimpleResponse(true));
    }

    @PostMapping("/{uuid}/punishments/{punishmentId}/evidence")
    public ResponseEntity<SimpleResponse> addEvidence(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            @PathVariable String punishmentId,
            @RequestBody @Valid AddEvidenceRequest evidenceRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        Player player = punishmentService.addEvidence(
                server,
                UUID.fromString(uuid),
                punishmentId,
                evidenceRequest
        );
        if (player == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new SimpleResponse(true));
    }

    @GetMapping("/{uuid}/linked")
    public ResponseEntity<List<LinkedAccountResponse>> getLinkedAccounts(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<LinkedAccountResponse> linkedAccounts = accountLinkingService.getLinkedAccounts(
                server,
                UUID.fromString(uuid)
        );

        return ResponseEntity.ok(linkedAccounts);
    }

    @PostMapping("/{uuid}/find-linked")
    public ResponseEntity<Map<String, Object>> findAndLinkAccounts(
            @PathVariable @Pattern(regexp = RegExpConstants.UUID) String uuid,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        AccountLinkingService.LinkingResult result = accountLinkingService.findAndLinkAccounts(
                server,
                UUID.fromString(uuid)
        );

        return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "message", result.message(),
                "linkedAccountsFound", result.linkedAccountsFound()
        ));
    }

    @Data
    @AllArgsConstructor
    public static final class SimpleResponse {
        boolean success;
    }
}
