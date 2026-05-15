package gg.modl.backend.player.controller;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import gg.modl.backend.infrastructure.proto.ProtobufMediaTypes;
import gg.modl.backend.infrastructure.rest.RESTMappingV3;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.player.dto.response.PunishmentPreviewView;
import gg.modl.backend.player.dto.response.PunishmentSeverityPreviewView;
import gg.modl.backend.player.service.PunishmentEvidenceService;
import gg.modl.backend.player.service.PunishmentLifecycleService;
import gg.modl.backend.player.service.PunishmentMutationService;
import gg.modl.backend.player.service.PunishmentQueryService;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.AddPunishmentEvidenceRequest;
import gg.modl.proto.modl.v1.AddPunishmentEvidenceResponse;
import gg.modl.proto.modl.v1.AddPunishmentNoteRequest;
import gg.modl.proto.modl.v1.AddPunishmentNoteResponse;
import gg.modl.proto.modl.v1.ApiError;
import gg.modl.proto.modl.v1.ChangePunishmentDurationRequest;
import gg.modl.proto.modl.v1.ChangePunishmentDurationResponse;
import gg.modl.proto.modl.v1.CreatePunishmentRequest;
import gg.modl.proto.modl.v1.CreateEvidenceUploadTokenRequest;
import gg.modl.proto.modl.v1.EvidenceUploadTokenResponse;
import gg.modl.proto.modl.v1.ModifyPunishmentTicketsRequest;
import gg.modl.proto.modl.v1.ModifyPunishmentTicketsResponse;
import gg.modl.proto.modl.v1.PardonPunishmentRequest;
import gg.modl.proto.modl.v1.PardonResponse;
import gg.modl.proto.modl.v1.PunishmentAcknowledgeRequest;
import gg.modl.proto.modl.v1.PunishmentAcknowledgeResponse;
import gg.modl.proto.modl.v1.PunishmentCreateResponse;
import gg.modl.proto.modl.v1.PunishmentEvidence;
import gg.modl.proto.modl.v1.PunishmentModification;
import gg.modl.proto.modl.v1.PunishmentNote;
import gg.modl.proto.modl.v1.PunishmentDetailResponse;
import gg.modl.proto.modl.v1.PunishmentPreviewResponse;
import gg.modl.proto.modl.v1.RecentPunishmentsResponse;
import gg.modl.proto.modl.v1.StatWipeAcknowledgeRequest;
import gg.modl.proto.modl.v1.StatWipeAcknowledgeResponse;
import gg.modl.proto.modl.v1.TogglePunishmentOptionRequest;
import gg.modl.proto.modl.v1.TogglePunishmentOptionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping(RESTMappingV3.PREFIX_MINECRAFT + "/punishments")
@RequiredArgsConstructor
@Validated
public class MinecraftPunishmentV3Controller {
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final PunishmentMutationService punishmentMutationService;
    private final PunishmentEvidenceService punishmentEvidenceService;
    private final PunishmentQueryService punishmentQueryService;

    @GetMapping(value = "/preview", produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE)
    public ResponseEntity<PunishmentPreviewResponse> previewPunishment(
        @RequestParam String playerUuid,
        @RequestParam int typeOrdinal,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentPreviewView preview = punishmentQueryService.previewPunishment(server, playerUuid, typeOrdinal);

        return ResponseEntity.ok(toProto(preview));
    }

    @GetMapping(value = "/recent", produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE)
    public ResponseEntity<RecentPunishmentsResponse> getRecentPunishments(
        @RequestParam(defaultValue = "48") @Min(1) @Max(8760) int hours,
        HttpServletRequest httpRequest
    ) {
        validateRecentHours(hours);
        Server server = RequestUtil.getRequestServer(httpRequest);
        RecentPunishmentsResponse.Builder response = RecentPunishmentsResponse.newBuilder()
            .setStatus(200);
        punishmentQueryService.getRecentPunishments(server, hours).stream()
            .map(this::toRecentPunishment)
            .forEach(response::addPunishments);

        return ResponseEntity.ok(response.build());
    }

