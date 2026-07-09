package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.PunishmentFields;
import gg.modl.backend.infrastructure.util.MongoKeyUtils;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.data.punishment.PunishmentStatus;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class PunishmentMongoRepository extends AbstractServerMongoRepository<Player> {
    public PunishmentMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(Player.class, CollectionName.PLAYERS, tenantMongoAccess);
    }

    public Optional<Player> findByPunishmentId(Server server, String punishmentId) {
        return findOne(server, Query.query(Criteria.where(PlayerFields.PUNISHMENT_ID).is(punishmentId)));
    }

    public List<Player> findByLinkedBanId(Server server, String parentPunishmentId) {
        return find(server, linkedBanQuery(parentPunishmentId));
    }

    private Query linkedBanQuery(String parentPunishmentId) {
        return Query.query(Criteria.where(PlayerFields.PUNISHMENTS).elemMatch(
            Criteria.where(PunishmentFields.TYPE_ORDINAL).is(4)
                .and(PunishmentFields.DATA_LINKED_BAN_ID).is(parentPunishmentId)
        ));
    }

    public void appendPunishment(Server server, String playerUuid, Punishment punishment) {
        Query query = Query.query(Criteria.where(PlayerFields.MINECRAFT_UUID).is(playerUuid));
        Update update = new Update().push(PlayerFields.PUNISHMENTS, punishment);
        updateFirst(server, query, update);
    }

    public void appendPardon(Server server, String playerUuid, String punishmentId,
                             PunishmentModification modification, List<PunishmentNote> notes, String status) {
        Query query = punishmentQuery(playerUuid, punishmentId);
        Update update = new Update()
            .push(PlayerFields.PUNISHMENT_MODIFICATIONS, modification)
            .set(PlayerFields.PUNISHMENT_DATA + ".status", status);
        if (!notes.isEmpty()) {
            update.push(PlayerFields.PUNISHMENT_NOTES).each(notes.toArray());
        }
        updateFirst(server, query, update);
    }

    public void appendDurationChange(Server server, String playerUuid, String punishmentId,
                                     PunishmentModification modification, PunishmentNote note, long effectiveDuration) {
        Query query = punishmentQuery(playerUuid, punishmentId);
        Update update = new Update()
            .push(PlayerFields.PUNISHMENT_MODIFICATIONS, modification)
            .push(PlayerFields.PUNISHMENT_NOTES, note)
            .set(PlayerFields.PUNISHMENT_DATA + ".duration", effectiveDuration);
        updateFirst(server, query, update);
    }

    public void appendModification(Server server, String playerUuid, String punishmentId,
                                   PunishmentModification modification) {
        Query query = punishmentQuery(playerUuid, punishmentId);
        Update update = new Update().push(PlayerFields.PUNISHMENT_MODIFICATIONS, modification);
        updateFirst(server, query, update);
    }

    public boolean setPunishmentStartedIfUnset(Server server, String playerUuid, String punishmentId, Date started) {
        Update update = new Update().set(PlayerFields.PUNISHMENT_STARTED, started);
        return updateFirst(server, startedUnsetQuery(playerUuid, punishmentId), update).getModifiedCount() > 0;
    }

    public void appendEvidence(Server server, String playerUuid, String punishmentId,
                               List<PunishmentEvidence> evidence, @Nullable PunishmentNote note) {
        Query query = punishmentQuery(playerUuid, punishmentId);
        Update update = new Update().push(PlayerFields.PUNISHMENT_EVIDENCE).each(evidence.toArray());
        if (note != null) {
            update.push(PlayerFields.PUNISHMENT_NOTES, note);
        }
        updateFirst(server, query, update);
    }

    public void setPunishmentData(Server server, String playerUuid, String punishmentId,
                                  Map<String, Object> dataUpdates, @Nullable PunishmentNote note) {
        Query query = punishmentQuery(playerUuid, punishmentId);
        Update update = new Update();
        for (Map.Entry<String, Object> entry : dataUpdates.entrySet()) {
            MongoKeyUtils.validateUpdatePath(entry.getKey());
            update.set(PlayerFields.PUNISHMENT_DATA + "." + entry.getKey(), MongoKeyUtils.sanitizeValue(entry.getValue()));
        }
        if (note != null) {
            update.push(PlayerFields.PUNISHMENT_NOTES, note);
        }
        updateFirst(server, query, update);
    }

    public void setPunishmentTickets(Server server, String playerUuid, String punishmentId, List<String> ticketIds) {
        Query query = punishmentQuery(playerUuid, punishmentId);
        Update update = new Update().set(PlayerFields.PUNISHMENT_ATTACHED_TICKET_IDS, ticketIds);
        updateFirst(server, query, update);
    }

    public boolean acknowledgePunishmentStart(Server server, String playerUuid, String punishmentId, Date started) {
        Update update = new Update()
            .set(PlayerFields.PUNISHMENT_STARTED, started)
            .unset(PlayerFields.PUNISHMENT_DATA + ".status");
        return updateFirst(server, startedUnsetQuery(playerUuid, punishmentId), update).getModifiedCount() > 0;
    }

    private Query startedUnsetQuery(String playerUuid, String punishmentId) {
        return Query.query(
            Criteria.where(PlayerFields.MINECRAFT_UUID).is(playerUuid)
                .and(PlayerFields.PUNISHMENTS).elemMatch(
                    Criteria.where(PunishmentFields.ID).is(punishmentId)
                        .and(PunishmentFields.STARTED).is(null)
                )
        );
    }

    public void linkAppealToPunishment(Server server, String playerUuid, String punishmentId,
                                       String appealId, PunishmentNote note) {
        Query query = punishmentQuery(playerUuid, punishmentId);
        Update update = new Update()
            .push(PlayerFields.PUNISHMENT_ATTACHED_TICKET_IDS, appealId)
            .push(PlayerFields.PUNISHMENT_NOTES, note);
        updateFirst(server, query, update);
    }

    public void addPunishmentNote(Server server, String playerUuid, String punishmentId,
                                  PunishmentNote note, Map<String, Object> dataUpdates) {
        Query query = punishmentQuery(playerUuid, punishmentId);
        Update update = new Update().push(PlayerFields.PUNISHMENT_NOTES, note);
        if (dataUpdates != null) {
            for (Map.Entry<String, Object> entry : dataUpdates.entrySet()) {
                MongoKeyUtils.validateUpdatePath(entry.getKey());
                update.set("punishments.$." + entry.getKey(), MongoKeyUtils.sanitizeValue(entry.getValue()));
            }
        }
        updateFirst(server, query, update);
    }

    public void applyAppealApproval(Server server, String playerUuid, String punishmentId,
                                    PunishmentModification modification, PunishmentNote note,
                                    String appealOutcome, String appealTicketId) {
        Query query = punishmentQuery(playerUuid, punishmentId);
        Update update = new Update()
            .push(PlayerFields.PUNISHMENT_MODIFICATIONS, modification)
            .push(PlayerFields.PUNISHMENT_NOTES, note)
            .set(PlayerFields.PUNISHMENT_DATA + ".appealOutcome", appealOutcome)
            .set(PlayerFields.PUNISHMENT_DATA + ".appealTicketId", appealTicketId);
        updateFirst(server, query, update);
    }

    private Query punishmentQuery(String playerUuid, String punishmentId) {
        return Query.query(
            Criteria.where(PlayerFields.MINECRAFT_UUID).is(playerUuid)
                .and(PlayerFields.PUNISHMENT_ID).is(punishmentId)
        );
    }

    public List<Document> fetchRecentPunishmentRows(Server server, Date issuedAfter, int limit) {
        Criteria criteria = Criteria.where(PlayerFields.PUNISHMENT_ISSUED).gte(issuedAfter);
        return fetchPunishmentRowsByCriteria(server, criteria, limit);
    }

    public List<Document> fetchRecentPunishmentRowsByIssuer(Server server, String issuerName, Date issuedAfter, int limit) {
        Criteria criteria = Criteria.where(PlayerFields.PUNISHMENT_ISSUER_NAME).is(issuerName)
            .and(PlayerFields.PUNISHMENT_ISSUED).gte(issuedAfter);
        return fetchPunishmentRowsByCriteria(server, criteria, limit);
    }

    private List<Document> fetchPunishmentRowsByCriteria(Server server, Criteria punishmentCriteria, int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(punishmentCriteria),
            Aggregation.unwind(PlayerFields.PUNISHMENTS),
            Aggregation.match(punishmentCriteria),
            Aggregation.sort(Sort.by(Sort.Direction.DESC, PlayerFields.PUNISHMENT_ISSUED)),
            Aggregation.limit(limit),
            Aggregation.project(PlayerFields.MINECRAFT_UUID, PlayerFields.USERNAMES)
                .and(PlayerFields.PUNISHMENTS).as("punishment")
        );
        return aggregate(server, aggregation, Document.class).getMappedResults();
    }

    public Punishment readPunishment(Server server, Document punishmentDocument) {
        return serverTemplate(server).getConverter().read(Punishment.class, punishmentDocument);
    }

    public Map<String, Integer> countPunishmentsByIssuerName(Server server) {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(PlayerFields.PUNISHMENTS).exists(true).ne(List.of())),
            Aggregation.unwind(PlayerFields.PUNISHMENTS),
            Aggregation.group(PlayerFields.PUNISHMENT_ISSUER_NAME).count().as("count")
        );
        AggregationResults<Document> results = aggregate(server, aggregation, Document.class);
        Map<String, Integer> punishmentCounts = new LinkedHashMap<>();
        for (Document document : results.getMappedResults()) {
            String issuerName = document.getString("_id");
            if (issuerName != null) {
                punishmentCounts.put(issuerName, document.getInteger("count", 0));
            }
        }
        return punishmentCounts;
    }

    public long countAllPunishments(Server server) {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(PlayerFields.PUNISHMENTS).exists(true).ne(List.of())),
            Aggregation.unwind(PlayerFields.PUNISHMENTS),
            Aggregation.count().as("count")
        );
        Document first = aggregate(server, aggregation, Document.class).getUniqueMappedResult();
        return first == null ? 0L : first.getInteger("count", 0);
    }

    public Map<String, Integer> countPunishmentsByIssuerId(Server server) {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(PlayerFields.PUNISHMENTS).exists(true).ne(List.of())),
            Aggregation.unwind(PlayerFields.PUNISHMENTS),
            Aggregation.match(Criteria.where(PlayerFields.PUNISHMENT_ISSUER_ID).ne(null)),
            Aggregation.group(PlayerFields.PUNISHMENT_ISSUER_ID).count().as("count")
        );
        AggregationResults<Document> results = aggregate(server, aggregation, Document.class);
        Map<String, Integer> punishmentCounts = new LinkedHashMap<>();
        for (Document document : results.getMappedResults()) {
            String issuerId = document.getString("_id");
            if (issuerId != null) {
                punishmentCounts.put(issuerId, document.getInteger("count", 0));
            }
        }
        return punishmentCounts;
    }

    public Map<String, Integer> countPunishmentsByEffectiveIssuer(Server server) {
        AggregationOperation effectiveIssuer = context -> new Document("$addFields",
            new Document("effectiveIssuer", new Document("$ifNull", List.of(
                "$" + PlayerFields.PUNISHMENT_ISSUER_ID,
                "$" + PlayerFields.PUNISHMENT_ISSUER_NAME))));
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(PlayerFields.PUNISHMENTS).exists(true).ne(List.of())),
            Aggregation.unwind(PlayerFields.PUNISHMENTS),
            effectiveIssuer,
            Aggregation.group("effectiveIssuer").count().as("count")
        );
        AggregationResults<Document> results = aggregate(server, aggregation, Document.class);
        Map<String, Integer> punishmentCounts = new LinkedHashMap<>();
        for (Document document : results.getMappedResults()) {
            String issuer = document.getString("_id");
            if (issuer != null) {
                punishmentCounts.put(issuer, document.getInteger("count", 0));
            }
        }
        return punishmentCounts;
    }

    public List<Player> findWithPunishmentsProjected(Server server) {
        Query query = Query.query(Criteria.where(PlayerFields.PUNISHMENTS).elemMatch(
            Criteria.where(PunishmentFields.TYPE_ORDINAL).ne(0)
                .and(PunishmentFields.DATA_STATUS).ne(PunishmentStatus.UNSTARTED)
        ));
        query.fields().include(PlayerFields.PUNISHMENTS);
        return find(server, query);
    }

    public List<Player> findWithPunishmentsIssuedAfter(Server server, Date cutoff, int limit) {
        Query query = Query.query(Criteria.where(PlayerFields.PUNISHMENTS).elemMatch(
            Criteria.where(PunishmentFields.ISSUED).gte(cutoff)
        ));
        query.fields()
            .include(PlayerFields.MINECRAFT_UUID)
            .include(PlayerFields.USERNAMES)
            .include(PlayerFields.PUNISHMENTS);
        query.with(Sort.by(Sort.Direction.DESC, PlayerFields.PUNISHMENT_ISSUED));
        query.limit(limit);
        return find(server, query);
    }

    public List<Player> findPlayersWithPunishments(Server server, int limit) {
        Query query = Query.query(Criteria.where(PlayerFields.PUNISHMENTS).exists(true));
        query.limit(limit);
        return find(server, query);
    }

    public void unsetPunishmentStatus(Server server, String playerUuid, String punishmentId) {
        Query query = Query.query(
            Criteria.where(PlayerFields.MINECRAFT_UUID).is(playerUuid)
                .and(PlayerFields.PUNISHMENT_ID).is(punishmentId)
        );
        Update update = new Update().unset(PlayerFields.PUNISHMENT_DATA + ".status");
        updateFirst(server, query, update);
    }
}
