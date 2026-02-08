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
import gg.modl.backend.player.dto.response.PunishmentResponse;
import gg.modl.backend.player.dto.response.PunishmentSearchResult;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DefaultPunishmentTypes;
import gg.modl.backend.settings.data.DurationDetail;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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

        Punishment punishment = new Punishment(
                punishmentId,
                request.typeOrdinal(),
                request.issuerName(),
                now,
                null,
                new ArrayList<>(),
                notes,
                evidence,
                request.attachedTicketIds() != null ? request.attachedTicketIds() : new ArrayList<>(),
                data
        );

        Update update = new Update().push("punishments", punishment);
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

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

                Query query = Query.query(
                        Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                                .and("punishments.id").is(toPromote.getId())
                );
                Update update = new Update()
                        .unset("punishments.$.data.status")
                        .set("punishments.$.started", now);
                var result = template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

                log.info("[PROMOTE] MongoDB update result for {}: matched={}, modified={}",
                        toPromote.getId(), result.getMatchedCount(), result.getModifiedCount());

                promotedIds.add(toPromote.getId());
            } else {
                log.info("[PROMOTE] No unstarted punishments found for category {}", category);
            }
        }

        return promotedIds;
    }

    private boolean isUnstarted(Punishment punishment) {
        Map<String, Object> data = punishment.getData();
        if (data == null) return false;

        String status = (String) data.get("status");
        if (!"Unstarted".equals(status)) return false;

        // Not unstarted if it was pardoned or appeal-accepted
        for (var mod : punishment.getModifications()) {
            if ("MANUAL_PARDON".equals(mod.type()) || "APPEAL_ACCEPT".equals(mod.type())) {
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