    @GetMapping(value = "/{punishmentId}", produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE)
    public ResponseEntity<?> getPunishmentById(
        @PathVariable String punishmentId,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        Map<String, Object> punishment = punishmentQueryService
            .getMinecraftPunishmentById(server, punishmentId)
            .orElse(null);

        if (punishment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .body(ApiError.newBuilder()
                    .setStatusCode(404)
                    .setCode("NOT_FOUND")
                    .setMessage("Punishment not found")
                    .build());
        }

        return ResponseEntity.ok(PunishmentDetailResponse.newBuilder()
            .setStatus(200)
            .setPunishment(toPunishmentDetail(punishment))
            .build());
    }

    @PostMapping(
        value = "/dynamic",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<PunishmentCreateResponse> createPunishmentDynamic(
        @RequestBody @Valid CreatePunishmentRequest request,
        HttpServletRequest httpRequest
    ) {
        validateCreatePunishmentData(request);
        Server server = RequestUtil.getRequestServer(httpRequest);
        String punishmentId = punishmentLifecycleService.createMinecraftPunishment(
            server,
            toLegacyCreatePunishmentRequest(request)
        );

        return ResponseEntity.ok(PunishmentCreateResponse.newBuilder()
            .setStatus(200)
            .setMessage("Punishment created")
            .setPunishmentId(punishmentId)
            .build());
    }

    @PostMapping(
        value = "/create",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<Empty> createPunishment(
        @RequestBody @Valid CreatePunishmentRequest request,
        HttpServletRequest httpRequest
    ) {
        validateCreatePunishmentData(request);
        Server server = RequestUtil.getRequestServer(httpRequest);
        punishmentLifecycleService.createMinecraftPunishment(server, toLegacyCreatePunishmentRequest(request));
        return ResponseEntity.ok(Empty.getDefaultInstance());
    }

    @PostMapping(
        value = "/acknowledge",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<PunishmentAcknowledgeResponse> acknowledgePunishment(
        @RequestBody @Valid PunishmentAcknowledgeRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentLifecycleService.acknowledgePunishment(
            server,
            UUID.fromString(request.getPlayerUuid()),
            request.getPunishmentId()
        );

        return switch (result.status()) {
            case NOT_FOUND -> acknowledgementResponse(HttpStatus.NOT_FOUND, 404, result.message());
            case INVALID_REQUEST -> acknowledgementResponse(HttpStatus.BAD_REQUEST, 400, result.message());
            case NO_OP, SUCCESS -> acknowledgementResponse(HttpStatus.OK, 200, result.message());
        };
    }

    @PostMapping(
        value = "/{punishmentId}/stat-wipe-acknowledge",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<StatWipeAcknowledgeResponse> acknowledgeStatWipe(
        @PathVariable String punishmentId,
        @RequestBody @Valid StatWipeAcknowledgeRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentMutationService.acknowledgeStatWipe(server, punishmentId);

        return switch (result.status()) {
            case NOT_FOUND -> statWipeResponse(HttpStatus.NOT_FOUND, 404, false, result.message());
            case INVALID_REQUEST -> statWipeResponse(HttpStatus.BAD_REQUEST, 400, false, result.message());
            case NO_OP -> statWipeResponse(HttpStatus.OK, 200, false, result.message());
            case SUCCESS -> statWipeResponse(HttpStatus.OK, 200, true, result.message());
        };
    }

    @PostMapping(
        value = "/{punishmentId}/duration",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<ChangePunishmentDurationResponse> changeDuration(
        @PathVariable String punishmentId,
        @RequestBody @Valid ChangePunishmentDurationRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentMutationService.changeDuration(
            server,
            punishmentId,
            request.hasNewDuration() ? request.getNewDuration() : null,
            emptyToNull(request.getIssuerName()),
            emptyToNull(request.getIssuerId())
        );

        return switch (result.status()) {
            case NOT_FOUND -> changeDurationResponse(HttpStatus.NOT_FOUND, 404, null, result.message());
            case INVALID_REQUEST, NO_OP, SUCCESS -> changeDurationResponse(HttpStatus.OK, 200, true, result.message());
        };
    }

    @PostMapping(
        value = "/{punishmentId}/toggle",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<TogglePunishmentOptionResponse> toggleOption(
        @PathVariable String punishmentId,
        @RequestBody @Valid TogglePunishmentOptionRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentMutationService.toggleOption(
            server,
            punishmentId,
            request.getOption(),
            request.getEnabled(),
            emptyToNull(request.getIssuerName()),
            emptyToNull(request.getIssuerId())
        );

        return switch (result.status()) {
            case NOT_FOUND -> toggleOptionResponse(HttpStatus.NOT_FOUND, 404, null, result.message());
            case INVALID_REQUEST -> toggleOptionResponse(HttpStatus.BAD_REQUEST, 400, null, result.message());
            case NO_OP, SUCCESS -> toggleOptionResponse(HttpStatus.OK, 200, result.success(), result.message());
        };
    }

    @PostMapping(
        value = "/{punishmentId}/note",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<AddPunishmentNoteResponse> addNote(
        @PathVariable String punishmentId,
        @RequestBody @Valid AddPunishmentNoteRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentEvidenceService.addPunishmentNote(
            server,
            punishmentId,
            request.getNote(),
            emptyToNull(request.getIssuerName()),
            emptyToNull(request.getIssuerId())
        );

        if (result.status() == PunishmentOperationStatus.NOT_FOUND) {
            return addNoteResponse(HttpStatus.NOT_FOUND, 404, null, result.message());
        }

        return addNoteResponse(HttpStatus.OK, 200, true, result.message());
    }

    @PostMapping(
        value = "/{punishmentId}/evidence",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<AddPunishmentEvidenceResponse> addEvidence(
        @PathVariable String punishmentId,
        @RequestBody @Valid AddPunishmentEvidenceRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentEvidenceService.addEvidence(
            server,
            punishmentId,
            request.getEvidenceUrl(),
            emptyToNull(request.getIssuerName()),
            emptyToNull(request.getIssuerId())
        );

        if (result.status() == PunishmentOperationStatus.NOT_FOUND) {
            return addEvidenceResponse(HttpStatus.NOT_FOUND, 404, null, result.message());
        }

        return addEvidenceResponse(HttpStatus.OK, 200, true, result.message());
    }

    @PostMapping(
        value = "/{punishmentId}/upload-token",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<EvidenceUploadTokenResponse> createUploadToken(
        @PathVariable String punishmentId,
        @RequestBody(required = false) @Valid CreateEvidenceUploadTokenRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        CreateEvidenceUploadTokenRequest effectiveRequest = request != null
            ? request
            : CreateEvidenceUploadTokenRequest.getDefaultInstance();
        String token = punishmentQueryService.createEvidenceUploadToken(
            server,
            punishmentId,
            emptyToNull(effectiveRequest.getIssuerName())
        ).orElse(null);

        if (token == null) {
            return uploadTokenResponse(HttpStatus.NOT_FOUND, 404, null);
        }

        return uploadTokenResponse(HttpStatus.OK, 200, token);
    }

    @PostMapping(
        value = "/{punishmentId}/pardon",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<PardonResponse> pardonPunishment(
        @PathVariable String punishmentId,
        @RequestBody @Valid PardonPunishmentRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentLifecycleService.pardonPunishment(
            server,
            punishmentId,
            emptyToNull(request.getIssuerName()),
            emptyToNull(request.getIssuerId()),
            request.hasReason() ? request.getReason() : null
        );

        return switch (result.status()) {
            case NOT_FOUND -> pardonResponse(HttpStatus.NOT_FOUND, 404, false, 0, result.message());
            case INVALID_REQUEST -> pardonResponse(HttpStatus.BAD_REQUEST, 400, false, 0, result.message());
            case NO_OP -> pardonResponse(HttpStatus.OK, 200, false, 0, result.message());
            case SUCCESS -> pardonResponse(HttpStatus.OK, 200, true, 1, result.message());
        };
    }

    @PostMapping(
        value = "/{punishmentId}/tickets",
        consumes = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE,
        produces = ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE
    )
    public ResponseEntity<ModifyPunishmentTicketsResponse> modifyTickets(
        @PathVariable String punishmentId,
        @RequestBody @Valid ModifyPunishmentTicketsRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        PunishmentOperationResult result = punishmentMutationService.modifyPunishmentTickets(
            server,
            punishmentId,
            request.getAddTicketIdsList(),
            request.getRemoveTicketIdsList(),
            request.getModifyAssociatedTickets(),
            emptyToNull(request.getIssuerName()),
            emptyToNull(request.getIssuerId())
        );

        if (result.status() == PunishmentOperationStatus.NOT_FOUND) {
            return modifyTicketsResponse(HttpStatus.NOT_FOUND, 404, null, result.message());
        }

        return modifyTicketsResponse(HttpStatus.OK, 200, true, result.message());
    }

    private ResponseEntity<PunishmentAcknowledgeResponse> acknowledgementResponse(
        HttpStatus httpStatus,
        int bodyStatus,
        String message
    ) {
        return ResponseEntity.status(httpStatus)
            .body(PunishmentAcknowledgeResponse.newBuilder()
                .setStatus(bodyStatus)
                .setMessage(message)
                .build());
    }

    private ResponseEntity<TogglePunishmentOptionResponse> toggleOptionResponse(
        HttpStatus httpStatus,
        int bodyStatus,
        Boolean success,
        String message
    ) {
        TogglePunishmentOptionResponse.Builder builder = TogglePunishmentOptionResponse.newBuilder()
            .setStatus(bodyStatus)
            .setMessage(message);
        if (success != null) {
            builder.setSuccess(success);
        }

        return ResponseEntity.status(httpStatus)
            .body(builder.build());
    }

    private ResponseEntity<ChangePunishmentDurationResponse> changeDurationResponse(
        HttpStatus httpStatus,
        int bodyStatus,
        Boolean success,
        String message
    ) {
        ChangePunishmentDurationResponse.Builder builder = ChangePunishmentDurationResponse.newBuilder()
            .setStatus(bodyStatus)
            .setMessage(message);
        if (success != null) {
            builder.setSuccess(success);
        }

        return ResponseEntity.status(httpStatus)
            .body(builder.build());
    }

    private ResponseEntity<AddPunishmentNoteResponse> addNoteResponse(
        HttpStatus httpStatus,
        int bodyStatus,
        Boolean success,
        String message
    ) {
        AddPunishmentNoteResponse.Builder builder = AddPunishmentNoteResponse.newBuilder()
            .setStatus(bodyStatus)
            .setMessage(message);
        if (success != null) {
            builder.setSuccess(success);
        }

        return ResponseEntity.status(httpStatus)
            .body(builder.build());
    }

    private ResponseEntity<AddPunishmentEvidenceResponse> addEvidenceResponse(
        HttpStatus httpStatus,
        int bodyStatus,
        Boolean success,
        String message
    ) {
        AddPunishmentEvidenceResponse.Builder builder = AddPunishmentEvidenceResponse.newBuilder()
            .setStatus(bodyStatus)
            .setMessage(message);
        if (success != null) {
            builder.setSuccess(success);
        }

        return ResponseEntity.status(httpStatus)
            .body(builder.build());
    }

    private ResponseEntity<EvidenceUploadTokenResponse> uploadTokenResponse(
        HttpStatus httpStatus,
        int bodyStatus,
        String token
    ) {
        EvidenceUploadTokenResponse.Builder builder = EvidenceUploadTokenResponse.newBuilder()
            .setStatus(bodyStatus);
        if (token != null) {
            builder.setToken(token);
        }

        return ResponseEntity.status(httpStatus)
            .body(builder.build());
    }

    private PunishmentPreviewResponse toProto(
        PunishmentPreviewView preview
    ) {
        PunishmentPreviewResponse.Builder builder = PunishmentPreviewResponse.newBuilder()
            .setStatus(preview.getStatus())
            .setSuccess(preview.isSuccess())
            .setSingleSeverityPunishment(preview.isSingleSeverityPunishment())
            .setPermanentUntilUsernameChange(preview.isPermanentUntilUsernameChange())
            .setPermanentUntilSkinChange(preview.isPermanentUntilSkinChange())
            .setCanBeAltBlocking(preview.isCanBeAltBlocking())
            .setCanBeStatWiping(preview.isCanBeStatWiping())
            .setSocialPoints(preview.getSocialPoints())
            .setGameplayPoints(preview.getGameplayPoints());

        setIfNotNull(builder::setMessage, preview.getMessage());
        setIfNotNull(builder::setSocialStatus, preview.getSocialStatus());
        setIfNotNull(builder::setGameplayStatus, preview.getGameplayStatus());
        setIfNotNull(builder::setOffenderStatus, preview.getOffenderStatus());
        setIfNotNull(builder::setCategory, preview.getCategory());

        if (preview.getLenient() != null) {
            builder.setLenient(toProto(preview.getLenient()));
        }
        if (preview.getRegular() != null) {
            builder.setRegular(toProto(preview.getRegular()));
        }
        if (preview.getAggravated() != null) {
            builder.setAggravated(toProto(preview.getAggravated()));
        }
        if (preview.getSingleSeverity() != null) {
            builder.setSingleSeverity(toProto(preview.getSingleSeverity()));
        }

        return builder.build();
    }

    private PunishmentPreviewResponse.SeverityPreview toProto(
        PunishmentSeverityPreviewView preview
    ) {
        PunishmentPreviewResponse.SeverityPreview.Builder builder =
            PunishmentPreviewResponse.SeverityPreview.newBuilder()
                .setPermanent(preview.isPermanent())
                .setPoints(preview.getPoints())
                .setDurationMs(preview.getDurationMs())
                .setNewSocialPoints(preview.getNewSocialPoints())
                .setNewGameplayPoints(preview.getNewGameplayPoints());

        setIfNotNull(builder::setSeverity, preview.getSeverity());
        setIfNotNull(builder::setDurationFormatted, preview.getDurationFormatted());
        setIfNotNull(builder::setPunishmentType, preview.getPunishmentType());
        setIfNotNull(builder::setNewSocialStatus, preview.getNewSocialStatus());
        setIfNotNull(builder::setNewGameplayStatus, preview.getNewGameplayStatus());

        return builder.build();
    }

    private PunishmentDetailResponse.PunishmentDetailEntry toPunishmentDetail(Map<String, Object> punishment) {
        PunishmentDetailResponse.PunishmentDetailEntry.Builder builder =
            PunishmentDetailResponse.PunishmentDetailEntry.newBuilder()
                .setPlayerName(stringValue(punishment.get("playerName")))
                .setPlayerUuid(stringValue(punishment.get("playerUuid")))
                .setId(stringValue(punishment.get("id")))
                .setIssuerName(stringValue(punishment.get("issuerName")))
                .setIssued(detailStringValue(punishment.get("issued")))
                .setStarted(detailStringValue(punishment.get("started")))
                .setType(stringValue(punishment.get("type")))
                .setTypeOrdinal(intValue(punishment.get("typeOrdinal")));

        list(punishment.get("attachedTicketIds")).stream()
            .map(Objects::toString)
            .forEach(builder::addAttachedTicketIds);

        Map<String, Object> data = mapValue(punishment.get("data"));
        if (data != null) {
            builder.setData(toDetailStruct(data));
        }
        listOfMaps(punishment.get("modifications")).stream()
            .map(this::toDetailStruct)
            .forEach(builder::addModifications);
        listOfMaps(punishment.get("notes")).stream()
            .map(this::toDetailStruct)
            .forEach(builder::addNotes);
        listOfMaps(punishment.get("evidence")).stream()
            .map(this::toDetailStruct)
            .forEach(builder::addEvidence);

        return builder.build();
    }

    private Struct toDetailStruct(Map<String, Object> map) {
        return MinecraftPlayerProtoMapper.toStruct(normalizeDetailMap(map));
    }

    private Map<String, Object> normalizeDetailMap(Map<String, Object> map) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, value) -> normalized.put(key, normalizeDetailValue(value)));
        return normalized;
    }

    private Object normalizeDetailValue(Object value) {
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) ->
                normalized.put(Objects.toString(key), normalizeDetailValue(nestedValue)));
            return normalized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new ArrayList<>();
            iterable.forEach(item -> normalized.add(normalizeDetailValue(item)));
            return normalized;
        }
        return value;
    }

    private String detailStringValue(Object value) {
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        return stringValue(value);
    }

    private RecentPunishmentsResponse.RecentPunishment toRecentPunishment(Map<String, Object> punishment) {
        RecentPunishmentsResponse.RecentPunishment.Builder builder =
            RecentPunishmentsResponse.RecentPunishment.newBuilder()
                .setPlayerName(stringValue(punishment.get("playerName")))
                .setPlayerUuid(stringValue(punishment.get("playerUuid")))
                .setId(stringValue(punishment.get("id")))
                .setIssuerName(stringValue(punishment.get("issuerName")))
                .setIssued(longValue(punishment.get("issued")))
                .setType(stringValue(punishment.get("type")));

        setOptionalLong(builder::setStarted, punishment.get("started"));
        setOptionalInt(builder::setTypeOrdinal, punishment.get("typeOrdinal"));
        listOfMaps(punishment.get("modifications")).stream()
            .map(this::toPunishmentModification)
            .forEach(builder::addModifications);
        listOfMaps(punishment.get("notes")).stream()
            .map(this::toPunishmentNote)
            .forEach(builder::addNotes);
        listOfMaps(punishment.get("evidence")).stream()
            .map(this::toPunishmentEvidence)
            .forEach(builder::addEvidence);
        list(punishment.get("attachedTicketIds")).stream()
            .map(Objects::toString)
            .forEach(builder::addAttachedTicketIds);

        Map<String, Object> data = mapValue(punishment.get("data"));
        if (data != null) {
            builder.setData(MinecraftPlayerProtoMapper.toStruct(data));
        }

        return builder.build();
    }

    private void validateRecentHours(int hours) {
        if (hours < 1 || hours > 8760) {
            throw new ValidationException("hours must be between 1 and 8760");
        }
    }

    private PunishmentModification toPunishmentModification(Map<String, Object> modification) {
        PunishmentModification.Builder builder = PunishmentModification.newBuilder()
            .setId(stringValue(modification.get("id")))
            .setType(stringValue(modification.get("type")))
            .setDate(longValue(modification.get("date")))
            .setReason(stringValue(modification.get("reason")));

        setOptionalString(builder::setIssuerName, modification.get("issuerName"));
        setOptionalString(builder::setIssuerId, modification.get("issuerId"));
        setOptionalLong(builder::setEffectiveDuration, modification.get("effectiveDuration"));
        setOptionalString(builder::setAppealTicketId, modification.get("appealTicketId"));
        Map<String, Object> data = mapValue(modification.get("data"));
        if (data != null) {
            builder.setData(MinecraftPlayerProtoMapper.toStruct(data));
        }

        return builder.build();
    }

    private PunishmentNote toPunishmentNote(Map<String, Object> note) {
        PunishmentNote.Builder builder = PunishmentNote.newBuilder()
            .setId(stringValue(note.get("id")))
            .setText(stringValue(note.get("text")))
            .setDate(longValue(note.get("date")));

        setOptionalString(builder::setIssuerName, note.get("issuerName"));
        setOptionalString(builder::setIssuerId, note.get("issuerId"));
        return builder.build();
    }

    private PunishmentEvidence toPunishmentEvidence(Map<String, Object> evidence) {
        PunishmentEvidence.Builder builder = PunishmentEvidence.newBuilder()
            .setType(stringValue(evidence.get("type")))
            .setUploadedAt(longValue(evidence.get("uploadedAt")));

        setOptionalString(builder::setText, evidence.get("text"));
        setOptionalString(builder::setUrl, evidence.get("url"));
        setOptionalString(builder::setUploadedBy, evidence.get("uploadedBy"));
        setOptionalString(builder::setUploadedById, evidence.get("uploadedById"));
        setOptionalString(builder::setFileName, evidence.get("fileName"));
        setOptionalString(builder::setFileType, evidence.get("fileType"));
        setOptionalLong(builder::setFileSize, evidence.get("fileSize"));
        return builder.build();
    }

    private void setIfNotNull(Consumer<String> setter, String value) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private void setOptionalString(Consumer<String> setter, Object value) {
        if (value != null) {
            setter.accept(Objects.toString(value));
        }
    }

    private void setOptionalLong(LongConsumer setter, Object value) {
        if (value != null) {
            setter.accept(longValue(value));
        }
    }

    private void setOptionalInt(IntConsumer setter, Object value) {
        if (value != null) {
            setter.accept(intValue(value));
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : Objects.toString(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.parseInt(string);
        }
        return 0;
    }

    private long longValue(Object value) {
        if (value instanceof Date date) {
            return date.getTime();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return 0L;
    }

    private List<?> list(Object value) {
        if (value instanceof List<?> values) {
            return values;
        }
        return List.of();
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        return list(value).stream()
            .filter(Map.class::isInstance)
            .map(item -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                return map;
            })
            .toList();
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        if (value instanceof Struct struct) {
            return MinecraftPlayerProtoMapper.structToMap(struct);
        }
        return null;
    }

    private ResponseEntity<PardonResponse> pardonResponse(
        HttpStatus httpStatus,
        int bodyStatus,
        boolean success,
        int pardonedCount,
        String message
    ) {
        return ResponseEntity.status(httpStatus)
            .body(PardonResponse.newBuilder()
                .setStatus(bodyStatus)
                .setSuccess(success)
                .setPardonedCount(pardonedCount)
                .setMessage(message)
                .build());
    }

    private ResponseEntity<ModifyPunishmentTicketsResponse> modifyTicketsResponse(
        HttpStatus httpStatus,
        int bodyStatus,
        Boolean success,
        String message
    ) {
        ModifyPunishmentTicketsResponse.Builder builder = ModifyPunishmentTicketsResponse.newBuilder()
            .setStatus(bodyStatus)
            .setMessage(message);
        if (success != null) {
            builder.setSuccess(success);
        }

        return ResponseEntity.status(httpStatus)
            .body(builder.build());
    }

    private void validateCreatePunishmentData(CreatePunishmentRequest request) {
        if (request.hasData()
            && request.getData().getFieldsCount() > RequestValidationLimits.PLAYER_PUNISHMENT_DATA_MAX_ENTRIES) {
            throw new ValidationException("data must contain no more than "
                + RequestValidationLimits.PLAYER_PUNISHMENT_DATA_MAX_ENTRIES + " entries");
        }
    }

    private MinecraftPunishmentController.MinecraftCreatePunishmentRequest toLegacyCreatePunishmentRequest(
        CreatePunishmentRequest request
    ) {
        return new MinecraftPunishmentController.MinecraftCreatePunishmentRequest(
            request.getTargetUuid(),
            request.hasIssuerName() ? request.getIssuerName() : null,
            request.hasIssuerId() ? request.getIssuerId() : null,
            request.getTypeOrdinal(),
            request.hasReason() ? request.getReason() : null,
            request.hasDuration() ? request.getDuration() : null,
            request.hasData() ? MinecraftPlayerProtoMapper.structToMap(request.getData()) : null,
            request.getNotesList(),
            request.getAttachedTicketIdsList(),
            request.hasSeverity() ? request.getSeverity() : null,
            request.hasStatus() ? request.getStatus() : null
        );
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private ResponseEntity<StatWipeAcknowledgeResponse> statWipeResponse(
        HttpStatus httpStatus,
        int bodyStatus,
        boolean success,
        String message
    ) {
        return ResponseEntity.status(httpStatus)
            .body(StatWipeAcknowledgeResponse.newBuilder()
                .setStatus(bodyStatus)
                .setSuccess(success)
                .setMessage(message)
                .build());
    }
}
