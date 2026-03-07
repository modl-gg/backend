package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.PunishmentFields;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.dto.request.AddEvidenceRequest;
import gg.modl.backend.player.dto.request.AddModificationRequest;
import gg.modl.backend.player.dto.request.CreateEvidenceRequest;
import gg.modl.backend.player.dto.request.CreateNoteRequest;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.player.dto.request.ModifyPunishmentTicketsRequest;
import gg.modl.backend.player.dto.response.PunishmentPreviewResponse;
import gg.modl.backend.player.dto.response.PunishmentResponse;
import gg.modl.backend.player.dto.response.PunishmentSearchResult;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.service.EvidenceUploadTokenService;
import gg.modl.backend.settings.data.DefaultPunishmentTypes;
import gg.modl.backend.settings.data.DurationDetail;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PunishmentService {
    private final PlayerMongoRepository playerRepository;
    private final TicketMongoRepository ticketRepository;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final OffenderThresholdSettingsService thresholdSettingsService;
    private final EvidenceUploadTokenService evidenceUploadTokenService;

    public String createPunishment(Server server, UUID playerUuid, CreatePunishmentRequest request) {
        Player player = findPlayerByUuid(server, playerUuid).orElse(null);

        if (player == null) {
            throw new IllegalArgumentException("Player not found");
        }

        Player original = playerRepository.snapshot(player);
        Date now = new Date();
        Map<String, Object> data = request.data() != null ? new HashMap<>(request.data()) : new HashMap<>();

        if (request.severity() != null) {
            data.put("severity", request.severity());
        }
        if (request.status() != null) {
            data.put("status", request.status());
        }

        // Calculate duration from severity if not explicitly provided
        Long calculatedDuration = request.duration();
        if (calculatedDuration == null && request.severity() != null) {
            List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
            PunishmentType punishmentType = types.stream()
                    .filter(t -> t.getOrdinal() == request.typeOrdinal())
                    .findFirst()
                    .orElse(null);

            if (punishmentType != null) {
                OffenderThresholdSettings thresholds = thresholdSettingsService.getThresholdSettings(server);
                PlayerStatusCalculator.PlayerStatus currentStatus = statusCalculator.calculateStatus(server, player.getPunishments());

                boolean isSocial = punishmentType.isSocial();
                int relevantPoints = isSocial ? currentStatus.socialPoints() : currentStatus.gameplayPoints();
                String offenseLevel = thresholds.getOffenseLevelInternal(relevantPoints, isSocial);

                String internalSeverity = switch (request.severity().toLowerCase()) {
                    case "low", "lenient" -> "low";
                    case "regular" -> "regular";
                    case "aggravated", "severe" -> "severe";
                    default -> "regular";
                };

                DurationDetail durationDetail = punishmentType.getDurationDetail(internalSeverity, offenseLevel);

                if (durationDetail == null) {
                    PunishmentType defaultType = DefaultPunishmentTypes.getAll().stream()
                            .filter(t -> t.getOrdinal() == request.typeOrdinal())
                            .findFirst()
                            .orElse(null);
                    if (defaultType != null) {
                        durationDetail = defaultType.getDurationDetail(internalSeverity, offenseLevel);
                    }
                }

                data.put("offenseLevel", offenseLevel);

                if (durationDetail != null) {
                    long durationMs = durationDetail.toMilliseconds();
                    if (durationMs != 0) {
                        calculatedDuration = durationMs;
                    }
                }
            }
        }

        // Fallback: check data map for duration (panel sends duration inside data for manual punishments)
        if (calculatedDuration == null) {
            Object dataDuration = data.get("duration");
            if (dataDuration instanceof Number n) {
                calculatedDuration = n.longValue();
            }
        }

        if (calculatedDuration != null && calculatedDuration != 0) {
            data.put("duration", calculatedDuration);
        }
        if (request.reason() != null && !request.reason().isBlank()) {
            data.put("reason", request.reason());
        }
        // Queue as "Unstarted" if player already has an active or unstarted punishment in the same category (ban/mute)
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        PunishmentType newPunishmentType = types.stream()
                .filter(t -> t.getOrdinal() == request.typeOrdinal())
                .findFirst()
                .orElse(null);

        // Auto-populate restriction data for Bad Username / Bad Skin punishments
        if (newPunishmentType != null) {
            if (newPunishmentType.isPermanentUntilUsernameChange() && !data.containsKey("blockedName")) {
                String currentUsername = player.getUsernames().isEmpty() ? null :
                        player.getUsernames().get(player.getUsernames().size() - 1).username();
                if (currentUsername != null) {
                    data.put("blockedName", currentUsername);
                }
            }
            if (newPunishmentType.isPermanentUntilSkinChange() && !data.containsKey("blockedSkin")) {
                Object skinHash = player.getData() != null ? player.getData().get("lastSkinHash") : null;
                if (skinHash instanceof String s) {
                    data.put("blockedSkin", s);
                }
            }
        }

        String newCategory = statusCalculator.getEffectiveCategory(newPunishmentType, data);
        if (newCategory != null) {
            boolean hasExistingInCategory = player.getPunishments().stream().anyMatch(existing -> {
                String existingCategory = statusCalculator.getEffectiveCategory(existing, types);
                if (!newCategory.equals(existingCategory)) return false;

                boolean active = statusCalculator.isPunishmentActive(existing);
                boolean unstarted = isUnstarted(existing);
                return active || unstarted;
            });

            if (hasExistingInCategory) {
                data.put("status", "Unstarted");
            }
        }

        // Build notes
        List<PunishmentNote> notes = new ArrayList<>();
        String enforcementType = newPunishmentType != null && newPunishmentType.isKick() ? "kick"
                : "BAN".equals(newCategory) ? "ban"
                : "MUTE".equals(newCategory) ? "mute"
                : "punishment";
        String issuedNote = calculatedDuration != null && calculatedDuration > 0
                ? "issued " + PunishmentMapper.formatDuration(calculatedDuration, false) + " " + enforcementType
                : "issued permanent " + enforcementType;
        if ("kick".equals(enforcementType)) {
            issuedNote = "issued kick";
        }
        notes.add(new PunishmentNote(
                new ObjectId().toHexString(),
                issuedNote,
                now,
                request.issuerName()
        ));
        if (request.reason() != null && !request.reason().isBlank()) {
            notes.add(new PunishmentNote(
                    new ObjectId().toHexString(),
                    request.reason(),
                    now,
                    request.issuerName()
            ));
        }
        if (request.notes() != null) {
            for (CreateNoteRequest noteRequest : request.notes()) {
                String issuer = noteRequest.issuerName() != null ? noteRequest.issuerName() : request.issuerName();
                notes.add(new PunishmentNote(new ObjectId().toHexString(), noteRequest.text(), now, issuer));
            }
        }

        // Build evidence
        List<PunishmentEvidence> evidence = new ArrayList<>();
        if (request.evidence() != null) {
            for (CreateEvidenceRequest evidenceRequest : request.evidence()) {
                String issuer = evidenceRequest.issuerName() != null ? evidenceRequest.issuerName() : request.issuerName();
                String type = evidenceRequest.type() != null ? evidenceRequest.type() : "text";
                evidence.add(new PunishmentEvidence(
                        evidenceRequest.text(),
                        evidenceRequest.fileUrl(),
                        type,
                        issuer,
                        now,
                        evidenceRequest.fileName(),
                        evidenceRequest.fileType(),
                        evidenceRequest.fileSize()
                ));
            }
        }

        String punishmentId = IdGenerator.generate();

        // Set started = null so punishments only begin when the plugin acknowledges them
        // (i.e., when the player is online). This applies to all sources (panel, plugin, etc.).
        // Queued ("Unstarted") and kick punishments also remain null.
        Boolean.TRUE.equals(data.remove("pendingAcknowledgement"));
        Date startedDate = null;

        Punishment punishment = new Punishment(
                punishmentId,
                request.typeOrdinal(),
                request.issuerName(),
                now,
                startedDate,
                new ArrayList<>(),
                notes,
                evidence,
                request.attachedTicketIds() != null ? request.attachedTicketIds() : new ArrayList<>(),
                data
        );

        ensurePlayerPunishments(player).add(punishment);
        playerRepository.saveChanges(server, original, player);

        // Close attached tickets with a system reply
        if (request.attachedTicketIds() != null && !request.attachedTicketIds().isEmpty()) {
            closeAttachedTickets(server, request.attachedTicketIds(), request.issuerName());
        }

        return punishmentId;
    }

    public Player addModification(Server server, UUID playerUuid, String punishmentId, AddModificationRequest request) {
        Player player = findPlayerByUuid(server, playerUuid).orElse(null);
        if (player == null) {
            return null;
        }

        Date now = new Date();

        PunishmentModification modification = new PunishmentModification(
                new ObjectId().toHexString(),
                request.type(),
                now,
                request.issuerName(),
                request.reason() != null ? request.reason() : "",
                request.effectiveDuration(),
                request.appealTicketId(),
                null
        );

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            return null;
        }

        Player original = playerRepository.snapshot(player);
        ensurePunishmentCollections(punishment);
        punishment.getModifications().add(modification);
        if (request.effectiveDuration() != null) {
            if (punishment.getStarted() == null) {
                punishment.setStarted(now);
            }
        }

        return playerRepository.saveChanges(server, original, player);
    }

    public List<PunishmentResponse> getActivePunishments(Server server, UUID playerUuid) {
        Player player = findPlayerByUuid(server, playerUuid).orElse(null);
        if (player == null) {
            return new ArrayList<>();
        }

        return player.getPunishments().stream()
                .filter(statusCalculator::isPunishmentActive)
                .map(p -> toPunishmentResponse(server, p))
                .toList();
    }

    public Optional<PunishmentResponse> getPunishmentById(Server server, String punishmentId) {
        return findPunishmentContext(server, punishmentId)
                .map(context -> toPunishmentResponseWithPlayer(server, context.punishment(), context.player()));
    }

    public Optional<Map<String, Object>> getMinecraftPunishmentById(Server server, String punishmentId) {
        return findPunishmentContext(server, punishmentId).map(context -> {
            List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
            Map<String, Object> result = PunishmentMapper.toPunishmentMap(context.punishment(), types);
            result.put("playerUuid", context.player().getMinecraftUuid().toString());
            result.put("playerName", getLatestUsername(context.player()));
            return result;
        });
    }

    public Optional<String> createEvidenceUploadToken(Server server, String punishmentId, String issuerName) {
        return findPunishmentContext(server, punishmentId)
                .map(context -> evidenceUploadTokenService.createToken(
                        server,
                        punishmentId,
                        context.player().getMinecraftUuid().toString(),
                        issuerName != null ? issuerName : "Unknown"
                ));
    }

    public List<Map<String, Object>> getRecentPunishments(Server server, int hours) {
        Date cutoff = new Date(System.currentTimeMillis() - (hours * 60L * 60L * 1000L));
        Query query = Query.query(MongoQueries.where(PlayerFields.PUNISHMENT_ISSUED).gte(cutoff));
        List<Player> players = playerRepository.find(server, query);
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);

        List<Map<String, Object>> punishments = new ArrayList<>();
        for (Player player : players) {
            String username = getLatestUsername(player);
            for (Punishment punishment : player.getPunishments()) {
                if (punishment.getIssued() == null || !punishment.getIssued().after(cutoff)) {
                    continue;
                }

                Map<String, Object> punishmentMap = PunishmentMapper.toPunishmentMap(punishment, types);
                punishmentMap.put("playerName", username);
                punishmentMap.put("playerUuid", player.getMinecraftUuid().toString());
                punishments.add(punishmentMap);
            }
        }

        punishments.sort((left, right) -> ((Date) right.get("issued")).compareTo((Date) left.get("issued")));
        return punishments.size() > 100 ? punishments.subList(0, 100) : punishments;
    }

    public PunishmentPreviewResponse previewPunishment(Server server, String playerUuid, int typeOrdinal) {
        Player player = playerRepository.findOne(server, Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).is(playerUuid)))
                .orElse(null);
        if (player == null) {
            return PunishmentPreviewResponse.error("Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        PunishmentType punishmentType = types.stream()
                .filter(type -> type.getOrdinal() == typeOrdinal)
                .findFirst()
                .orElse(null);
        if (punishmentType == null) {
            return PunishmentPreviewResponse.error("Punishment type not found");
        }

        OffenderThresholdSettings thresholds = thresholdSettingsService.getThresholdSettings(server);
        PlayerStatusCalculator.PlayerStatus currentStatus = statusCalculator.calculateStatus(server, player.getPunishments());

        boolean isSocial = punishmentType.isSocial();
        int relevantPoints = isSocial ? currentStatus.socialPoints() : currentStatus.gameplayPoints();
        String offenseLevel = thresholds.getOffenseLevelInternal(relevantPoints, isSocial);
        String socialOffenderLevel = thresholds.getSocialOffenderLevel(currentStatus.socialPoints());
        String gameplayOffenderLevel = thresholds.getGameplayOffenderLevel(currentStatus.gameplayPoints());

        PunishmentPreviewResponse.PunishmentPreviewResponseBuilder builder = PunishmentPreviewResponse.builder()
                .status(200)
                .success(true)
                .socialStatus(socialOffenderLevel)
                .gameplayStatus(gameplayOffenderLevel)
                .socialPoints(currentStatus.socialPoints())
                .gameplayPoints(currentStatus.gameplayPoints())
                .offenseLevel(offenseLevel)
                .singleSeverityPunishment(punishmentType.isSingleSeverityPunishment())
                .permanentUntilUsernameChange(punishmentType.isPermanentUntilUsernameChange())
                .permanentUntilSkinChange(punishmentType.isPermanentUntilSkinChange())
                .canBeAltBlocking(punishmentType.isCanBeAltBlocking())
                .canBeStatWiping(punishmentType.isCanBeStatWiping())
                .category(punishmentType.getCategory());

        if (punishmentType.isSingleSeverityPunishment()
                || punishmentType.isPermanentUntilUsernameChange()
                || punishmentType.isPermanentUntilSkinChange()) {
            builder.singleSeverity(buildSeverityPreview(punishmentType, "regular", offenseLevel, currentStatus, isSocial, thresholds));
        } else {
            builder.lenient(buildSeverityPreview(punishmentType, "low", offenseLevel, currentStatus, isSocial, thresholds));
            builder.regular(buildSeverityPreview(punishmentType, "regular", offenseLevel, currentStatus, isSocial, thresholds));
            builder.aggravated(buildSeverityPreview(punishmentType, "severe", offenseLevel, currentStatus, isSocial, thresholds));
        }

        return builder.build();
    }

    public PunishmentOperationResult acknowledgePunishment(Server server, UUID playerUuid, String punishmentId) {
        Player player = findPlayerByUuid(server, playerUuid).orElse(null);
        if (player == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Player not found: " + playerUuid, false, 0);
        }

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND,
                    "Punishment not found: " + punishmentId + " for player: " + playerUuid, false, 0);
        }

        if (punishment.getStarted() != null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NO_OP, "Punishment already acknowledged", true, 0);
        }

        Player original = playerRepository.snapshot(player);
        punishment.setStarted(new Date());
        if (punishment.getData() != null) {
            punishment.getData().remove("status");
        }
        playerRepository.saveChanges(server, original, player);

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Punishment acknowledged", true, 1);
    }

    public PunishmentOperationResult pardonPunishment(Server server, String punishmentId, String issuerName, String reason) {
        PunishmentContext context = findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Punishment punishment = context.punishment();
        if (isPardoned(punishment)) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NO_OP,
                    "Punishment has already been pardoned", false, 0);
        }

        Player original = playerRepository.snapshot(context.player());
        Date now = new Date();
        ensurePunishmentCollections(punishment);

        punishment.getModifications().add(new PunishmentModification(
                new ObjectId().toHexString(),
                "MANUAL_PARDON",
                now,
                issuerName,
                reason != null ? reason : "",
                null,
                null,
                null
        ));

        punishment.getNotes().add(new PunishmentNote(
                new ObjectId().toHexString(),
                "pardoned punishment",
                now,
                issuerName
        ));
        if (reason != null && !reason.isBlank()) {
            punishment.getNotes().add(new PunishmentNote(
                    new ObjectId().toHexString(),
                    reason,
                    now,
                    issuerName
            ));
        }

        ensurePunishmentData(punishment).put("status", "Pardoned");
        playerRepository.saveChanges(server, original, context.player());

        if (Boolean.TRUE.equals(punishment.getData().get("altBlocking"))) {
            cascadePardonLinkedBans(server, punishmentId);
        }

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Punishment pardoned", true, 1);
    }

    public PunishmentOperationResult addPunishmentNote(Server server, String punishmentId, String text, String issuerName) {
        PunishmentContext context = findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Player original = playerRepository.snapshot(context.player());
        ensurePunishmentCollections(context.punishment());
        context.punishment().getNotes().add(new PunishmentNote(new ObjectId().toHexString(), text, new Date(), issuerName));
        playerRepository.saveChanges(server, original, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Note added", true, 1);
    }

    public PunishmentOperationResult addEvidence(Server server, String punishmentId, String evidenceUrl, String issuerName) {
        PunishmentContext context = findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Player original = playerRepository.snapshot(context.player());
        Date now = new Date();
        ensurePunishmentCollections(context.punishment());
        context.punishment().getEvidence().add(new PunishmentEvidence(
                null,
                evidenceUrl,
                "url",
                issuerName,
                now,
                null,
                null,
                null
        ));
        context.punishment().getNotes().add(new PunishmentNote(
                new ObjectId().toHexString(),
                "added evidence",
                now,
                issuerName
        ));
        playerRepository.saveChanges(server, original, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Evidence added", true, 1);
    }

    public PunishmentOperationResult addUploadedEvidence(Server server, String punishmentId, String issuerName, List<UploadedEvidenceItem> evidenceItems) {
        PunishmentContext context = findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Player original = playerRepository.snapshot(context.player());
        Date now = new Date();
        ensurePunishmentCollections(context.punishment());
        for (UploadedEvidenceItem evidenceItem : evidenceItems) {
            context.punishment().getEvidence().add(new PunishmentEvidence(
                    null,
                    evidenceItem.url(),
                    "file",
                    issuerName,
                    now,
                    evidenceItem.fileName(),
                    evidenceItem.fileType(),
                    evidenceItem.fileSize()
            ));
        }
        context.punishment().getNotes().add(new PunishmentNote(
                new ObjectId().toHexString(),
                "uploaded " + evidenceItems.size() + " evidence file(s)",
                now,
                issuerName
        ));
        playerRepository.saveChanges(server, original, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Evidence uploaded successfully", true, evidenceItems.size());
    }

    public PunishmentOperationResult changeDuration(Server server, String punishmentId, Long newDuration, String issuerName) {
        PunishmentContext context = findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Player original = playerRepository.snapshot(context.player());
        Punishment punishment = context.punishment();
        Date now = new Date();
        ensurePunishmentCollections(punishment);

        punishment.getModifications().add(new PunishmentModification(
                new ObjectId().toHexString(),
                "MANUAL_DURATION_CHANGE",
                now,
                issuerName,
                "Duration changed",
                newDuration,
                null,
                null
        ));

        String durationText = newDuration == null || newDuration < 0
                ? "permanent"
                : PunishmentMapper.formatDuration(newDuration, false);
        punishment.getNotes().add(new PunishmentNote(
                new ObjectId().toHexString(),
                "changed duration to " + durationText,
                now,
                issuerName
        ));
        ensurePunishmentData(punishment).put("duration", newDuration);
        if (punishment.getStarted() == null) {
            punishment.setStarted(now);
        }

        playerRepository.saveChanges(server, original, context.player());

        if (Boolean.TRUE.equals(punishment.getData().get("altBlocking"))) {
            int cascaded = cascadeDurationChangeToLinkedBans(server, punishmentId, newDuration, issuerName);
            if (cascaded > 0) {
                return new PunishmentOperationResult(
                        PunishmentOperationStatus.SUCCESS,
                        "Duration changed (cascaded to " + cascaded + " linked ban" + (cascaded > 1 ? "s" : "") + ")",
                        true,
                        cascaded + 1
                );
            }
        }

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Duration changed", true, 1);
    }

    public PunishmentOperationResult toggleOption(Server server, String punishmentId, String option, boolean enabled, String issuerName) {
        PunishmentToggleOption toggleOption = PunishmentToggleOption.from(option);
        if (toggleOption == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.INVALID_REQUEST, "Invalid option", false, 0);
        }

        PunishmentContext context = findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Player original = playerRepository.snapshot(context.player());
        Punishment punishment = context.punishment();
        Date now = new Date();
        ensurePunishmentCollections(punishment);
        ensurePunishmentData(punishment).put(toggleOption.dataKey, enabled);
        punishment.getNotes().add(new PunishmentNote(
                new ObjectId().toHexString(),
                (enabled ? "enabled " : "disabled ") + toggleOption.displayName,
                now,
                issuerName
        ));
        playerRepository.saveChanges(server, original, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Option toggled", true, 1);
    }

    public PunishmentOperationResult acknowledgeStatWipe(Server server, String punishmentId) {
        PunishmentContext context = findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Map<String, Object> data = context.punishment().getData();
        if (data == null || !Boolean.TRUE.equals(data.get("wipeAfterExpiry"))) {
            return new PunishmentOperationResult(
                    PunishmentOperationStatus.NO_OP,
                    "Stat wipe no longer enabled for this punishment",
                    false,
                    0
            );
        }

        Player original = playerRepository.snapshot(context.player());
        Map<String, Object> updatedData = ensurePunishmentData(context.punishment());
        updatedData.put("statWipeCompleted", true);
        updatedData.put("statWipeCompletedAt", new Date());
        playerRepository.saveChanges(server, original, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Stat wipe acknowledged", true, 1);
    }

    public PunishmentOperationResult modifyPunishmentTickets(Server server, String punishmentId, ModifyPunishmentTicketsRequest request) {
        PunishmentContext context = findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Failed to modify punishment tickets", false, 0);
        }

        Player updated = modifyPunishmentTickets(server, context.player().getMinecraftUuid(), punishmentId, request);
        if (updated == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Failed to modify punishment tickets", false, 0);
        }

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Punishment tickets modified", true, 1);
    }

    public Optional<PunishmentContext> findPunishmentContext(Server server, String punishmentId) {
        return playerRepository.findOne(server, Query.query(MongoQueries.where(PlayerFields.PUNISHMENT_ID).is(punishmentId)))
                .flatMap(player -> player.getPunishments().stream()
                        .filter(punishment -> punishmentId.equals(punishment.getId()))
                        .findFirst()
                        .map(punishment -> new PunishmentContext(player, punishment)));
    }

    public List<PunishmentSearchResult> searchPunishments(Server server, String searchQuery, boolean activeOnly) {
        List<PunishmentSearchResult> results = new ArrayList<>();

        Pattern pattern = Pattern.compile(Pattern.quote(searchQuery), Pattern.CASE_INSENSITIVE);
        Query query = Query.query(MongoQueries.where(PlayerFields.PUNISHMENTS).exists(true));
        query.limit(50);

        List<Player> players = playerRepository.find(server, query);

        for (Player player : players) {
            String username = player.getUsernames().isEmpty() ? "Unknown" :
                    player.getUsernames().get(player.getUsernames().size() - 1).username();

            for (Punishment punishment : player.getPunishments()) {
                if (activeOnly && !statusCalculator.isPunishmentActive(punishment)) {
                    continue;
                }

                boolean matches = punishment.getId().contains(searchQuery) ||
                        (punishment.getIssuerName() != null && pattern.matcher(punishment.getIssuerName()).find());

                Map<String, Object> pData = punishment.getData();
                if (pData != null) {
                    String reason = (String) pData.get("reason");
                    if (reason != null && pattern.matcher(reason).find()) {
                        matches = true;
                    }
                }

                if (matches) {
                    results.add(new PunishmentSearchResult(
                            punishment.getId(),
                            username,
                            punishment.getTypeOrdinal(),
                            statusCalculator.isPunishmentActive(punishment) ? "Active" : "Inactive",
                            punishment.getIssued()
                    ));

                    if (results.size() >= 20) {
                        return results;
                    }
                }
            }
        }

        return results;
    }

    public Player addPunishmentNote(Server server, UUID playerUuid, String punishmentId, String text, String issuerName) {
        Player player = findPlayerByUuid(server, playerUuid).orElse(null);
        if (player == null) {
            return null;
        }

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            return null;
        }

        Player original = playerRepository.snapshot(player);
        ensurePunishmentCollections(punishment);
        punishment.getNotes().add(new PunishmentNote(new ObjectId().toHexString(), text, new Date(), issuerName));
        return playerRepository.saveChanges(server, original, player);
    }

    public Player addEvidence(Server server, UUID playerUuid, String punishmentId, AddEvidenceRequest request) {
        Player player = findPlayerByUuid(server, playerUuid).orElse(null);
        if (player == null) {
            return null;
        }

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            return null;
        }

        Player original = playerRepository.snapshot(player);
        ensurePunishmentCollections(punishment);
        punishment.getEvidence().add(new PunishmentEvidence(
                request.text(),
                request.url(),
                request.type(),
                request.issuerName() != null ? request.issuerName() : "System",
                new Date(),
                request.fileName(),
                request.fileType(),
                request.fileSize()
        ));

        return playerRepository.saveChanges(server, original, player);
    }

    /**
     * Promotes the oldest unstarted punishment in each category (BAN, MUTE) if no active punishment exists.
     * @return list of promoted punishment IDs
     */
    public List<String> promoteUnstartedPunishments(Server server, Player player) {
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        List<String> promotedIds = new ArrayList<>();

        for (String category : List.of("BAN", "MUTE")) {
            boolean hasActive = player.getPunishments().stream().anyMatch(p -> {
                String effectiveCategory = statusCalculator.getEffectiveCategory(p, types);
                return category.equals(effectiveCategory) && statusCalculator.isPunishmentActive(p);
            });

            if (hasActive) {
                continue;
            }

            // Find the oldest unstarted punishment in this category
            Optional<Punishment> oldest = player.getPunishments().stream()
                    .filter(p -> {
                        String effectiveCategory = statusCalculator.getEffectiveCategory(p, types);
                        return category.equals(effectiveCategory) && isUnstarted(p);
                    })
                    .min((a, b) -> a.getIssued().compareTo(b.getIssued()));

            if (oldest.isPresent()) {
                Punishment toPromote = oldest.get();
                Player freshPlayer = findPlayerByUuid(server, player.getMinecraftUuid()).orElse(null);
                if (freshPlayer != null) {
                    Player original = playerRepository.snapshot(freshPlayer);
                    for (Punishment p : freshPlayer.getPunishments()) {
                        if (p.getId().equals(toPromote.getId())) {
                            if (p.getData() != null) {
                                p.getData().remove("status");
                            }
                            break;
                        }
                    }
                    playerRepository.saveChanges(server, original, freshPlayer);
                    promotedIds.add(toPromote.getId());
                }
            }
        }

        return promotedIds;
    }

    // ===== Login enforcement: linked bans, restriction auto-pardons =====

    /**
     * Check linked accounts for active alt-blocking bans and create a LinkedBan (ordinal 4) if needed.
     * @return list of created LinkedBan punishment IDs
     */
    public List<String> enforceAltBlockingBans(Server server, Player player) {
        List<String> createdIds = new ArrayList<>();

        List<String> linkedUuids = getLinkedAccountUuids(player);
        if (linkedUuids.isEmpty()) return createdIds;

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);

        // Skip if player already has an active ban
        boolean alreadyBanned = player.getPunishments().stream().anyMatch(p -> {
            String cat = statusCalculator.getEffectiveCategory(p, types);
            return "BAN".equals(cat) && statusCalculator.isPunishmentActive(p);
        });
        if (alreadyBanned) return createdIds;

        // Fetch linked players and check for alt-blocking bans
        Query batchQuery = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).in(linkedUuids));
        List<Player> linkedPlayers = playerRepository.find(server, batchQuery);

        for (Player linkedPlayer : linkedPlayers) {
            for (Punishment punishment : linkedPlayer.getPunishments()) {
                if (!statusCalculator.isPunishmentActive(punishment)) continue;

                Map<String, Object> data = punishment.getData();
                if (data == null) continue;

                Boolean altBlocking = data.get("altBlocking") instanceof Boolean b ? b : null;
                if (!Boolean.TRUE.equals(altBlocking)) continue;

                // Verify this is a ban-category punishment
                String cat = statusCalculator.getEffectiveCategory(punishment, types);
                if (!"BAN".equals(cat)) continue;

                // Calculate remaining duration from parent ban
                Date parentExpiry = statusCalculator.getEffectiveExpiry(punishment);
                Long linkedDuration = null;
                if (parentExpiry != null) {
                    long remaining = parentExpiry.getTime() - System.currentTimeMillis();
                    if (remaining <= 0) continue; // Parent ban effectively expired
                    linkedDuration = remaining;
                }
                // null parentExpiry = permanent, linkedDuration stays null (permanent)

                String linkedBanId = createLinkedBanPunishment(
                        server, player.getMinecraftUuid(),
                        punishment.getId(),
                        linkedPlayer.getMinecraftUuid().toString(),
                        linkedDuration
                );
                createdIds.add(linkedBanId);

                // Only create one linked ban per login
                return createdIds;
            }
        }

        return createdIds;
    }

    /**
     * Check and auto-pardon restriction punishments if the condition has been resolved.
     * Bad Username (ordinal 10): pardoned when username changes.
     * Bad Skin (ordinal 11): pardoned when skin changes.
     * @return list of auto-pardoned punishment IDs
     */
    public List<String> checkRestrictionAutoPardons(Server server, Player player, String currentUsername, String currentSkinHash) {
        List<String> pardonedIds = new ArrayList<>();
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);

        for (Punishment punishment : player.getPunishments()) {
            if (!statusCalculator.isPunishmentActive(punishment)) continue;

            int ordinal = punishment.getTypeOrdinal();
            PunishmentType type = types.stream()
                    .filter(t -> t.getOrdinal() == ordinal)
                    .findFirst()
                    .orElse(null);
            if (type == null) continue;

            Map<String, Object> data = punishment.getData();
            if (data == null) continue;

            // Bad Username: permanentUntilUsernameChange
            if (type.isPermanentUntilUsernameChange() && currentUsername != null) {
                String blockedName = data.get("blockedName") instanceof String s ? s : null;
                if (blockedName != null && !blockedName.equalsIgnoreCase(currentUsername)) {
                    String reason = "Auto-pardoned: username changed from '" + blockedName + "' to '" + currentUsername + "'";
                    systemPardonPunishment(server, player.getMinecraftUuid(), punishment.getId(), reason);
                    pardonedIds.add(punishment.getId());
                }
            }

            // Bad Skin: permanentUntilSkinChange
            if (type.isPermanentUntilSkinChange() && currentSkinHash != null) {
                String blockedSkin = data.get("blockedSkin") instanceof String s ? s : null;
                if (blockedSkin != null && !blockedSkin.equals(currentSkinHash)) {
                    String reason = "Auto-pardoned: skin changed";
                    systemPardonPunishment(server, player.getMinecraftUuid(), punishment.getId(), reason);
                    pardonedIds.add(punishment.getId());
                }
            }
        }

        return pardonedIds;
    }

    /**
     * Add a SYSTEM_PARDON modification to a punishment.
     */
    public void systemPardonPunishment(Server server, UUID playerUuid, String punishmentId, String reason) {
        Player player = findPlayerByUuid(server, playerUuid).orElse(null);
        if (player == null) {
            return;
        }

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            return;
        }

        Player original = playerRepository.snapshot(player);
        addSystemPardon(punishment, reason, new Date());
        playerRepository.saveChanges(server, original, player);
    }

    /**
     * When a parent ban with altBlocking is pardoned, cascade pardon all LinkedBans referencing it.
     * @return number of linked bans pardoned
     */
    public int cascadePardonLinkedBans(String databaseName, String parentPunishmentId) {
        return cascadePardonLinkedBansInternal(databaseName, parentPunishmentId);
    }

    public int cascadePardonLinkedBans(Server server, String parentPunishmentId) {
        return cascadePardonLinkedBansInternal(server, parentPunishmentId);
    }

    /**
     * When a parent ban with altBlocking has its duration changed, cascade the new duration to all LinkedBans referencing it.
     * @return number of linked bans updated
     */
    public int cascadeDurationChangeToLinkedBans(Server server, String parentPunishmentId, Long newDuration, String issuerName) {
        return cascadeDurationChangeInternal(server, parentPunishmentId, newDuration, issuerName);
    }

    /**
     * Get all LinkedBans referencing a parent punishment.
     * @return list of linked ban info maps with punishmentId, playerUuid, playerName, active
     */
    public List<Map<String, Object>> getLinkedBansForParent(Server server, String parentPunishmentId) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<Player> players = playerRepository.find(server, linkedBanQuery(parentPunishmentId));

        for (Player player : players) {
            String username = player.getUsernames().isEmpty() ? "Unknown" :
                    player.getUsernames().get(player.getUsernames().size() - 1).username();

            for (Punishment punishment : player.getPunishments()) {
                if (punishment.getTypeOrdinal() == 4 &&
                        punishment.getData() != null &&
                        parentPunishmentId.equals(punishment.getData().get("linkedBanId"))) {

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("punishmentId", punishment.getId());
                    entry.put("playerUuid", player.getMinecraftUuid().toString());
                    entry.put("playerName", username);
                    entry.put("active", statusCalculator.isPunishmentActive(punishment));
                    results.add(entry);
                }
            }
        }

        return results;
    }

    private String createLinkedBanPunishment(Server server, UUID playerUuid, String parentPunishmentId, String parentPlayerUuid, Long duration) {
        Player player = findPlayerByUuid(server, playerUuid).orElse(null);
        if (player == null) {
            throw new IllegalArgumentException("Player not found");
        }

        Date now = new Date();
        Map<String, Object> data = new HashMap<>();
        data.put("linkedBanId", parentPunishmentId);
        data.put("linkedBanParentUuid", parentPlayerUuid);
        if (duration != null) {
            data.put("duration", duration);
        }

        String punishmentId = IdGenerator.generate();

        List<PunishmentNote> notes = new ArrayList<>();
        String linkedBanNote = duration != null && duration > 0
                ? "issued " + PunishmentMapper.formatDuration(duration, false) + " ban"
                : "issued permanent ban";
        notes.add(new PunishmentNote(
                new ObjectId().toHexString(),
                linkedBanNote,
                now,
                "System"
        ));
        notes.add(new PunishmentNote(
                new ObjectId().toHexString(),
                "Automatically issued linked ban due to alt-blocking ban on linked account",
                now,
                "System"
        ));

        Punishment punishment = new Punishment(
                punishmentId,
                4, // LinkedBan ordinal
                "System",
                now,
                now, // started immediately
                new ArrayList<>(),
                notes,
                new ArrayList<>(),
                new ArrayList<>(),
                data
        );

        Player original = playerRepository.snapshot(player);
        ensurePlayerPunishments(player).add(punishment);
        playerRepository.saveChanges(server, original, player);

        return punishmentId;
    }

    @SuppressWarnings("unchecked")
    private List<String> getLinkedAccountUuids(Player player) {
        if (player.getData() == null) return List.of();
        Object linkedObj = player.getData().get("linkedAccounts");
        if (linkedObj instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    public Player modifyPunishmentTickets(Server server, UUID playerUuid, String punishmentId, ModifyPunishmentTicketsRequest request) {
        Player player = findPlayerByUuid(server, playerUuid).orElse(null);
        if (player == null) {
            return null;
        }

        Punishment punishment = player.getPunishments().stream()
                .filter(p -> p.getId().equals(punishmentId))
                .findFirst()
                .orElse(null);
        if (punishment == null) {
            return null;
        }

        Player original = playerRepository.snapshot(player);

        List<String> currentIds = punishment.getAttachedTicketIds() != null
                ? new ArrayList<>(punishment.getAttachedTicketIds())
                : new ArrayList<>();

        // Add new ticket IDs (deduplicate)
        if (request.addTicketIds() != null) {
            for (String id : request.addTicketIds()) {
                if (!currentIds.contains(id)) {
                    currentIds.add(id);
                }
            }
        }

        // Remove ticket IDs
        if (request.removeTicketIds() != null) {
            currentIds.removeAll(request.removeTicketIds());
        }

        punishment.setAttachedTicketIds(currentIds);
        playerRepository.saveChanges(server, original, player);

        // Optionally close/reopen the actual tickets
        if (request.modifyAssociatedTickets()) {
            if (request.addTicketIds() != null && !request.addTicketIds().isEmpty()) {
                closeAttachedTickets(server, request.addTicketIds(), request.issuerName());
            }
            if (request.removeTicketIds() != null && !request.removeTicketIds().isEmpty()) {
                reopenAttachedTickets(server, request.removeTicketIds(), request.issuerName());
            }
        }

        return player;
    }

    private void reopenAttachedTickets(Server server, List<String> ticketIds, String issuerName) {
        for (String ticketId : ticketIds) {
            try {
                Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
                if (ticket == null || !ticket.isLocked()) {
                    continue;
                }

                Ticket original = ticketRepository.snapshot(ticket);
                if (ticket.getReplies() == null) {
                    ticket.setReplies(new ArrayList<>());
                }

                TicketReply systemReply = TicketReply.builder()
                        .id(UUID.randomUUID().toString())
                        .name(issuerName)
                        .content("Ticket reopened - punishment association removed.")
                        .type("public")
                        .created(new Date())
                        .staff(true)
                        .action("report_reopened")
                        .attachments(new ArrayList<>())
                        .build();

                ticket.getReplies().add(systemReply);
                ticket.setLocked(false);
                ticket.setStatus("Open");
                ticket.setUpdatedAt(new Date());
                ticketRepository.saveChanges(server, original, ticket);
            } catch (Exception e) {
                log.error("[TICKET_REOPEN] Failed to reopen ticket {}: {}", ticketId, e.getMessage());
            }
        }
    }

    private void closeAttachedTickets(Server server, List<String> ticketIds, String issuerName) {
        for (String ticketId : ticketIds) {
            try {
                Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
                if (ticket == null || ticket.isLocked()) {
                    continue;
                }

                Ticket original = ticketRepository.snapshot(ticket);
                if (ticket.getReplies() == null) {
                    ticket.setReplies(new ArrayList<>());
                }

                TicketReply systemReply = TicketReply.builder()
                        .id(UUID.randomUUID().toString())
                        .name(issuerName)
                        .content("Report accepted - punishment has been issued.")
                        .type("public")
                        .created(new Date())
                        .staff(true)
                        .action("report_accepted")
                        .attachments(new ArrayList<>())
                        .build();

                ticket.getReplies().add(systemReply);
                ticket.setLocked(true);
                ticket.setStatus("Closed");
                ticket.setUpdatedAt(new Date());
                ticketRepository.saveChanges(server, original, ticket);
            } catch (Exception e) {
                log.error("[TICKET_CLOSE] Failed to close ticket {}: {}", ticketId, e.getMessage());
            }
        }
    }

    private PunishmentPreviewResponse.SeverityPreview buildSeverityPreview(
            PunishmentType type,
            String severity,
            String offenseLevel,
            PlayerStatusCalculator.PlayerStatus currentStatus,
            boolean isSocial,
            OffenderThresholdSettings thresholds
    ) {
        int points = type.getPointsForSeverity(severity);
        DurationDetail durationDetail = type.getDurationDetail(severity, offenseLevel);

        PunishmentType defaultType = null;
        if (durationDetail == null || points == 0) {
            defaultType = DefaultPunishmentTypes.getAll().stream()
                    .filter(defaultPunishment -> defaultPunishment.getOrdinal() == type.getOrdinal())
                    .findFirst()
                    .orElse(null);
        }
        if (durationDetail == null && defaultType != null) {
            durationDetail = defaultType.getDurationDetail(severity, offenseLevel);
        }
        if (points == 0 && defaultType != null) {
            points = defaultType.getPointsForSeverity(severity);
        }

        long durationMs = durationDetail != null ? durationDetail.toMilliseconds() : 0L;
        boolean permanent = durationDetail != null && durationDetail.isPermanent();
        String punishmentType = durationDetail != null
                ? (durationDetail.isBan() ? "ban" : (durationDetail.isMute() ? "mute" : "kick"))
                : "unknown";

        int newSocialPoints = currentStatus.socialPoints() + (isSocial ? points : 0);
        int newGameplayPoints = currentStatus.gameplayPoints() + (isSocial ? 0 : points);

        return PunishmentPreviewResponse.SeverityPreview.builder()
                .severity(severity)
                .points(points)
                .durationMs(durationMs)
                .durationFormatted(PunishmentMapper.formatDuration(durationMs, permanent))
                .punishmentType(punishmentType)
                .permanent(permanent)
                .newSocialStatus(thresholds.getSocialOffenderLevel(newSocialPoints))
                .newGameplayStatus(thresholds.getGameplayOffenderLevel(newGameplayPoints))
                .newSocialPoints(newSocialPoints)
                .newGameplayPoints(newGameplayPoints)
                .build();
    }

    private String getLatestUsername(Player player) {
        if (player.getUsernames() == null || player.getUsernames().isEmpty()) {
            return "Unknown";
        }
        return player.getUsernames().get(player.getUsernames().size() - 1).username();
    }

    private Punishment findPunishment(Player player, String punishmentId) {
        if (player.getPunishments() == null || player.getPunishments().isEmpty()) {
            return null;
        }

        return player.getPunishments().stream()
                .filter(punishment -> punishmentId.equals(punishment.getId()))
                .findFirst()
                .orElse(null);
    }

    private void ensurePunishmentCollections(Punishment punishment) {
        if (punishment.getModifications() == null) {
            punishment.setModifications(new ArrayList<>());
        }
        if (punishment.getNotes() == null) {
            punishment.setNotes(new ArrayList<>());
        }
        if (punishment.getEvidence() == null) {
            punishment.setEvidence(new ArrayList<>());
        }
        if (punishment.getAttachedTicketIds() == null) {
            punishment.setAttachedTicketIds(new ArrayList<>());
        }
    }

    private Map<String, Object> ensurePunishmentData(Punishment punishment) {
        if (punishment.getData() == null) {
            punishment.setData(new HashMap<>());
        }
        return punishment.getData();
    }

    private List<Punishment> ensurePlayerPunishments(Player player) {
        if (player.getPunishments() == null) {
            player.setPunishments(new ArrayList<>());
        }
        return player.getPunishments();
    }

    private boolean isPardoned(Punishment punishment) {
        return punishment.getModifications() != null && punishment.getModifications().stream()
                .anyMatch(modification ->
                        "MANUAL_PARDON".equals(modification.type())
                                || "APPEAL_ACCEPT".equals(modification.type())
                                || "SYSTEM_PARDON".equals(modification.type()));
    }

    private boolean isUnstarted(Punishment punishment) {
        Map<String, Object> data = punishment.getData();
        if (data == null) return false;

        String status = (String) data.get("status");
        if (!"Unstarted".equals(status)) return false;

        // Not unstarted if it was pardoned or appeal-accepted
        for (var mod : punishment.getModifications()) {
            if ("MANUAL_PARDON".equals(mod.type()) || "APPEAL_ACCEPT".equals(mod.type()) || "SYSTEM_PARDON".equals(mod.type())) {
                return false;
            }
        }
        return true;
    }

    private Optional<Player> findPlayerByUuid(Server server, UUID uuid) {
        return playerRepository.findOne(server, Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).is(uuid.toString())));
    }

    private void addSystemPardon(Punishment punishment, String reason, Date now) {
        ensurePunishmentCollections(punishment);
        punishment.getModifications().add(new PunishmentModification(
                new ObjectId().toHexString(),
                "SYSTEM_PARDON",
                now,
                "System",
                reason,
                null,
                null,
                null
        ));
        punishment.getNotes().add(new PunishmentNote(
                new ObjectId().toHexString(),
                reason,
                now,
                "System"
        ));
    }

    private Query linkedBanQuery(String parentPunishmentId) {
        return Query.query(MongoQueries.where(PlayerFields.PUNISHMENTS).elemMatch(
                Criteria.where(PunishmentFields.TYPE_ORDINAL.path()).is(4)
                        .and(PunishmentFields.DATA_LINKED_BAN_ID.path()).is(parentPunishmentId)
        ));
    }

    private int cascadeDurationChangeInternal(Server server, String parentPunishmentId, Long newDuration, String issuerName) {
        int count = 0;

        for (Player player : playerRepository.find(server, linkedBanQuery(parentPunishmentId))) {
            Player original = playerRepository.snapshot(player);
            int updatedPunishments = applyLinkedBanDurationChange(player, parentPunishmentId, newDuration);
            if (updatedPunishments <= 0) {
                continue;
            }

            playerRepository.saveChanges(server, original, player);
            count += updatedPunishments;
        }

        return count;
    }

    private int cascadePardonLinkedBansInternal(Server server, String parentPunishmentId) {
        return cascadePardonLinkedBansInternal(
                playerRepository.find(server, linkedBanQuery(parentPunishmentId)),
                parentPunishmentId,
                player -> playerRepository.saveChanges(server, player.original(), player.updated())
        );
    }

    private int cascadePardonLinkedBansInternal(String databaseName, String parentPunishmentId) {
        return cascadePardonLinkedBansInternal(
                playerRepository.find(databaseName, linkedBanQuery(parentPunishmentId)),
                parentPunishmentId,
                player -> playerRepository.saveChanges(databaseName, player.original(), player.updated())
        );
    }

    private int cascadePardonLinkedBansInternal(
            List<Player> players,
            String parentPunishmentId,
            LinkedBanSaveAction saveAction
    ) {
        int count = 0;

        for (Player player : players) {
            Player original = playerRepository.snapshot(player);
            int pardonedPunishments = applyLinkedBanSystemPardon(player, parentPunishmentId);
            if (pardonedPunishments <= 0) {
                continue;
            }

            saveAction.save(new PlayerSavePair(original, player));
            count += pardonedPunishments;
        }

        return count;
    }

    private int applyLinkedBanDurationChange(Player player, String parentPunishmentId, Long newDuration) {
        int count = 0;

        for (Punishment punishment : ensurePlayerPunishments(player)) {
            if (punishment.getTypeOrdinal() != 4
                    || punishment.getData() == null
                    || !parentPunishmentId.equals(punishment.getData().get("linkedBanId"))
                    || !statusCalculator.isPunishmentActive(punishment)) {
                continue;
            }

            Date now = new Date();
            ensurePunishmentCollections(punishment);
            ensurePunishmentData(punishment).put("duration", newDuration);
            punishment.getModifications().add(new PunishmentModification(
                    new ObjectId().toHexString(),
                    "MANUAL_DURATION_CHANGE",
                    now,
                    "System",
                    "Cascaded from parent ban duration change",
                    newDuration,
                    null,
                    null
            ));
            punishment.getNotes().add(new PunishmentNote(
                    new ObjectId().toHexString(),
                    "Duration changed (cascaded from parent ban)",
                    now,
                    "System"
            ));
            if (punishment.getStarted() == null) {
                punishment.setStarted(now);
            }
            count++;
        }

        return count;
    }

    private int applyLinkedBanSystemPardon(Player player, String parentPunishmentId) {
        int count = 0;

        for (Punishment punishment : ensurePlayerPunishments(player)) {
            if (punishment.getTypeOrdinal() != 4
                    || punishment.getData() == null
                    || !parentPunishmentId.equals(punishment.getData().get("linkedBanId"))
                    || !statusCalculator.isPunishmentActive(punishment)) {
                continue;
            }

            addSystemPardon(punishment, "Auto-pardoned: parent ban was pardoned", new Date());
            count++;
        }

        return count;
    }

    private record PlayerSavePair(Player original, Player updated) {
    }

    @FunctionalInterface
    private interface LinkedBanSaveAction {
        void save(PlayerSavePair player);
    }

    private PunishmentResponse toPunishmentResponse(Server server, Punishment punishment) {
        return toPunishmentResponseWithPlayer(server, punishment, null);
    }

    private PunishmentResponse toPunishmentResponseWithPlayer(Server server, Punishment punishment, Player player) {
        Map<String, Object> data = punishment.getData();
        boolean active = statusCalculator.isPunishmentActive(punishment);
        Date expires = statusCalculator.getEffectiveExpiry(punishment);

        String playerUuid = player != null ? player.getMinecraftUuid().toString() : null;
        String playerUsername = player != null && !player.getUsernames().isEmpty() ?
                player.getUsernames().get(player.getUsernames().size() - 1).username() : null;

        int ordinal = punishment.getTypeOrdinal();

        // Compute effective category (BAN, MUTE, or null) using the uniform calculation
        PunishmentType punishmentType = punishmentTypeService.getPunishmentTypeByOrdinal(server, ordinal).orElse(null);
        String effectiveCategory = statusCalculator.getEffectiveCategory(punishmentType, data);

        return new PunishmentResponse(
                punishment.getId(),
                punishmentTypeService.getPunishmentTypeName(server, ordinal),
                ordinal,
                punishment.getIssuerName(),
                punishment.getIssued(),
                punishment.getStarted(),
                punishmentTypeService.isAppealable(server, ordinal),
                data != null ? (String) data.get("reason") : null,
                data != null ? (String) data.get("severity") : null,
                data != null ? (String) data.get("status") : null,
                active,
                expires,
                playerUuid,
                playerUsername,
                data != null ? (Boolean) data.get("altBlocking") : null,
                data != null ? (Boolean) data.get("wipeAfterExpiry") : null,
                data != null ? (String) data.get("offenseLevel") : null,
                effectiveCategory,
                punishment.getModifications(),
                punishment.getNotes(),
                punishment.getEvidence(),
                punishment.getAttachedTicketIds()
        );
    }

    public record PunishmentContext(Player player, Punishment punishment) {
    }

    public record UploadedEvidenceItem(String url, String fileName, String fileType, Long fileSize) {
    }

    public record PunishmentOperationResult(
            PunishmentOperationStatus status,
            String message,
            boolean success,
            int affectedCount
    ) {
    }

    public enum PunishmentOperationStatus {
        SUCCESS,
        NOT_FOUND,
        INVALID_REQUEST,
        NO_OP
    }

    private enum PunishmentToggleOption {
        ALT_BLOCKING("altBlocking", "alt-blocking"),
        STAT_WIPE("wipeAfterExpiry", "stat wipe");

        private final String dataKey;
        private final String displayName;

        PunishmentToggleOption(String dataKey, String displayName) {
            this.dataKey = dataKey;
            this.displayName = displayName;
        }

        private static PunishmentToggleOption from(String option) {
            if (option == null || option.isBlank()) {
                return null;
            }

            try {
                return PunishmentToggleOption.valueOf(option.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}

