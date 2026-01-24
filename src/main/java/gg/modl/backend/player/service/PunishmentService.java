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
import gg.modl.backend.settings.service.PunishmentTypeService;
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

    public Player createPunishment(Server server, UUID playerUuid, CreatePunishmentRequest request) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("minecraftUuid").is(playerUuid.toString()));

        Date now = new Date();
        Map<String, Object> data = request.data() != null ? new HashMap<>(request.data()) : new HashMap<>();

        if (request.severity() != null) {
            data.put("severity", request.severity());
        }
        if (request.status() != null) {
            data.put("status", request.status());
        }
        data.putIfAbsent("active", true);

        List<PunishmentNote> notes = new ArrayList<>();
        if (request.notes() != null) {
            for (CreateNoteRequest noteRequest : request.notes()) {
                String issuer = noteRequest.issuerName() != null ? noteRequest.issuerName() : request.issuerName();
                notes.add(new PunishmentNote(new ObjectId().toHexString(), noteRequest.text(), now, issuer));
            }
        }

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

        Punishment punishment = new Punishment(
                new ObjectId().toHexString(),
                request.typeOrdinal(),
                request.issuerName(),
                now,
                new ArrayList<>(),
                notes,
                evidence,
                request.attachedTicketIds() != null ? request.attachedTicketIds() : new ArrayList<>(),
                data
        );

        Update update = new Update().push("punishments", punishment);
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        return findPlayerByUuid(template, playerUuid);
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

        if ("MANUAL_PARDON".equals(request.type()) || "APPEAL_ACCEPT".equals(request.type())) {
            update.set("punishments.$.data.active", false);
        }

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

                Map<String, Object> data = punishment.getData();
                if (data != null) {
                    String reason = (String) data.get("reason");
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
