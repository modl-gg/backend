package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.ticket.service.TicketService;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.player.dto.request.MinecraftCreatePunishmentRequest;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentData;
import gg.modl.backend.player.data.punishment.PunishmentDataView;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentModificationType;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.data.punishment.PunishmentStatus;
import gg.modl.backend.player.data.punishment.EnforcementCategory;
import gg.modl.backend.player.dto.request.CreateEvidenceRequest;
import gg.modl.backend.player.dto.request.CreateNoteRequest;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentContext;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeIndex;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.util.MongoKeyUtils;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.infrastructure.validation.SafeUrls;
import gg.modl.backend.log.service.LogService;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.infrastructure.util.IdGenerator;
import gg.modl.backend.settings.service.WebhookSettingsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PunishmentLifecycleService {
    private final PlayerMongoRepository playerRepository;
    private final PunishmentMongoRepository punishmentRepository;
    private final TicketService ticketService;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final OffenderThresholdSettingsService thresholdSettingsService;
    private final PunishmentDurationCalculator durationCalculator;
    private final IssuerNameResolver issuerNameResolver;
    private final StaffMongoRepository staffRepository;
    private final PunishmentQueryService punishmentQueryService;
    private final PermissionService permissionService;
    private final WebhookSettingsService webhookSettingsService;
    private final PunishmentRealtimePublisher realtimePublisher;
    private final LogService logService;

    private static final String SYSTEM_ISSUER = "System";

    private static final Set<String> CLIENT_SETTABLE_PUNISHMENT_DATA_KEYS = Set.of(
        PunishmentData.ALT_BLOCKING,
        PunishmentData.WIPE_AFTER_EXPIRY,
        "banLinkedAccounts",
        "kickSameIP",
        "silent",
        PunishmentData.LINKED_BAN_ID,
        PunishmentData.SEVERITY,
        PunishmentData.STATUS,
        PunishmentData.DURATION,
        PunishmentData.REASON,
        "aiGenerated",
        PunishmentData.PENDING_ACKNOWLEDGEMENT
    );

    private Map<String, Object> filterClientSettableData(Map<String, Object> requestData) {
        Map<String, Object> data = new HashMap<>();
        if (requestData == null) {
            return data;
        }
        Map<String, Object> sanitized = MongoKeyUtils.sanitizeKeys(new HashMap<>(requestData));
        for (String key : CLIENT_SETTABLE_PUNISHMENT_DATA_KEYS) {
            if (sanitized.containsKey(key)) {
                data.put(key, sanitized.get(key));
            }
        }
        return data;
    }

    private void validateReasonLength(CreatePunishmentRequest request) {
        requireReasonWithinLimit(request.reason());
        if (request.data() != null) {
            requireReasonWithinLimit(request.data().get("reason"));
        }
    }

    private void requireReasonWithinLimit(Object reason) {
        if (reason instanceof String text && text.length() > RequestValidationLimits.PLAYER_PUNISHMENT_REASON_MAX_LENGTH) {
            throw new ValidationException("reason must be at most "
                + RequestValidationLimits.PLAYER_PUNISHMENT_REASON_MAX_LENGTH + " characters");
        }
    }

    public void validatePunishmentPermission(Server server, String email, int typeOrdinal) {
        if (email == null) {
            throw new ForbiddenException("No authenticated user found for permission check");
        }
        if (permissionService.isSuperAdmin(server, email)) {
            return;
        }

        PunishmentType type = punishmentTypeService.getPunishmentTypeByOrdinal(server, typeOrdinal)
            .orElseThrow(() -> new ValidationException("Invalid punishment type"));

        String applyPermission = PermissionService.punishmentApplyPermissionId(type.getName());
        String roleId = staffRepository.findByEmailIgnoreCase(server, email)
            .map(Staff::getRoleId)
            .orElse(null);

        if (!permissionService.hasPermission(server, roleId, applyPermission)) {
            throw new ForbiddenException("You do not have permission to apply this punishment type");
        }
    }

    public String createMinecraftPunishment(Server server, MinecraftCreatePunishmentRequest request) {
        UUID playerUuid = UUID.fromString(request.targetUuid());

        List<CreateNoteRequest> noteRequests = null;
        if (request.notes() != null) {
            noteRequests = request.notes()
                .stream()
                .map(text -> new CreateNoteRequest(text, request.issuerName(), request.issuerId(), null))
                .toList();
        }

        Map<String, Object> data = request.data() != null ? MongoKeyUtils.sanitizeKeys(new HashMap<>(request.data())) : new HashMap<>();
        PunishmentDataView.ofMap(data).setPendingAcknowledgement(true);

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

        return createPunishment(server, playerUuid, serviceRequest);
    }

    public String createPunishment(Server server, UUID playerUuid, CreatePunishmentRequest request) {
        validateReasonLength(request);
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);

        if (player == null) {
            throw new ResourceNotFoundException("Player not found");
        }
        Date now = new Date();
        Map<String, Object> data = filterClientSettableData(request.data());
        PunishmentDataView view = PunishmentDataView.ofMap(data);

        if (request.severity() != null) {
            view.setSeverity(request.severity());
        }
        if (request.status() != null) {
            view.setStatus(request.status());
        }

        Long calculatedDuration = request.duration();
        if (calculatedDuration == null && request.severity() != null) {
            PunishmentDurationCalculator.DurationResult result =
                durationCalculator.calculate(server, player.getPunishments(), request.typeOrdinal(), request.severity());
            calculatedDuration = result.duration();

            if (result.status() != null && (!view.hasStatus() || view.status() == null)) {
                view.setStatus(result.status());
            }
            if (result.offenseLevel() != null && !view.hasOffenseLevel()) {
                view.setOffenseLevel(result.offenseLevel());
            }
        }

        if (calculatedDuration == null) {
            calculatedDuration = view.duration();
        }

        if (calculatedDuration != null && calculatedDuration != 0) {
            view.setDuration(calculatedDuration);
        }
        if (request.reason() != null && !request.reason().isBlank()) {
            view.setReason(request.reason());
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        Map<Integer, PunishmentType> typesByOrdinal = PunishmentTypeIndex.byOrdinal(types);
        PunishmentType newPunishmentType = typesByOrdinal.get(request.typeOrdinal());

        applyRestrictionBlocks(newPunishmentType, player, view);

        String newCategory = statusCalculator.getEffectiveCategory(newPunishmentType, view);
        if (newCategory != null) {
            view.setEnforcementCategory(newCategory);
            boolean hasExistingInCategory = player.getPunishments()
                .stream().anyMatch(existing -> {
                    String existingCategory = statusCalculator.getEffectiveCategory(existing, types);
                    if (!newCategory.equals(existingCategory)) {
                        return false;
                    }

                    boolean active = statusCalculator.isPunishmentActive(existing);
                    boolean unstarted = existing.isUnstarted();
                    return active || unstarted;
                });

            if (hasExistingInCategory) {
                view.setStatus(PunishmentStatus.UNSTARTED);
            }
        }

        String reqIssuerName = PunishmentMapper.storedName(request.issuerId(), request.issuerName());
        String reqIssuerId = request.issuerId();

        List<PunishmentNote> notes = buildCreationNotes(request, now, reqIssuerName, reqIssuerId, newPunishmentType, newCategory, calculatedDuration);

        List<PunishmentEvidence> evidence = buildCreationEvidence(request, now, reqIssuerId);

        String punishmentId = IdGenerator.generateShortId();

        boolean pendingAcknowledgement = view.removePendingAcknowledgement();
        if (pendingAcknowledgement) {
            view.setStatus(PunishmentStatus.UNSTARTED);
        }
        Date startedDate = null;

        Punishment punishment = new Punishment(
            punishmentId,
            request.typeOrdinal(),
            reqIssuerName,
            reqIssuerId,
            now,
            startedDate,
            new ArrayList<>(),
            notes,
            evidence,
            request.attachedTicketIds() != null ? request.attachedTicketIds() : new ArrayList<>(),
            data
        );

        player.getPunishments().add(punishment);
        punishmentRepository.appendPunishment(server, player.getMinecraftUuid().toString(), punishment);

        String playerName = PlayerDataUtils.extractLatestUsername(player.getUsernames());
        String typeName = newPunishmentType != null ? newPunishmentType.getName() : "Unknown";
        String severity = request.severity() != null ? request.severity() : "";
        String reason = request.reason() != null ? request.reason() : "";
        String duration = calculatedDuration != null
            ? PunishmentMapper.formatDuration(calculatedDuration, calculatedDuration < 0)
            : "Permanent";
        String issuerDisplay = issuerNameResolver.resolve(request.issuerId(), request.issuerName(), server);
        String ticketIds = request.attachedTicketIds() != null ? String.join(", ", request.attachedTicketIds()) : "";

        webhookSettingsService.sendPunishmentCreatedWebhook(server, Map.of(
            "id", punishmentId,
            "playerName", playerName,
            "type", typeName,
            "severity", severity,
            "reason", reason,
            "duration", duration,
            "issuer", issuerDisplay,
            "ticketId", ticketIds
        ));

        if (request.attachedTicketIds() != null && !request.attachedTicketIds().isEmpty()) {
            String ticketIssuerName = issuerNameResolver.resolve(request.issuerId(), request.issuerName(), server);
            closeAttachedTickets(server, request.attachedTicketIds(), ticketIssuerName);
        }

        realtimePublisher.punishmentIssued(server, player, punishment);

        logService.recordModerationAction(server, issuerDisplay, typeName + " issued for " + playerName);

        return punishmentId;
    }

    private void applyRestrictionBlocks(PunishmentType punishmentType, Player player, PunishmentDataView data) {
        if (punishmentType == null) {
            return;
        }
        if (punishmentType.isPermanentUntilUsernameChange() && !data.hasBlockedName()) {
            String currentUsername = PlayerDataUtils.extractLatestUsername(player.getUsernames());
            currentUsername = "Unknown".equals(currentUsername) ? null : currentUsername;
            if (currentUsername != null) {
                data.setBlockedName(currentUsername);
            }
        }
        if (punishmentType.isPermanentUntilSkinChange() && !data.hasBlockedSkin()) {
            String skinHash = player.data().lastSkinHash();
            if (skinHash != null) {
                data.setBlockedSkin(skinHash);
            }
        }
    }

    private List<PunishmentNote> buildCreationNotes(
        CreatePunishmentRequest request,
        Date now,
        String reqIssuerName,
        String reqIssuerId,
        PunishmentType punishmentType,
        String newCategory,
        Long calculatedDuration
    ) {
        List<PunishmentNote> notes = new ArrayList<>();
        String enforcementType = punishmentType != null && punishmentType.isKick() ? "kick"
            : EnforcementCategory.BAN.name().equals(newCategory) ? "ban"
            : EnforcementCategory.MUTE.name().equals(newCategory) ? "mute"
            : "punishment";
        String issuedNote = calculatedDuration != null && calculatedDuration > 0
            ? "issued " + PunishmentMapper.formatDuration(calculatedDuration, false) + " " + enforcementType
            : "issued permanent " + enforcementType;
        if ("kick".equals(enforcementType)) {
            issuedNote = "issued kick";
        }
        notes.add(new PunishmentNote(IdGenerator.generateShortId(), issuedNote, now, reqIssuerName, reqIssuerId));
        if (request.reason() != null && !request.reason().isBlank()) {
            notes.add(new PunishmentNote(IdGenerator.generateShortId(), request.reason(), now, reqIssuerName, reqIssuerId));
        }
        if (request.notes() != null) {
            for (CreateNoteRequest noteRequest : request.notes()) {
                String noteIssuerId = noteRequest.issuerId() != null ? noteRequest.issuerId() : reqIssuerId;
                String noteIssuerName = PunishmentMapper.storedName(noteIssuerId, noteRequest.issuerName() != null ? noteRequest.issuerName() : request.issuerName());
                notes.add(new PunishmentNote(IdGenerator.generateShortId(), noteRequest.text(), now, noteIssuerName, noteIssuerId));
            }
        }
        return notes;
    }

    private List<PunishmentEvidence> buildCreationEvidence(CreatePunishmentRequest request, Date now, String reqIssuerId) {
        List<PunishmentEvidence> evidence = new ArrayList<>();
        if (request.evidence() != null) {
            for (CreateEvidenceRequest evidenceRequest : request.evidence()) {
                String evIssuerId = evidenceRequest.issuerId() != null ? evidenceRequest.issuerId() : reqIssuerId;
                String evIssuerName = PunishmentMapper.storedName(evIssuerId, evidenceRequest.issuerName() != null ? evidenceRequest.issuerName() : request.issuerName());
                String type = evidenceRequest.type() != null ? evidenceRequest.type() : "text";
                SafeUrls.requireSafe(evidenceRequest.fileUrl(), "Invalid evidence URL");
                evidence.add(new PunishmentEvidence(
                    evidenceRequest.text(),
                    evidenceRequest.fileUrl(),
                    type,
                    evIssuerName,
                    evIssuerId,
                    now,
                    evidenceRequest.fileName(),
                    evidenceRequest.fileType(),
                    evidenceRequest.fileSize()
                ));
            }
        }
        return evidence;
    }

    void closeAttachedTickets(Server server, List<String> ticketIds, String issuerName) {
        for (String ticketId : ticketIds) {
            try {
                ticketService.closeTicketForPunishment(server, ticketId, issuerName);
            } catch (Exception e) {
                log.error("[TICKET_CLOSE] Failed to close ticket {}", ticketId, e);
            }
        }
    }


    public PunishmentOperationResult acknowledgePunishment(Server server, UUID playerUuid, String punishmentId) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
        if (player == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Player not found: " + playerUuid, false, 0);
        }

        Punishment punishment = PunishmentQueryService.findPunishment(player, punishmentId);
        if (punishment == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND,
                "Punishment not found: " + punishmentId + " for player: " + playerUuid, false, 0);
        }

        boolean acknowledged = punishmentRepository.acknowledgePunishmentStart(
            server, player.getMinecraftUuid().toString(), punishmentId, new Date());
        if (!acknowledged) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NO_OP, "Punishment already acknowledged", true, 0);
        }

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Punishment acknowledged", true, 1);
    }

    public PunishmentOperationResult pardonPunishment(Server server, String punishmentId, String issuerName, String issuerId, String reason) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Punishment punishment = context.punishment();
        if (punishment.isPardoned()) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NO_OP,
                "Punishment has already been pardoned", false, 0);
        }

        applyManualPardon(server, context.player(), punishment, issuerName, issuerId, reason);
        realtimePublisher.punishmentModified(server, context.player(), punishment);

        if (punishment.data().altBlocking()) {
            cascadePardonLinkedBans(server, punishmentId);
        }

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Punishment pardoned", true, 1);
    }

    public int pardonPunishments(Server server, Player player, List<Punishment> targets,
                                 String issuerName, String issuerId, String reason) {
        List<PunishmentRealtimePublisher.PlayerPunishment> modified = new ArrayList<>();
        Set<String> altBlockingParents = new LinkedHashSet<>();

        for (Punishment punishment : targets) {
            applyManualPardon(server, player, punishment, issuerName, issuerId, reason);
            modified.add(new PunishmentRealtimePublisher.PlayerPunishment(player, punishment));
            if (punishment.data().altBlocking()) {
                altBlockingParents.add(punishment.getId());
            }
        }

        realtimePublisher.punishmentsModified(server, modified);

        for (String parentPunishmentId : altBlockingParents) {
            cascadePardonLinkedBans(server, parentPunishmentId);
        }

        return targets.size();
    }

    private void applyManualPardon(Server server, Player player, Punishment punishment,
                                   String issuerName, String issuerId, String reason) {
        String resolvedIssuerName = PunishmentMapper.storedName(issuerId, issuerName);
        Date now = new Date();

        PunishmentModification modification = new PunishmentModification(
            IdGenerator.generateShortId(),
            PunishmentModificationType.MANUAL_PARDON.name(),
            now,
            resolvedIssuerName,
            issuerId,
            reason != null ? reason : "",
            null,
            null,
            null
        );

        List<PunishmentNote> notes = new ArrayList<>();
        notes.add(new PunishmentNote(IdGenerator.generateShortId(), "pardoned punishment", now, resolvedIssuerName, issuerId));
        if (reason != null && !reason.isBlank()) {
            notes.add(new PunishmentNote(IdGenerator.generateShortId(), reason, now, resolvedIssuerName, issuerId));
        }

        punishment.getModifications().add(modification);
        punishment.getNotes().addAll(notes);
        punishment.data().setStatus(PunishmentStatus.PARDONED);

        punishmentRepository.appendPardon(server, player.getMinecraftUuid().toString(), punishment.getId(),
            modification, notes, PunishmentStatus.PARDONED);
    }

    private int cascadeToLinkedBans(Server server, String parentPunishmentId, LinkedBanCascade cascade) {
        List<PunishmentRealtimePublisher.PlayerPunishment> modified = new ArrayList<>();
        int count = 0;
        for (Player player : punishmentRepository.findByLinkedBanId(server, parentPunishmentId)) {
            count += cascade.apply(player, modified);
        }
        realtimePublisher.punishmentsModified(server, modified);
        return count;
    }

    @FunctionalInterface
    private interface LinkedBanCascade {
        int apply(Player player, List<PunishmentRealtimePublisher.PlayerPunishment> modified);
    }

    private boolean isActiveLinkedBanFor(Punishment punishment, String parentPunishmentId) {
        return punishment.getTypeOrdinal() == Punishment.LINKED_BAN_TYPE_ORDINAL
            && parentPunishmentId.equals(punishment.data().linkedBanId())
            && statusCalculator.isPunishmentActive(punishment);
    }

    public int cascadePardonLinkedBans(Server server, String parentPunishmentId) {
        return cascadeToLinkedBans(server, parentPunishmentId,
            (player, modified) -> applyLinkedBanSystemPardon(server, player, parentPunishmentId, modified));
    }

    private int applyLinkedBanSystemPardon(
        Server server,
        Player player,
        String parentPunishmentId,
        List<PunishmentRealtimePublisher.PlayerPunishment> modified
    ) {
        int count = 0;

        for (Punishment punishment : player.getPunishments()) {
            if (!isActiveLinkedBanFor(punishment, parentPunishmentId)) {
                continue;
            }

            applySystemPardon(server, player.getMinecraftUuid().toString(), punishment,
                "Auto-pardoned: parent ban was pardoned", new Date());
            modified.add(new PunishmentRealtimePublisher.PlayerPunishment(player, punishment));
            count++;
        }

        return count;
    }

    private void applySystemPardon(Server server, String playerUuid, Punishment punishment, String reason, Date now) {
        PunishmentModification modification = new PunishmentModification(
            IdGenerator.generateShortId(),
            PunishmentModificationType.SYSTEM_PARDON.name(),
            now,
            SYSTEM_ISSUER,
            null,
            reason,
            null,
            null,
            null
        );
        PunishmentNote note = new PunishmentNote(
            IdGenerator.generateShortId(),
            reason,
            now,
            SYSTEM_ISSUER,
            null
        );

        punishment.getModifications().add(modification);
        punishment.getNotes().add(note);
        punishment.data().setStatus(PunishmentStatus.PARDONED);

        punishmentRepository.appendPardon(server, playerUuid, punishment.getId(),
            modification, List.of(note), PunishmentStatus.PARDONED);
    }


    public int cascadeDurationChangeToLinkedBans(Server server, String parentPunishmentId, Long newDuration, String issuerName) {
        return cascadeToLinkedBans(server, parentPunishmentId,
            (player, modified) -> applyLinkedBanDurationChange(server, player, parentPunishmentId, newDuration, modified));
    }

    private int applyLinkedBanDurationChange(
        Server server,
        Player player,
        String parentPunishmentId,
        Long newDuration,
        List<PunishmentRealtimePublisher.PlayerPunishment> modified
    ) {
        int count = 0;

        for (Punishment punishment : player.getPunishments()) {
            if (!isActiveLinkedBanFor(punishment, parentPunishmentId)) {
                continue;
            }

            Date now = new Date();
            Long effective = (newDuration == null) ? -1L : newDuration;
            PunishmentModification modification = new PunishmentModification(
                IdGenerator.generateShortId(),
                PunishmentModificationType.MANUAL_DURATION_CHANGE.name(),
                now,
                SYSTEM_ISSUER,
                null,
                "Cascaded from parent ban duration change",
                effective,
                null,
                null
            );
            PunishmentNote note = new PunishmentNote(
                IdGenerator.generateShortId(),
                "Duration changed (cascaded from parent ban)",
                now,
                SYSTEM_ISSUER,
                null
            );

            punishment.data().setDuration(effective);
            punishment.getModifications().add(modification);
            punishment.getNotes().add(note);
            String linkedPlayerUuid = player.getMinecraftUuid().toString();
            punishmentRepository.appendDurationChange(server, linkedPlayerUuid, punishment.getId(), modification, note, effective);

            if (punishment.getStarted() == null) {
                punishment.setStarted(now);
                punishmentRepository.setPunishmentStartedIfUnset(server, linkedPlayerUuid, punishment.getId(), now);
            }
            modified.add(new PunishmentRealtimePublisher.PlayerPunishment(player, punishment));
            count++;
        }

        return count;
    }

    public List<String> promoteUnstartedPunishments(Server server, Player player) {
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        List<String> promotedIds = new ArrayList<>();
        List<Punishment> promoted = new ArrayList<>();

        for (String category : List.of(EnforcementCategory.BAN.name(), EnforcementCategory.MUTE.name())) {
            boolean hasActive = player.getPunishments()
                .stream().anyMatch(p -> {
                    String effectiveCategory = statusCalculator.getEffectiveCategory(p, types);
                    return category.equals(effectiveCategory) && statusCalculator.isPunishmentActive(p);
                });

            if (hasActive) {
                continue;
            }

            Optional<Punishment> oldest = player.getPunishments()
                .stream()
                .filter(p -> {
                    String effectiveCategory = statusCalculator.getEffectiveCategory(p, types);
                    return category.equals(effectiveCategory) && p.isUnstarted();
                })
                .min((a, b) -> a.getIssued().compareTo(b.getIssued()));

            if (oldest.isPresent()) {
                Punishment toPromote = oldest.get();
                punishmentRepository.unsetPunishmentStatus(server, player.getMinecraftUuid().toString(), toPromote.getId());
                toPromote.data().removeStatus();
                promotedIds.add(toPromote.getId());
                promoted.add(toPromote);
            }
        }

        realtimePublisher.punishmentsPromoted(server, player, promoted);

        return promotedIds;
    }

    public List<String> enforceAltBlockingBans(Server server, Player player) {
        List<String> createdIds = new ArrayList<>();

        List<String> linkedUuids = getLinkedAccountUuids(player);
        if (linkedUuids.isEmpty()) {
            return createdIds;
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);

        boolean alreadyBanned = player.getPunishments()
            .stream().anyMatch(p -> {
                String cat = statusCalculator.getEffectiveCategory(p, types);
                return EnforcementCategory.BAN.name().equals(cat) && statusCalculator.isPunishmentActive(p);
            });
        if (alreadyBanned) {
            return createdIds;
        }

        List<Player> linkedPlayers = playerRepository.findByMinecraftUuids(server, linkedUuids);

        for (Player linkedPlayer : linkedPlayers) {
            for (Punishment punishment : linkedPlayer.getPunishments()) {
                if (!statusCalculator.isPunishmentActive(punishment)) {
                    continue;
                }

                if (!punishment.data().altBlocking()) {
                    continue;
                }

                String cat = statusCalculator.getEffectiveCategory(punishment, types);
                if (!EnforcementCategory.BAN.name().equals(cat)) {
                    continue;
                }

                Date parentExpiry = statusCalculator.getEffectiveExpiry(punishment);
                Long linkedDuration = null;
                if (parentExpiry != null) {
                    long remaining = parentExpiry.getTime() - System.currentTimeMillis();
                    if (remaining <= 0) {
                        continue;
                    }
                    linkedDuration = remaining;
                }

                String linkedBanId = createLinkedBanPunishment(
                    server, player.getMinecraftUuid(),
                    punishment.getId(),
                    linkedPlayer.getMinecraftUuid().toString(),
                    linkedDuration
                );
                createdIds.add(linkedBanId);

                return createdIds;
            }
        }

        return createdIds;
    }

    private String createLinkedBanPunishment(Server server, UUID playerUuid, String parentPunishmentId, String parentPlayerUuid, Long duration) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
        if (player == null) {
            throw new ResourceNotFoundException("Player not found");
        }

        Date now = new Date();
        Map<String, Object> data = new HashMap<>();
        PunishmentDataView view = PunishmentDataView.ofMap(data);
        view.setLinkedBanId(parentPunishmentId);
        view.setLinkedBanParentUuid(parentPlayerUuid);
        if (duration != null) {
            view.setDuration(duration);
        }

        String punishmentId = IdGenerator.generateShortId();

        List<PunishmentNote> notes = new ArrayList<>();
        String linkedBanNote = duration != null && duration > 0
                               ? "issued " + PunishmentMapper.formatDuration(duration, false) + " ban"
                               : "issued permanent ban";
        notes.add(new PunishmentNote(
            IdGenerator.generateShortId(),
            linkedBanNote,
            now,
            SYSTEM_ISSUER,
            null
        ));
        notes.add(new PunishmentNote(
            IdGenerator.generateShortId(),
            "Automatically issued linked ban due to alt-blocking ban on linked account",
            now,
            SYSTEM_ISSUER,
            null
        ));

        Punishment punishment = new Punishment(
            punishmentId,
            Punishment.LINKED_BAN_TYPE_ORDINAL,
            SYSTEM_ISSUER,
            null,
            now,
            now,
            new ArrayList<>(),
            notes,
            new ArrayList<>(),
            new ArrayList<>(),
            data
        );

        player.getPunishments().add(punishment);
        punishmentRepository.appendPunishment(server, player.getMinecraftUuid().toString(), punishment);

        return punishmentId;
    }

    private List<String> getLinkedAccountUuids(Player player) {
        return player.data().linkedAccountUuids();
    }

    public List<String> checkRestrictionAutoPardons(Server server, Player player, String currentUsername, String currentSkinHash) {
        List<String> pardonedIds = new ArrayList<>();
        Map<Integer, PunishmentType> typesByOrdinal = PunishmentTypeIndex.byOrdinal(punishmentTypeService.getPunishmentTypes(server));

        for (Punishment punishment : player.getPunishments()) {
            if (!statusCalculator.isPunishmentActive(punishment)) {
                continue;
            }

            PunishmentType type = typesByOrdinal.get(punishment.getTypeOrdinal());
            if (type == null) {
                continue;
            }

            PunishmentDataView data = punishment.data();
            if (data.asMap() == null) {
                continue;
            }

            if (type.isPermanentUntilUsernameChange() && currentUsername != null) {
                String blockedName = data.blockedName();
                if (blockedName != null && !blockedName.equalsIgnoreCase(currentUsername)) {
                    String reason = "Auto-pardoned: username changed from '" + blockedName + "' to '" + currentUsername + "'";
                    systemPardonPunishment(server, player.getMinecraftUuid(), punishment.getId(), reason);
                    pardonedIds.add(punishment.getId());
                }
            }

            if (type.isPermanentUntilSkinChange() && currentSkinHash != null) {
                String blockedSkin = data.blockedSkin();
                if (blockedSkin != null && !blockedSkin.equals(currentSkinHash)) {
                    String reason = "Auto-pardoned: skin changed";
                    systemPardonPunishment(server, player.getMinecraftUuid(), punishment.getId(), reason);
                    pardonedIds.add(punishment.getId());
                }
            }
        }

        return pardonedIds;
    }

    public void systemPardonPunishment(Server server, UUID playerUuid, String punishmentId, String reason) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
        if (player == null) {
            return;
        }

        Punishment punishment = PunishmentQueryService.findPunishment(player, punishmentId);
        if (punishment == null) {
            return;
        }

        applySystemPardon(server, player.getMinecraftUuid().toString(), punishment, reason, new Date());
        realtimePublisher.punishmentModified(server, player, punishment);
    }
}
