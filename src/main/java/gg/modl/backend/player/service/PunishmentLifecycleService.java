package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.controller.MinecraftPunishmentController.MinecraftCreatePunishmentRequest;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.dto.request.CreateEvidenceRequest;
import gg.modl.backend.player.dto.request.CreateNoteRequest;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentContext;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DefaultPunishmentTypes;
import gg.modl.backend.settings.data.DurationDetail;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PunishmentLifecycleService {
    private final PlayerMongoRepository playerRepository;
    private final TicketMongoRepository ticketRepository;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final OffenderThresholdSettingsService thresholdSettingsService;
    private final IssuerNameResolver issuerNameResolver;
    private final StaffMongoRepository staffRepository;
    private final PunishmentQueryService punishmentQueryService;

    public String createMinecraftPunishment(Server server, MinecraftCreatePunishmentRequest request) {
        UUID playerUuid = UUID.fromString(request.targetUuid());

        List<CreateNoteRequest> noteRequests = null;
        if (request.notes() != null) {
            noteRequests = request.notes().stream()
                    .map(text -> new CreateNoteRequest(text, request.issuerName(), request.issuerId(), null))
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

        return createPunishment(server, playerUuid, serviceRequest);
    }

    public String createPunishment(Server server, UUID playerUuid, CreatePunishmentRequest request) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);

        if (player == null) {
            throw new IllegalArgumentException("Player not found");
        }
        Date now = new Date();
        Map<String, Object> data = request.data() != null ? new HashMap<>(request.data()) : new HashMap<>();

        if (request.severity() != null) {
            data.put("severity", request.severity());
        }
        if (request.status() != null) {
            data.put("status", request.status());
        }

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

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        PunishmentType newPunishmentType = types.stream()
                .filter(t -> t.getOrdinal() == request.typeOrdinal())
                .findFirst()
                .orElse(null);

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

        String reqIssuerName = request.issuerId() != null ? null : request.issuerName();
        String reqIssuerId = request.issuerId();

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
                reqIssuerName,
                reqIssuerId
        ));
        if (request.reason() != null && !request.reason().isBlank()) {
            notes.add(new PunishmentNote(
                    new ObjectId().toHexString(),
                    request.reason(),
                    now,
                    reqIssuerName,
                    reqIssuerId
            ));
        }
        if (request.notes() != null) {
            for (CreateNoteRequest noteRequest : request.notes()) {
                String noteIssuerId = noteRequest.issuerId() != null ? noteRequest.issuerId() : reqIssuerId;
                String noteIssuerName = noteIssuerId != null ? null : (noteRequest.issuerName() != null ? noteRequest.issuerName() : request.issuerName());
                notes.add(new PunishmentNote(new ObjectId().toHexString(), noteRequest.text(), now, noteIssuerName, noteIssuerId));
            }
        }

        List<PunishmentEvidence> evidence = new ArrayList<>();
        if (request.evidence() != null) {
            for (CreateEvidenceRequest evidenceRequest : request.evidence()) {
                String evIssuerId = evidenceRequest.issuerId() != null ? evidenceRequest.issuerId() : reqIssuerId;
                String evIssuerName = evIssuerId != null ? null : (evidenceRequest.issuerName() != null ? evidenceRequest.issuerName() : request.issuerName());
                String type = evidenceRequest.type() != null ? evidenceRequest.type() : "text";
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

        String punishmentId = new ObjectId().toHexString();

        Boolean.TRUE.equals(data.remove("pendingAcknowledgement"));
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

        ensurePlayerPunishments(player).add(punishment);
        persistPlayerPunishments(server, player);

        if (request.attachedTicketIds() != null && !request.attachedTicketIds().isEmpty()) {
            String ticketIssuerName = issuerNameResolver.resolve(request.issuerId(), request.issuerName(), server, staffRepository);
            closeAttachedTickets(server, request.attachedTicketIds(), ticketIssuerName);
        }

        return punishmentId;
    }

    public PunishmentOperationResult acknowledgePunishment(Server server, UUID playerUuid, String punishmentId) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
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

        punishment.setStarted(new Date());
        if (punishment.getData() != null) {
            punishment.getData().remove("status");
        }
        persistPlayerPunishments(server, player);

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Punishment acknowledged", true, 1);
    }

    public PunishmentOperationResult pardonPunishment(Server server, String punishmentId, String issuerName, String issuerId, String reason) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Punishment punishment = context.punishment();
        if (isPardoned(punishment)) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NO_OP,
                    "Punishment has already been pardoned", false, 0);
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;

        Date now = new Date();
        ensurePunishmentCollections(punishment);

        punishment.getModifications().add(new PunishmentModification(
                new ObjectId().toHexString(),
                "MANUAL_PARDON",
                now,
                resolvedIssuerName,
                issuerId,
                reason != null ? reason : "",
                null,
                null,
                null
        ));

        punishment.getNotes().add(new PunishmentNote(
                new ObjectId().toHexString(),
                "pardoned punishment",
                now,
                resolvedIssuerName,
                issuerId
        ));
        if (reason != null && !reason.isBlank()) {
            punishment.getNotes().add(new PunishmentNote(
                    new ObjectId().toHexString(),
                    reason,
                    now,
                    resolvedIssuerName,
                    issuerId
            ));
        }

        ensurePunishmentData(punishment).put("status", "Pardoned");
        persistPlayerPunishments(server, context.player());

        if (Boolean.TRUE.equals(punishment.getData().get("altBlocking"))) {
            cascadePardonLinkedBans(server, punishmentId);
        }

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Punishment pardoned", true, 1);
    }

    public void systemPardonPunishment(Server server, UUID playerUuid, String punishmentId, String reason) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
        if (player == null) {
            return;
        }

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            return;
        }

        addSystemPardon(punishment, reason, new Date());
        persistPlayerPunishments(server, player);
    }

    public int cascadePardonLinkedBans(String databaseName, String parentPunishmentId) {
        return cascadePardonLinkedBansInternal(
                playerRepository.findByLinkedBanId(databaseName, parentPunishmentId),
                parentPunishmentId,
                player -> persistPlayerPunishments(databaseName, player)
        );
    }

    public int cascadePardonLinkedBans(Server server, String parentPunishmentId) {
        return cascadePardonLinkedBansInternal(
                playerRepository.findByLinkedBanId(server, parentPunishmentId),
                parentPunishmentId,
                player -> persistPlayerPunishments(server, player)
        );
    }

    public int cascadeDurationChangeToLinkedBans(Server server, String parentPunishmentId, Long newDuration, String issuerName) {
        int count = 0;

        for (Player player : playerRepository.findByLinkedBanId(server, parentPunishmentId)) {
            int updatedPunishments = applyLinkedBanDurationChange(player, parentPunishmentId, newDuration);
            if (updatedPunishments <= 0) {
                continue;
            }

            persistPlayerPunishments(server, player);
            count += updatedPunishments;
        }

        return count;
    }

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

            Optional<Punishment> oldest = player.getPunishments().stream()
                    .filter(p -> {
                        String effectiveCategory = statusCalculator.getEffectiveCategory(p, types);
                        return category.equals(effectiveCategory) && isUnstarted(p);
                    })
                    .min((a, b) -> a.getIssued().compareTo(b.getIssued()));

            if (oldest.isPresent()) {
                Punishment toPromote = oldest.get();
                Player freshPlayer = playerRepository.findByMinecraftUuid(server, player.getMinecraftUuid().toString()).orElse(null);
                if (freshPlayer != null) {
                    for (Punishment p : freshPlayer.getPunishments()) {
                        if (p.getId().equals(toPromote.getId())) {
                            if (p.getData() != null) {
                                p.getData().remove("status");
                            }
                            break;
                        }
                    }
                    persistPlayerPunishments(server, freshPlayer);
                    promotedIds.add(toPromote.getId());
                }
            }
        }

        return promotedIds;
    }

    public List<String> enforceAltBlockingBans(Server server, Player player) {
        List<String> createdIds = new ArrayList<>();

        List<String> linkedUuids = getLinkedAccountUuids(player);
        if (linkedUuids.isEmpty()) return createdIds;

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);

        boolean alreadyBanned = player.getPunishments().stream().anyMatch(p -> {
            String cat = statusCalculator.getEffectiveCategory(p, types);
            return "BAN".equals(cat) && statusCalculator.isPunishmentActive(p);
        });
        if (alreadyBanned) return createdIds;

        List<Player> linkedPlayers = playerRepository.findByMinecraftUuids(server, linkedUuids);

        for (Player linkedPlayer : linkedPlayers) {
            for (Punishment punishment : linkedPlayer.getPunishments()) {
                if (!statusCalculator.isPunishmentActive(punishment)) continue;

                Map<String, Object> data = punishment.getData();
                if (data == null) continue;

                Boolean altBlocking = data.get("altBlocking") instanceof Boolean b ? b : null;
                if (!Boolean.TRUE.equals(altBlocking)) continue;

                String cat = statusCalculator.getEffectiveCategory(punishment, types);
                if (!"BAN".equals(cat)) continue;

                Date parentExpiry = statusCalculator.getEffectiveExpiry(punishment);
                Long linkedDuration = null;
                if (parentExpiry != null) {
                    long remaining = parentExpiry.getTime() - System.currentTimeMillis();
                    if (remaining <= 0) continue;
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

            if (type.isPermanentUntilUsernameChange() && currentUsername != null) {
                String blockedName = data.get("blockedName") instanceof String s ? s : null;
                if (blockedName != null && !blockedName.equalsIgnoreCase(currentUsername)) {
                    String reason = "Auto-pardoned: username changed from '" + blockedName + "' to '" + currentUsername + "'";
                    systemPardonPunishment(server, player.getMinecraftUuid(), punishment.getId(), reason);
                    pardonedIds.add(punishment.getId());
                }
            }

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

    private String createLinkedBanPunishment(Server server, UUID playerUuid, String parentPunishmentId, String parentPlayerUuid, Long duration) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
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

        String punishmentId = new ObjectId().toHexString();

        List<PunishmentNote> notes = new ArrayList<>();
        String linkedBanNote = duration != null && duration > 0
                ? "issued " + PunishmentMapper.formatDuration(duration, false) + " ban"
                : "issued permanent ban";
        notes.add(new PunishmentNote(
                new ObjectId().toHexString(),
                linkedBanNote,
                now,
                "System",
                null
        ));
        notes.add(new PunishmentNote(
                new ObjectId().toHexString(),
                "Automatically issued linked ban due to alt-blocking ban on linked account",
                now,
                "System",
                null
        ));

        Punishment punishment = new Punishment(
                punishmentId,
                4,
                "System",
                null,
                now,
                now,
                new ArrayList<>(),
                notes,
                new ArrayList<>(),
                new ArrayList<>(),
                data
        );

        ensurePlayerPunishments(player).add(punishment);
        persistPlayerPunishments(server, player);

        return punishmentId;
    }

    private int cascadePardonLinkedBansInternal(
            List<Player> players,
            String parentPunishmentId,
            LinkedBanSaveAction saveAction
    ) {
        int count = 0;

        for (Player player : players) {
            int pardonedPunishments = applyLinkedBanSystemPardon(player, parentPunishmentId);
            if (pardonedPunishments <= 0) {
                continue;
            }

            saveAction.save(player);
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
                    null,
                    "Cascaded from parent ban duration change",
                    newDuration,
                    null,
                    null
            ));
            punishment.getNotes().add(new PunishmentNote(
                    new ObjectId().toHexString(),
                    "Duration changed (cascaded from parent ban)",
                    now,
                    "System",
                    null
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

    private void addSystemPardon(Punishment punishment, String reason, Date now) {
        ensurePunishmentCollections(punishment);
        punishment.getModifications().add(new PunishmentModification(
                new ObjectId().toHexString(),
                "SYSTEM_PARDON",
                now,
                "System",
                null,
                reason,
                null,
                null,
                null
        ));
        punishment.getNotes().add(new PunishmentNote(
                new ObjectId().toHexString(),
                reason,
                now,
                "System",
                null
        ));
    }

    private void closeAttachedTickets(Server server, List<String> ticketIds, String issuerName) {
        for (String ticketId : ticketIds) {
            try {
                Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
                if (ticket == null || ticket.isLocked()) {
                    continue;
                }

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
                applyTicketLifecycleStatus(ticket, TicketStatus.CLOSED);
                ticket.setUpdatedAt(new Date());
                ticketRepository.updateState(server, ticket);
            } catch (Exception e) {
                log.error("[TICKET_CLOSE] Failed to close ticket {}: {}", ticketId, e.getMessage());
            }
        }
    }

    private void applyTicketLifecycleStatus(Ticket ticket, TicketStatus status) {
        ticket.setStatus(status);
        ticket.setLocked(status != null && status.isTerminal());
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

        for (var mod : punishment.getModifications()) {
            if ("MANUAL_PARDON".equals(mod.type()) || "APPEAL_ACCEPT".equals(mod.type()) || "SYSTEM_PARDON".equals(mod.type())) {
                return false;
            }
        }
        return true;
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

    private void persistPlayerPunishments(Server server, Player player) {
        ensurePlayerPunishments(player);
        playerRepository.replacePunishments(server, player);
    }

    private void persistPlayerPunishments(String databaseName, Player player) {
        ensurePlayerPunishments(player);
        playerRepository.replacePunishments(databaseName, player);
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

    @FunctionalInterface
    private interface LinkedBanSaveAction {
        void save(Player player);
    }
}
