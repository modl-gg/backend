package gg.modl.backend.player.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.player.dto.request.CreateUploadTokenRequest;
import gg.modl.backend.player.dto.request.ModifyPunishmentTicketsRequest;
import gg.modl.backend.player.dto.response.PunishmentPreviewResponse;
import gg.modl.backend.player.service.PunishmentEvidenceService;
import gg.modl.backend.player.service.PunishmentLifecycleService;
import gg.modl.backend.player.service.PunishmentMutationService;
import gg.modl.backend.player.service.PunishmentQueryService;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.validation.RegExpConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_PUNISHMENTS)
@RequiredArgsConstructor
public class MinecraftPunishmentController {
    private final PunishmentQueryService punishmentQueryService;
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final PunishmentEvidenceService punishmentEvidenceService;
    private final PunishmentMutationService punishmentMutationService;

    @PostMapping("/create")
    public ResponseEntity<?> createPunishment(
            @RequestBody @Valid MinecraftCreatePunishmentRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        try {
            createPunishmentInternal(server, request);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dynamic")
    public ResponseEntity<Map<String, Object>> createPunishmentDynamic(
            @RequestBody @Valid MinecraftCreatePunishmentRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        try {
            String punishmentId = createPunishmentInternal(server, request);
            return ResponseEntity.ok(Map.of(
                    "status", 200,
                    "message", "Punishment created",
                    "punishmentId", punishmentId
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }
    }

    @GetMapping("/{punishmentId}")
    public ResponseEntity<Map<String, Object>> getPunishmentById(
            @PathVariable String punishmentId,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        Map<String, Object> punishment = punishmentQueryService.getMinecraftPunishmentById(server, punishmentId).orElse(null);
        if (punishment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Punishment not found"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "punishment", punishment
        ));
    }

    @PostMapping("/{punishmentId}/upload-token")
    public ResponseEntity<Map<String, Object>> createUploadToken(
            @PathVariable String punishmentId,
            @RequestBody @Valid CreateUploadTokenRequest body,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        String token = punishmentQueryService.createEvidenceUploadToken(server, punishmentId, body.issuerName()).orElse(null);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Punishment not found"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "token", token
        ));
    }

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentPunishments(
            @RequestParam(defaultValue = "48") int hours,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return ResponseEntity.ok(Map.of(
                "status", 200,
                "punishments", punishmentQueryService.getRecentPunishments(server, hours)
        ));
    }

    @GetMapping("/preview")
    public ResponseEntity<PunishmentPreviewResponse> previewPunishment(
            @RequestParam String playerUuid,
            @RequestParam int typeOrdinal,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        return ResponseEntity.ok(punishmentQueryService.previewPunishment(server, playerUuid, typeOrdinal));
    }

    @PostMapping("/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgePunishment(
            @RequestBody @Valid AcknowledgeRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentLifecycleService.acknowledgePunishment(
                server,
                UUID.fromString(request.playerUuid()),
                request.punishmentId()
        );

        return switch (result.status()) {
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", result.message()
            ));
            case INVALID_REQUEST -> ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", result.message()
            ));
            case NO_OP, SUCCESS -> ResponseEntity.ok(Map.of(
                    "status", 200,
                    "message", result.message()
            ));
        };
    }

    @PostMapping("/{punishmentId}/pardon")
    public ResponseEntity<Map<String, Object>> pardonPunishment(
            @PathVariable String punishmentId,
            @RequestBody @Valid PardonRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentLifecycleService.pardonPunishment(
                server,
                punishmentId,
                request.issuerName(),
                request.issuerId(),
                request.reason()
        );

        return switch (result.status()) {
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", result.message()
            ));
            case INVALID_REQUEST -> ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", result.message()
            ));
            case NO_OP -> ResponseEntity.ok(Map.of(
                    "status", 200,
                    "success", false,
                    "pardonedCount", 0,
                    "message", result.message()
            ));
            case SUCCESS -> ResponseEntity.ok(Map.of(
                    "status", 200,
                    "success", true,
                    "pardonedCount", 1,
                    "message", result.message()
            ));
        };
    }

    @PostMapping("/{punishmentId}/note")
    public ResponseEntity<Map<String, Object>> addNote(
            @PathVariable String punishmentId,
            @RequestBody @Valid AddNoteRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentEvidenceService.addPunishmentNote(
                server,
                punishmentId,
                request.note(),
                request.issuerName(),
                request.issuerId()
        );

        if (result.status() == PunishmentOperationStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", result.message()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", result.message()
        ));
    }

    @PostMapping("/{punishmentId}/evidence")
    public ResponseEntity<Map<String, Object>> addEvidence(
            @PathVariable String punishmentId,
            @RequestBody @Valid AddEvidenceRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentEvidenceService.addEvidence(
                server,
                punishmentId,
                request.evidenceUrl(),
                request.issuerName(),
                request.issuerId()
        );

        if (result.status() == PunishmentOperationStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", result.message()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", result.message()
        ));
    }

    @PostMapping("/{punishmentId}/duration")
    public ResponseEntity<Map<String, Object>> changeDuration(
            @PathVariable String punishmentId,
            @RequestBody @Valid ChangeDurationRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentMutationService.changeDuration(
                server,
                punishmentId,
                request.newDuration(),
                request.issuerName(),
                request.issuerId()
        );

        if (result.status() == PunishmentOperationStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", result.message()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", result.message()
        ));
    }

    @PostMapping("/{punishmentId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleOption(
            @PathVariable String punishmentId,
            @RequestBody @Valid ToggleOptionRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentMutationService.toggleOption(
                server,
                punishmentId,
                request.option(),
                request.enabled(),
                request.issuerName(),
                request.issuerId()
        );

        return switch (result.status()) {
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", result.message()
            ));
            case INVALID_REQUEST -> ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", result.message()
            ));
            case NO_OP, SUCCESS -> ResponseEntity.ok(Map.of(
                    "status", 200,
                    "success", result.success(),
                    "message", result.message()
            ));
        };
    }

    @PostMapping("/{punishmentId}/stat-wipe-acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeStatWipe(
            @PathVariable String punishmentId,
            @RequestBody @Valid StatWipeAcknowledgeRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentMutationService.acknowledgeStatWipe(server, punishmentId);

        return switch (result.status()) {
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", result.message()
            ));
            case INVALID_REQUEST -> ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", result.message()
            ));
            case NO_OP -> ResponseEntity.ok(Map.of(
                    "status", 200,
                    "message", result.message()
            ));
            case SUCCESS -> ResponseEntity.ok(Map.of(
                    "status", 200,
                    "success", true,
                    "message", result.message()
            ));
        };
    }

    @PostMapping("/{punishmentId}/tickets")
    public ResponseEntity<Map<String, Object>> modifyPunishmentTickets(
            @PathVariable String punishmentId,
            @RequestBody @Valid ModifyTicketsRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentMutationService.modifyPunishmentTickets(
                server,
                punishmentId,
                new ModifyPunishmentTicketsRequest(
                        request.addTicketIds(),
                        request.removeTicketIds(),
                        request.modifyAssociatedTickets(),
                        request.issuerName(),
                        request.issuerId()
                )
        );

        if (result.status() == PunishmentOperationStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", result.message()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", result.message()
        ));
    }

    private String createPunishmentInternal(Server server, MinecraftCreatePunishmentRequest request) {
        UUID playerUuid = UUID.fromString(request.targetUuid());

        List<gg.modl.backend.player.dto.request.CreateNoteRequest> noteRequests = null;
        if (request.notes() != null) {
            noteRequests = request.notes().stream()
                    .map(text -> new gg.modl.backend.player.dto.request.CreateNoteRequest(text, request.issuerName(), request.issuerId(), null))
                    .toList();
        }

        Map<String, Object> data = request.data() != null ? new HashMap<>(request.data()) : new HashMap<>();
        data.put("pendingAcknowledgement", true);

        CreatePunishmentRequest serviceRequest = new CreatePunishmentRequest(
                request.issuerName(),
                request.issuerId(),
                request.typeOrdinal(),
                noteRequests,
                null,
                request.attachedTicketIds(),
                request.severity(),
                request.status(),
                data,
                request.reason(),
                request.duration()
        );

        return punishmentLifecycleService.createPunishment(server, playerUuid, serviceRequest);
    }

    public record MinecraftCreatePunishmentRequest(
            @NotBlank String targetUuid,
            String issuerName,
            String issuerId,
            @JsonProperty("type_ordinal") int typeOrdinal,
            String reason,
            Long duration,
            Map<String, Object> data,
            List<String> notes,
            List<String> attachedTicketIds,
            String severity,
            String status
    ) {
    }

    public record AcknowledgeRequest(
            @NotBlank String punishmentId,
            @NotBlank @Pattern(regexp = RegExpConstants.UUID) String playerUuid,
            String executedAt,
            boolean success,
            String errorMessage
    ) {
    }

    public record PardonRequest(
            String issuerName,
            String issuerId,
            String reason,
            String expectedType
    ) {
    }

    public record AddNoteRequest(
            String issuerName,
            String issuerId,
            @NotBlank String note
    ) {
    }

    public record AddEvidenceRequest(
            String issuerName,
            String issuerId,
            @NotBlank String evidenceUrl
    ) {
    }

    public record ChangeDurationRequest(
            String issuerName,
            String issuerId,
            Long newDuration
    ) {
    }

    public record ToggleOptionRequest(
            String issuerName,
            String issuerId,
            @NotBlank String option,
            boolean enabled
    ) {
    }

    public record StatWipeAcknowledgeRequest(
            @NotBlank String punishmentId,
            String serverName,
            boolean success
    ) {
    }

    public record ModifyTicketsRequest(
            String issuerName,
            String issuerId,
            List<String> addTicketIds,
            List<String> removeTicketIds,
            boolean modifyAssociatedTickets
    ) {
    }
}
