package gg.modl.backend.player.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
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
import gg.modl.backend.player.dto.response.PunishmentResponse;
import gg.modl.backend.player.dto.response.PunishmentSearchResult;
import gg.modl.backend.server.data.Server;
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
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PunishmentService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final OffenderThresholdSettingsService thresholdSettingsService;

    public String createPunishment(Server server, UUID playerUuid, CreatePunishmentRequest request) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("minecraftUuid").is(playerUuid.toString()));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

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
                log.info("[CREATE_PUNISHMENT] Checking existing {} ordinal={} effectiveCategory={} active={} unstarted={} data.status={}",
                        existing.getId(), existing.getType_ordinal(), existingCategory, active, unstarted,
                        existing.getData() != null ? existing.getData().get("status") : "null");
                return active || unstarted;
            });

            log.info("[CREATE_PUNISHMENT] New punishment ordinal={} effectiveCategory={} hasExistingInCategory={} -> {}",
                    request.typeOrdinal(), newCategory, hasExistingInCategory,
                    hasExistingInCategory ? "QUEUING as Unstarted" : "ACTIVE");

            if (hasExistingInCategory) {
                data.put("status", "Unstarted");
            }
        }

        // Build notes
        List<PunishmentNote> notes = new ArrayList<>();
        notes.add(new PunishmentNote(
                new ObjectId().toHexString(),
                "issued punishment",
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

        String punishmentId = IdGenerator.generatePunishmentId();

        // Set started = now for non-queued punishments (countdown starts at enforcement time)
        Date startedDate = "Unstarted".equals(data.get("status")) ? null : now;

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

        // Convert to raw Document to avoid Spring Data wrapping embedded @Field("_id") objects in arrays
        Document punishmentDoc = (Document) template.getConverter().convertToMongoType(punishment);
        Update update = new Update().push("punishments", punishmentDoc);
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        // Close attached tickets with a system reply
        if (request.attachedTicketIds() != null && !request.attachedTicketIds().isEmpty()) {
            closeAttachedTickets(template, request.attachedTicketIds(), request.issuerName());
        }

        return punishmentId;
    }

    public Player addModification(Server server, UUID playerUuid, String punishmentId, AddModificationRequest request) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid.toString())
                        .and("punishments.id").is(punishmentId)
        );

        PunishmentModification modification = new PunishmentModification(
                new ObjectId().toHexString(),
                request.type(),
                new Date(),
                request.issuerName(),
                request.reason() != null ? request.reason() : "",
                request.effectiveDuration(),
                request.appealTicketId(),
                null
        );

        Update update = new Update().push("punishments.$.modifications", modification);

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
        return findPlayerByUuid(template, playerUuid);
    }

    public List<PunishmentResponse> getActivePunishments(Server server, UUID playerUuid) {
        MongoTemplate template = getTemplate(server);
        Player player = findPlayerByUuid(template, playerUuid);
        if (player == null) {
            return new ArrayList<>();
        }

        return player.getPunishments().stream()
                .filter(statusCalculator::isPunishmentActive)
                .map(p -> toPunishmentResponse(server, p))
                .toList();
    }

    public Optional<PunishmentResponse> getPunishmentById(Server server, String punishmentId) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("punishments.id").is(punishmentId));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return Optional.empty();
        }

        return player.getPunishments().stream()
                .filter(p -> p.getId().equals(punishmentId))
                .findFirst()
                .map(p -> toPunishmentResponseWithPlayer(server, p, player));
    }

    public List<PunishmentSearchResult> searchPunishments(Server server, String searchQuery, boolean activeOnly) {
        MongoTemplate template = getTemplate(server);
        List<PunishmentSearchResult> results = new ArrayList<>();

        Pattern pattern = Pattern.compile(Pattern.quote(searchQuery), Pattern.CASE_INSENSITIVE);
        Query query = new Query(Criteria.where("punishments").exists(true));
        query.limit(50);

        List<Player> players = template.find(query, Player.class, CollectionName.PLAYERS);

        for (Player player : players) {
            String username = player.getUsernames().isEmpty() ? "Unknown" :
                    player.getUsernames().get(player.getUsernames().size() - 1).username();

            for (Punishment punishment : player.getPunishments()) {
                if (activeOnly && !statusCalculator.isPunishmentActive(punishment)) {
                    continue;
                }

                boolean matches = punishment.getId().contains(searchQuery) ||
                        pattern.matcher(punishment.getIssuerName()).find();

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
                            punishment.getType_ordinal(),
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
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid.toString())
                        .and("punishments.id").is(punishmentId)
        );

        PunishmentNote note = new PunishmentNote(new ObjectId().toHexString(), text, new Date(), issuerName);
        Update update = new Update().push("punishments.$.notes", note);

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
        return findPlayerByUuid(template, playerUuid);
    }

    public Player addEvidence(Server server, UUID playerUuid, String punishmentId, AddEvidenceRequest request) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid.toString())
                        .and("punishments.id").is(punishmentId)
        );

        PunishmentEvidence evidence = new PunishmentEvidence(
                request.text(),
                request.url(),
                request.type(),
                request.issuerName() != null ? request.issuerName() : "System",
                new Date(),
                request.fileName(),
                request.fileType(),
                request.fileSize()
        );

        Update update = new Update().push("punishments.$.evidence", evidence);
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        return findPlayerByUuid(template, playerUuid);
    }

    /**
     * Promotes the oldest unstarted punishment in each category (BAN, MUTE) if no active punishment exists.
     * @return list of promoted punishment IDs
     */
    public List<String> promoteUnstartedPunishments(Server server, Player player) {
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        List<String> promotedIds = new ArrayList<>();

        log.info("[PROMOTE] Checking promotions for player {} with {} punishments",
                player.getMinecraftUuid(), player.getPunishments().size());

        for (String category : List.of("BAN", "MUTE")) {
            boolean hasActive = player.getPunishments().stream().anyMatch(p -> {
                String effectiveCategory = statusCalculator.getEffectiveCategory(p, types);
                return category.equals(effectiveCategory) && statusCalculator.isPunishmentActive(p);
            });

            if (hasActive) {
                log.info("[PROMOTE] Category {} already has active punishment, skipping", category);
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
                MongoTemplate template = getTemplate(server);
                Date now = new Date();

                log.info("[PROMOTE] Promoting {} in category {} (ordinal={}, issued={})",
                        toPromote.getId(), category, toPromote.getType_ordinal(), toPromote.getIssued());

                try {
                    Query query = Query.query(
                            Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                    );
                    Update update = new Update()
                            .set("punishments.$[elem].started", now)
                            .unset("punishments.$[elem].data.status")
                            .filterArray(Criteria.where("elem._id").is(toPromote.getId()));
                    var result = template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

                    log.info("[PROMOTE] MongoDB update result for {}: matched={}, modified={}",
                            toPromote.getId(), result.getMatchedCount(), result.getModifiedCount());

                    promotedIds.add(toPromote.getId());
                } catch (Exception e) {
                    log.error("[PROMOTE] Failed to promote {}, attempting repair", toPromote.getId(), e);
                    // Nested array corruption — repair by rewriting the full punishments array
                    Query findQuery = Query.query(Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString()));
                    Player freshPlayer = template.findOne(findQuery, Player.class, CollectionName.PLAYERS);
                    if (freshPlayer != null) {
                        for (Punishment p : freshPlayer.getPunishments()) {
                            if (p.getId().equals(toPromote.getId())) {
                                p.setStarted(now);
                                if (p.getData() != null) {
                                    p.getData().remove("status");
                                }
                                break;
                            }
                        }
                        Update repairUpdate = new Update().set("punishments", freshPlayer.getPunishments());
                        template.updateFirst(findQuery, repairUpdate, Player.class, CollectionName.PLAYERS);
                        log.info("[PROMOTE] Repaired and promoted {} via full array rewrite", toPromote.getId());
                        promotedIds.add(toPromote.getId());
                    }
                }
            } else {
                log.info("[PROMOTE] No unstarted punishments found for category {}", category);
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
        MongoTemplate template = getTemplate(server);
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
        Query batchQuery = new Query(Criteria.where("minecraftUuid").in(linkedUuids));
        List<Player> linkedPlayers = template.find(batchQuery, Player.class, CollectionName.PLAYERS);

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
                log.info("[ALT_BLOCK] Created LinkedBan {} for {} due to alt-blocking ban {} on {}",
                        linkedBanId, player.getMinecraftUuid(), punishment.getId(), linkedPlayer.getMinecraftUuid());
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

            int ordinal = punishment.getType_ordinal();
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
                    log.info("[RESTRICTION] Auto-pardoned Bad Username {} for {}: {}", punishment.getId(), player.getMinecraftUuid(), reason);
                    pardonedIds.add(punishment.getId());
                }
            }

            // Bad Skin: permanentUntilSkinChange
            if (type.isPermanentUntilSkinChange() && currentSkinHash != null) {
                String blockedSkin = data.get("blockedSkin") instanceof String s ? s : null;
                if (blockedSkin != null && !blockedSkin.equals(currentSkinHash)) {
                    String reason = "Auto-pardoned: skin changed";
                    systemPardonPunishment(server, player.getMinecraftUuid(), punishment.getId(), reason);
                    log.info("[RESTRICTION] Auto-pardoned Bad Skin {} for {}", punishment.getId(), player.getMinecraftUuid());
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
        MongoTemplate template = getTemplate(server);
        Date now = new Date();

        Query query = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid.toString())
                        .and("punishments.id").is(punishmentId)
        );

        PunishmentModification modification = new PunishmentModification(
                new ObjectId().toHexString(),
                "SYSTEM_PARDON",
                now,
                "System",
                reason,
                null,
                null,
                null
        );

        PunishmentNote pardonNote = new PunishmentNote(
                new ObjectId().toHexString(),
                reason,
                now,
                "System"
        );

        Update update = new Update()
                .push("punishments.$.modifications", modification)
                .push("punishments.$.notes", pardonNote);

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
    }

    /**
     * When a parent ban with altBlocking is pardoned, cascade pardon all LinkedBans referencing it.
     * @return number of linked bans pardoned
     */
    public int cascadePardonLinkedBans(String databaseName, String parentPunishmentId) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(databaseName);
        return cascadePardonLinkedBansInternal(template, parentPunishmentId);
    }

    public int cascadePardonLinkedBans(Server server, String parentPunishmentId) {
        MongoTemplate template = getTemplate(server);
        return cascadePardonLinkedBansInternal(template, parentPunishmentId);
    }

    /**
     * When a parent ban with altBlocking has its duration changed, cascade the new duration to all LinkedBans referencing it.
     * @return number of linked bans updated
     */
    public int cascadeDurationChangeToLinkedBans(Server server, String parentPunishmentId, Long newDuration, String issuerName) {
        MongoTemplate template = getTemplate(server);
        return cascadeDurationChangeInternal(template, parentPunishmentId, newDuration, issuerName);
    }

    private int cascadeDurationChangeInternal(MongoTemplate template, String parentPunishmentId, Long newDuration, String issuerName) {
        int count = 0;

        Query query = new Query(Criteria.where("punishments").elemMatch(
                Criteria.where("type_ordinal").is(4)
                        .and("data.linkedBanId").is(parentPunishmentId)
        ));

        List<Player> players = template.find(query, Player.class, CollectionName.PLAYERS);

        for (Player player : players) {
            for (Punishment punishment : player.getPunishments()) {
                if (punishment.getType_ordinal() == 4 &&
                        punishment.getData() != null &&
                        parentPunishmentId.equals(punishment.getData().get("linkedBanId")) &&
                        statusCalculator.isPunishmentActive(punishment)) {

                    Date now = new Date();

                    PunishmentModification modification = new PunishmentModification(
                            new ObjectId().toHexString(),
                            "MANUAL_DURATION_CHANGE",
                            now,
                            "System",
                            "Cascaded from parent ban duration change",
                            newDuration,
                            null,
                            null
                    );

                    PunishmentNote note = new PunishmentNote(
                            new ObjectId().toHexString(),
                            "Duration changed (cascaded from parent ban)",
                            now,
                            "System"
                    );

                    Query updateQuery = Query.query(
                            Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                                    .and("punishments.id").is(punishment.getId())
                    );

                    Update update = new Update()
                            .push("punishments.$.modifications", modification)
                            .push("punishments.$.notes", note)
                            .set("punishments.$.data.duration", newDuration);

                    template.updateFirst(updateQuery, update, Player.class, CollectionName.PLAYERS);

                    log.info("[CASCADE] Duration changed on LinkedBan {} on {} (parent: {}, newDuration: {})",
                            punishment.getId(), player.getMinecraftUuid(), parentPunishmentId, newDuration);
                    count++;
                }
            }
        }

        return count;
    }

    private int cascadePardonLinkedBansInternal(MongoTemplate template, String parentPunishmentId) {
        int count = 0;

        Query query = new Query(Criteria.where("punishments").elemMatch(
                Criteria.where("type_ordinal").is(4)
                        .and("data.linkedBanId").is(parentPunishmentId)
        ));

        List<Player> players = template.find(query, Player.class, CollectionName.PLAYERS);

        for (Player player : players) {
            for (Punishment punishment : player.getPunishments()) {
                if (punishment.getType_ordinal() == 4 &&
                        punishment.getData() != null &&
                        parentPunishmentId.equals(punishment.getData().get("linkedBanId")) &&
                        statusCalculator.isPunishmentActive(punishment)) {
                    systemPardonWithTemplate(template, player.getMinecraftUuid(), punishment.getId(),
                            "Auto-pardoned: parent ban was pardoned");
                    log.info("[CASCADE] Pardoned LinkedBan {} on {} (parent: {})",
                            punishment.getId(), player.getMinecraftUuid(), parentPunishmentId);
                    count++;
                }
            }
        }

        return count;
    }

    private void systemPardonWithTemplate(MongoTemplate template, UUID playerUuid, String punishmentId, String reason) {
        Date now = new Date();

        Query query = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid.toString())
                        .and("punishments.id").is(punishmentId)
        );

        PunishmentModification modification = new PunishmentModification(
                new ObjectId().toHexString(),
                "SYSTEM_PARDON",
                now,
                "System",
                reason,
                null,
                null,
                null
        );

        PunishmentNote pardonNote = new PunishmentNote(
                new ObjectId().toHexString(),
                reason,
                now,
                "System"
        );

        Update update = new Update()
                .push("punishments.$.modifications", modification)
                .push("punishments.$.notes", pardonNote);

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
    }

    /**
     * Get all LinkedBans referencing a parent punishment.
     * @return list of linked ban info maps with punishmentId, playerUuid, playerName, active
     */
    public List<Map<String, Object>> getLinkedBansForParent(Server server, String parentPunishmentId) {
        MongoTemplate template = getTemplate(server);
        List<Map<String, Object>> results = new ArrayList<>();

        Query query = new Query(Criteria.where("punishments").elemMatch(
                Criteria.where("type_ordinal").is(4)
                        .and("data.linkedBanId").is(parentPunishmentId)
        ));

        List<Player> players = template.find(query, Player.class, CollectionName.PLAYERS);

        for (Player player : players) {
            String username = player.getUsernames().isEmpty() ? "Unknown" :
                    player.getUsernames().get(player.getUsernames().size() - 1).username();

            for (Punishment punishment : player.getPunishments()) {
                if (punishment.getType_ordinal() == 4 &&
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
        Date now = new Date();
        Map<String, Object> data = new HashMap<>();
        data.put("linkedBanId", parentPunishmentId);
        data.put("linkedBanParentUuid", parentPlayerUuid);
        if (duration != null) {
            data.put("duration", duration);
        }

        String punishmentId = IdGenerator.generatePunishmentId();

        List<PunishmentNote> notes = new ArrayList<>();
        notes.add(new PunishmentNote(
                new ObjectId().toHexString(),
                "issued punishment",
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

        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("minecraftUuid").is(playerUuid.toString()));
        Document punishmentDoc = (Document) template.getConverter().convertToMongoType(punishment);
        Update update = new Update().push("punishments", punishmentDoc);
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

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
        MongoTemplate template = getTemplate(server);
        Player player = findPlayerByUuid(template, playerUuid);
        if (player == null) return null;

        Punishment punishment = player.getPunishments().stream()
                .filter(p -> p.getId().equals(punishmentId))
                .findFirst()
                .orElse(null);
        if (punishment == null) return null;

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

        // Update the punishment's attachedTicketIds in MongoDB
        Query query = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid.toString())
                        .and("punishments.id").is(punishmentId)
        );
        Update update = new Update().set("punishments.$.attachedTicketIds", currentIds);
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        // Optionally close/reopen the actual tickets
        if (request.modifyAssociatedTickets()) {
            if (request.addTicketIds() != null && !request.addTicketIds().isEmpty()) {
                closeAttachedTickets(template, request.addTicketIds(), request.issuerName());
            }
            if (request.removeTicketIds() != null && !request.removeTicketIds().isEmpty()) {
                reopenAttachedTickets(template, request.removeTicketIds(), request.issuerName());
            }
        }

        log.info("[TICKET_MODIFY] Modified tickets on punishment {} for player {} by {}",
                punishmentId, playerUuid, request.issuerName());

        return findPlayerByUuid(template, playerUuid);
    }

    private void reopenAttachedTickets(MongoTemplate template, List<String> ticketIds, String issuerName) {
        for (String ticketId : ticketIds) {
            try {
                Query ticketQuery = Query.query(Criteria.where("_id").is(ticketId));
                Ticket ticket = template.findOne(ticketQuery, Ticket.class, CollectionName.TICKETS);
                if (ticket == null || !ticket.isLocked()) continue;

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

                Update ticketUpdate = new Update()
                        .push("replies", systemReply)
                        .set("locked", false)
                        .set("status", "Open")
                        .set("updatedAt", new Date());

                template.updateFirst(ticketQuery, ticketUpdate, Ticket.class, CollectionName.TICKETS);
                log.info("[TICKET_REOPEN] Reopened ticket {} due to punishment ticket removal by {}", ticketId, issuerName);
            } catch (Exception e) {
                log.error("[TICKET_REOPEN] Failed to reopen ticket {}: {}", ticketId, e.getMessage());
            }
        }
    }

    private void closeAttachedTickets(MongoTemplate template, List<String> ticketIds, String issuerName) {
        for (String ticketId : ticketIds) {
            try {
                Query ticketQuery = Query.query(Criteria.where("_id").is(ticketId));
                Ticket ticket = template.findOne(ticketQuery, Ticket.class, CollectionName.TICKETS);
                if (ticket == null || ticket.isLocked()) continue;

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

                Update ticketUpdate = new Update()
                        .push("replies", systemReply)
                        .set("locked", true)
                        .set("status", "Closed")
                        .set("updatedAt", new Date());

                template.updateFirst(ticketQuery, ticketUpdate, Ticket.class, CollectionName.TICKETS);
                log.info("[TICKET_CLOSE] Closed ticket {} due to punishment creation by {}", ticketId, issuerName);
            } catch (Exception e) {
                log.error("[TICKET_CLOSE] Failed to close ticket {}: {}", ticketId, e.getMessage());
            }
        }
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

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }

    private Player findPlayerByUuid(MongoTemplate template, UUID uuid) {
        Query query = Query.query(Criteria.where("minecraftUuid").is(uuid.toString()));
        return template.findOne(query, Player.class, CollectionName.PLAYERS);
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

        int ordinal = punishment.getType_ordinal();

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
                punishment.getModifications(),
                punishment.getNotes(),
                punishment.getEvidence(),
                punishment.getAttachedTicketIds()
        );
    }
}
