package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.AuditLogFields;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.PunishmentFields;
import gg.modl.backend.database.mongo.fields.TicketFields;
import gg.modl.backend.server.data.Server;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.StringOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StaffActivityAnalyticsRepository {
    private final TenantMongoAccess tenantMongoAccess;
    private static final String PUNISHMENTS_PREFIX = PlayerFields.PUNISHMENTS + ".";
    private static final String TOTAL_ACTIONS = "totalActions";
    private static final String COUNT = "count";
    private static final String LEVEL_MODERATION = "moderation";
    private static final String SOURCE_SYSTEM = "system";
    private static final String DATE = "date";
    private static final String ALIAS_TICKET_ACTIONS = "ticketActions";
    private static final String ALIAS_MODERATION_ACTIONS = "moderationActions";
    private static final String ALIAS_LAST_ACTIVE = "lastActive";
    private static final String ALIAS_EFFECTIVE_ISSUER = "effectiveIssuer";
    private static final String ALIAS_PUNISHMENT_ID = "punishmentId";
    private static final String ALIAS_PLAYER_ID = "playerId";
    private static final String ALIAS_TYPE_ORDINAL = "typeOrdinal";
    private static final String ALIAS_ISSUED = "issued";
    private static final String ALIAS_STARTED = "started";
    private static final String ALIAS_MODIFICATIONS = "modifications";
    private static final String ALIAS_USERNAMES = "usernames";
    private static final String ALIAS_REASON = "reason";
    private static final String ALIAS_DURATION = "duration";
    private static final String ALIAS_SUBJECT = "subject";
    private static final String ALIAS_CATEGORY = "category";
    private static final String ALIAS_STATUS = "status";
    private static final String ALIAS_TICKET_CREATED = "ticketCreated";
    private static final String ALIAS_LAST_ACTIVITY = "lastActivity";
    private static final String ALIAS_FIRST_REPLY = "firstReply";
    private static final String ALIAS_STAFF = "staff";
    private static final String ALIAS_STAFF_KEY = "staffKey";
    private static final String ALIAS_TICKET_ID = "ticketId";
    private static final String ALIAS_REPLY_CREATED = "replyCreated";

    public List<StaffActivityResult> aggregateLogActivityBySource(Server server, Date startDate) {
        Criteria logCriteria = Criteria.where(AuditLogFields.SOURCE).ne(SOURCE_SYSTEM);
        if (startDate != null) {
            logCriteria = logCriteria.and(AuditLogFields.CREATED).gte(startDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(logCriteria),
            Aggregation.group(AuditLogFields.SOURCE)
                .count().as(TOTAL_ACTIONS)
                .sum(ConditionalOperators.when(Criteria.where(AuditLogFields.DESCRIPTION).regex(Pattern.compile("ticket", Pattern.CASE_INSENSITIVE)))
                    .then(1)
                    .otherwise(0)).as(ALIAS_TICKET_ACTIONS)
                .sum(ConditionalOperators.when(new Criteria().orOperator(
                    Criteria.where(AuditLogFields.LEVEL).is(LEVEL_MODERATION),
                    Criteria.where(AuditLogFields.DESCRIPTION).regex(Pattern.compile("ban|mute|kick|punishment", Pattern.CASE_INSENSITIVE))
                )).then(1).otherwise(0)).as(ALIAS_MODERATION_ACTIONS)
                .max(AuditLogFields.CREATED).as(ALIAS_LAST_ACTIVE),
            Aggregation.sort(Sort.Direction.DESC, TOTAL_ACTIONS)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.LOGS, StaffActivityResult.class)
            .getMappedResults();
    }

    public List<IdCountResult> aggregateTicketResponseCounts(Server server, Date startDate) {
        Criteria replyCriteria = Criteria.where(TicketFields.REPLY_STAFF).is(true);
        if (startDate != null) {
            replyCriteria = replyCriteria.and(TicketFields.REPLY_CREATED).gte(startDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.unwind(TicketFields.REPLIES),
            Aggregation.match(replyCriteria),
            Aggregation.group(TicketFields.REPLY_NAME).count().as(COUNT)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.TICKETS, IdCountResult.class)
            .getMappedResults();
    }

    public List<IdCountResult> aggregatePunishmentCountsByIssuer(Server server, Date startDate) {
        List<AggregationOperation> stages = new ArrayList<>();
        stages.add(Aggregation.unwind(PlayerFields.PUNISHMENTS));
        if (startDate != null) {
            stages.add(Aggregation.match(Criteria.where(PlayerFields.PUNISHMENT_ISSUED).gte(startDate)));
        }
        stages.add(context -> new Document("$addFields", new Document(ALIAS_EFFECTIVE_ISSUER,
            new Document("$ifNull", List.of("$" + PlayerFields.PUNISHMENT_ISSUER_ID, "$" + PlayerFields.PUNISHMENT_ISSUER_NAME)))));
        stages.add(Aggregation.group(ALIAS_EFFECTIVE_ISSUER).count().as(COUNT));

        return tenantMongoAccess.forServer(server)
            .aggregate(Aggregation.newAggregation(stages), CollectionName.PLAYERS, IdCountResult.class)
            .getMappedResults();
    }

    public List<Document> aggregatePunishmentDetails(Server server, List<String> usernames, String staffId, Date startDate) {
        Criteria criteria = buildIssuerCriteria(usernames, staffId);
        if (startDate != null) {
            criteria = criteria.and(PlayerFields.PUNISHMENT_ISSUED).gte(startDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.unwind(PlayerFields.PUNISHMENTS),
            Aggregation.match(criteria),
            Aggregation.sort(Sort.Direction.DESC, PlayerFields.PUNISHMENT_ISSUED),
            Aggregation.limit(50),
            Aggregation.project()
                .and(PlayerFields.PUNISHMENT_ID).as(ALIAS_PUNISHMENT_ID)
                .and(PlayerFields.MINECRAFT_UUID).as(ALIAS_PLAYER_ID)
                .and(PlayerFields.PUNISHMENT_TYPE_ORDINAL).as(ALIAS_TYPE_ORDINAL)
                .and(PlayerFields.PUNISHMENT_ISSUED).as(ALIAS_ISSUED)
                .and(PUNISHMENTS_PREFIX + PunishmentFields.STARTED).as(ALIAS_STARTED)
                .and(PlayerFields.PUNISHMENT_DATA_REASON).as(ALIAS_REASON)
                .and(PlayerFields.PUNISHMENT_DATA_DURATION).as(ALIAS_DURATION)
                .and(PUNISHMENTS_PREFIX + PunishmentFields.MODIFICATIONS).as(ALIAS_MODIFICATIONS)
                .and(PlayerFields.USERNAMES).as(ALIAS_USERNAMES)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.PLAYERS, Document.class)
            .getMappedResults();
    }

    private Criteria buildIssuerCriteria(List<String> usernames, String staffId) {
        List<Criteria> parts = new ArrayList<>();
        for (String username : usernames) {
            parts.add(Criteria.where(PlayerFields.PUNISHMENT_ISSUER_NAME).regex("^" + Pattern.quote(username) + "$", "i"));
        }
        if (staffId != null) {
            parts.add(Criteria.where(PlayerFields.PUNISHMENT_ISSUER_ID).is(staffId));
        }
        return new Criteria().orOperator(parts.toArray(new Criteria[0]));
    }

    public List<Document> aggregateTicketDetails(Server server, String username, Date startDate) {
        Criteria criteria = Criteria.where(TicketFields.REPLY_STAFF).is(true)
            .and(TicketFields.REPLY_NAME).regex("^" + Pattern.quote(username) + "$", "i");
        if (startDate != null) {
            criteria = criteria.and(TicketFields.REPLY_CREATED).gte(startDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.unwind(TicketFields.REPLIES),
            Aggregation.match(criteria),
            Aggregation.sort(Sort.Direction.DESC, TicketFields.REPLY_CREATED),
            Aggregation.group(TicketFields.ID)
                .first(TicketFields.SUBJECT).as(ALIAS_SUBJECT)
                .first(TicketFields.TYPE).as(ALIAS_CATEGORY)
                .first(TicketFields.STATUS).as(ALIAS_STATUS)
                .first(TicketFields.CREATED).as(ALIAS_TICKET_CREATED)
                .max(TicketFields.REPLY_CREATED).as(ALIAS_LAST_ACTIVITY)
                .min(TicketFields.REPLY_CREATED).as(ALIAS_FIRST_REPLY),
            Aggregation.limit(50)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.TICKETS, Document.class)
            .getMappedResults();
    }

    public List<StaffTicketResponseTime> aggregateTicketResponseTimesByStaff(Server server, Date startDate) {
        Criteria replyCriteria = Criteria.where(TicketFields.REPLY_STAFF).is(true);
        if (startDate != null) {
            replyCriteria = replyCriteria.and(TicketFields.REPLY_CREATED).gte(startDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.unwind(TicketFields.REPLIES),
            Aggregation.match(replyCriteria),
            Aggregation.project()
                .and(StringOperators.ToLower.lowerValueOf(TicketFields.REPLY_NAME)).as(ALIAS_STAFF_KEY)
                .and(TicketFields.ID).as(ALIAS_TICKET_ID)
                .and(TicketFields.CREATED).as(ALIAS_TICKET_CREATED)
                .and(TicketFields.REPLY_CREATED).as(ALIAS_REPLY_CREATED),
            Aggregation.group(ALIAS_STAFF_KEY, ALIAS_TICKET_ID)
                .first(ALIAS_TICKET_CREATED).as(ALIAS_TICKET_CREATED)
                .min(ALIAS_REPLY_CREATED).as(ALIAS_FIRST_REPLY),
            Aggregation.project()
                .and("_id." + ALIAS_STAFF_KEY).as(ALIAS_STAFF)
                .and(ALIAS_TICKET_CREATED).as(ALIAS_TICKET_CREATED)
                .and(ALIAS_FIRST_REPLY).as(ALIAS_FIRST_REPLY)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.TICKETS, StaffTicketResponseTime.class)
            .getMappedResults();
    }

    public List<IdCountResult> aggregateDailyPunishmentCounts(Server server, List<String> usernames, String staffId, Date startDate) {
        Criteria criteria = buildIssuerCriteria(usernames, staffId);
        if (startDate != null) {
            criteria = criteria.and(PlayerFields.PUNISHMENT_ISSUED).gte(startDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.unwind(PlayerFields.PUNISHMENTS),
            Aggregation.match(criteria),
            Aggregation.project().andExpression("dateToString('%Y-%m-%d', " + PlayerFields.PUNISHMENT_ISSUED + ")").as(DATE),
            Aggregation.group(DATE).count().as(COUNT)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.PLAYERS, IdCountResult.class)
            .getMappedResults();
    }

    public List<IdCountResult> aggregateDailyTicketResponseCounts(Server server, String username, Date startDate) {
        Criteria criteria = Criteria.where(TicketFields.REPLY_STAFF).is(true)
            .and(TicketFields.REPLY_NAME).regex("^" + Pattern.quote(username) + "$", "i");
        if (startDate != null) {
            criteria = criteria.and(TicketFields.REPLY_CREATED).gte(startDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.unwind(TicketFields.REPLIES),
            Aggregation.match(criteria),
            Aggregation.project().andExpression("dateToString('%Y-%m-%d', " + TicketFields.REPLY_CREATED + ")").as(DATE),
            Aggregation.group(DATE).count().as(COUNT)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.TICKETS, IdCountResult.class)
            .getMappedResults();
    }

    public List<OrdinalCountResult> aggregatePunishmentTypeBreakdown(Server server, List<String> usernames, String staffId, Date startDate) {
        Criteria criteria = buildIssuerCriteria(usernames, staffId);
        if (startDate != null) {
            criteria = criteria.and(PlayerFields.PUNISHMENT_ISSUED).gte(startDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.unwind(PlayerFields.PUNISHMENTS),
            Aggregation.match(criteria),
            Aggregation.group(PlayerFields.PUNISHMENT_TYPE_ORDINAL).count().as(COUNT),
            Aggregation.sort(Sort.Direction.DESC, COUNT)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.PLAYERS, OrdinalCountResult.class)
            .getMappedResults();
    }

    public record IdCountResult(String id, int count) {}

    public record OrdinalCountResult(Integer id, int count) {}

    public record StaffActivityResult(String id, int totalActions, int ticketActions, int moderationActions, Date lastActive) {}

    public record StaffTicketResponseTime(String staff, Date ticketCreated, Date firstReply) {}
}
