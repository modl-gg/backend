package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.AuditLogFields;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.TicketFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.TicketStatus;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnalyticsMongoRepository {
    private static final String FACET_TOTAL = "total";
    private static final String FACET_ACTIVE = "active";
    private static final String FACET_RECENT = "recent";
    private static final String FACET_PREVIOUS = "previous";
    private static final String FACET_BY_TYPE = "byType";
    private static final String FACET_BY_STAFF = "byStaff";
    private static final String FACET_DAILY = "daily";
    private static final String FACET_NEW_PLAYERS = "newPlayers";
    private static final String FACET_BY_COUNTRY = "byCountry";
    private static final String FACET_SUSPICIOUS = "suspicious";
    private static final String ALIAS_N = "n";
    private static final String ALIAS_COUNT = "count";
    private static final String ALIAS_DATE = "date";
    private static final String EARLIEST_FIRST_LOGIN = "earliestFirstLogin";

    private final TenantMongoAccess tenantMongoAccess;

    @NotNull
    public OverviewStats loadOverviewStats(@NotNull Server server, @NotNull Date thirtyDaysAgo, @NotNull Date sixtyDaysAgo) {
        final MongoTemplate template = tenantMongoAccess.forServer(server);
        final List<Document> pipeline = List.of(
            new Document("$facet", new Document()
                .append(FACET_TOTAL, List.of(new Document("$count", ALIAS_N)))
                .append(FACET_ACTIVE, List.of(
                    new Document("$match", new Document(TicketFields.STATUS, TicketStatus.OPEN.getId())),
                    new Document("$count", ALIAS_N)
                ))
                .append(FACET_RECENT, List.of(
                    new Document("$match", new Document(TicketFields.CREATED, new Document("$gte", thirtyDaysAgo))),
                    new Document("$count", ALIAS_N)
                ))
                .append(FACET_PREVIOUS, List.of(
                    new Document("$match", new Document(TicketFields.CREATED,
                        new Document("$gte", sixtyDaysAgo).append("$lt", thirtyDaysAgo))),
                    new Document("$count", ALIAS_N)
                ))
            )
        );
        final List<Document> ticketResult = template.getCollection(CollectionName.TICKETS).aggregate(pipeline).into(new ArrayList<>());

        long totalTickets = 0, activeTickets = 0, recentTickets = 0, previousTickets = 0;
        if (!ticketResult.isEmpty()) {
            Document facets = ticketResult.get(0);
            totalTickets = extractFacetCount(facets, FACET_TOTAL);
            activeTickets = extractFacetCount(facets, FACET_ACTIVE);
            recentTickets = extractFacetCount(facets, FACET_RECENT);
            previousTickets = extractFacetCount(facets, FACET_PREVIOUS);
        }

        final long totalPlayers = template.getCollection(CollectionName.PLAYERS).countDocuments();
        final long totalStaff = template.getCollection(CollectionName.STAFF).countDocuments();

        return new OverviewStats(totalTickets, totalPlayers, totalStaff, activeTickets, recentTickets, previousTickets);
    }

    private long extractFacetCount(Document facets, String key) {
        final List<?> list = facets.getList(key, Document.class, List.of());
        if (list.isEmpty()) {
            return 0;
        }

        final Object first = list.getFirst();
        if (first instanceof final Document doc) {
            final Number n = doc.get(ALIAS_N, Number.class);
            return n != null ? n.longValue() : 0;
        }

        return 0;
    }

    public List<IdCountResult> aggregateTicketStatusCounts(Server server, Date startDate) {
        Criteria criteria = MongoQueries.where(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId());
        if (startDate != null) {
            criteria = criteria.and(TicketFields.CREATED).gte(startDate);
        }

        final Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group(TicketFields.STATUS).count().as(ALIAS_COUNT)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.TICKETS, IdCountResult.class)
            .getMappedResults();
    }

    public List<IdCountResult> aggregateTicketCategoryCounts(Server server, Date startDate) {
        Criteria criteria = MongoQueries.where(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId());
        if (startDate != null) {
            criteria = criteria.and(TicketFields.CREATED).gte(startDate);
        }

        final Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group(TicketFields.TYPE).count().as(ALIAS_COUNT)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.TICKETS, IdCountResult.class)
            .getMappedResults();
    }

    public List<IdCountResult> aggregateDailyTicketCounts(Server server, Date startDate) {
        Criteria criteria = MongoQueries.where(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId());
        if (startDate != null) {
            criteria = criteria.and(TicketFields.CREATED).gte(startDate);
        }

        final Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.project().andExpression("dateToString('%Y-%m-%d', " + TicketFields.CREATED + ")").as(ALIAS_DATE),
            Aggregation.group(ALIAS_DATE).count().as(ALIAS_COUNT),
            Aggregation.sort(Sort.Direction.ASC, "_id")
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.TICKETS, IdCountResult.class)
            .getMappedResults();
    }

    public Document aggregatePunishmentAnalytics(Server server, Date startDate, String analyticsTimeZone) {
        final List<Document> pipeline = new ArrayList<>();
        if (startDate != null) {
            pipeline.add(new Document("$match", new Document(PlayerFields.PUNISHMENT_ISSUED, new Document("$gte", startDate))));
        }

        pipeline.add(new Document("$unwind", "$" + PlayerFields.PUNISHMENTS));
        pipeline.add(new Document("$match", new Document(PlayerFields.PUNISHMENT_ISSUED, new Document("$type", "date"))));

        if (startDate != null) {
            pipeline.add(new Document("$match", new Document(PlayerFields.PUNISHMENT_ISSUED, new Document("$gte", startDate))));
        }

        final Document byTypeFacet = new Document("$group",
            new Document("_id", "$" + PlayerFields.PUNISHMENT_TYPE_ORDINAL).append(ALIAS_COUNT, new Document("$sum", 1)));
        final Document sortByCountDesc = new Document("$sort", new Document(ALIAS_COUNT, -1));
        final Document byStaffFacet = new Document("$group",
            new Document("_id", "$" + PlayerFields.PUNISHMENT_ISSUER_NAME).append(ALIAS_COUNT, new Document("$sum", 1)));
        final Document byDayFacet = new Document("$group", new Document("_id",
            new Document("$dateToString",
                new Document("format", "%Y-%m-%d")
                    .append("date", "$" + PlayerFields.PUNISHMENT_ISSUED)
                    .append("timezone", analyticsTimeZone)))
            .append(ALIAS_COUNT, new Document("$sum", 1)));
        final Document sortByDayAsc = new Document("$sort", new Document("_id", 1));

        pipeline.add(new Document("$facet", new Document(FACET_BY_TYPE, List.of(byTypeFacet, sortByCountDesc))
            .append(FACET_BY_STAFF, List.of(byStaffFacet, sortByCountDesc))
            .append(FACET_DAILY, List.of(byDayFacet, sortByDayAsc))));

        final List<Document> aggregateResults = tenantMongoAccess.forServer(server)
            .getCollection(CollectionName.PLAYERS)
            .aggregate(pipeline)
            .into(new ArrayList<>());

        return aggregateResults.isEmpty() ? null : aggregateResults.get(0);
    }

    public List<IdCountResult> aggregateAuditLogLevelCounts(Server server, Date startDate) {
        Criteria criteria = new Criteria();
        if (startDate != null) {
            criteria = MongoQueries.where(AuditLogFields.CREATED).gte(startDate);
        }

        final Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group(AuditLogFields.LEVEL).count().as(ALIAS_COUNT)
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.LOGS, IdCountResult.class)
            .getMappedResults();
    }

    public List<Document> aggregateHourlyAuditLogCounts(Server server, Date since, String timeZone) {
        final List<Document> pipeline = List.of(
            new Document("$match", new Document(AuditLogFields.CREATED, new Document("$gte", since))),
            new Document("$group", new Document("_id",
                new Document("$dateToString",
                    new Document("format", "%Y-%m-%dT%H")
                        .append("date", "$" + AuditLogFields.CREATED)
                        .append("timezone", timeZone)))
                .append(ALIAS_COUNT, new Document("$sum", 1))),
            new Document("$sort", new Document("_id", 1))
        );

        return tenantMongoAccess.forServer(server)
            .getCollection(CollectionName.LOGS)
            .aggregate(pipeline)
            .into(new ArrayList<>());
    }

    public Document aggregatePlayerActivity(Server server, Date startDate, String timeZone) {
        final List<Document> pipeline = new ArrayList<>();

        pipeline.add(new Document("$match", new Document(PlayerFields.IP_ADDRESSES, new Document("$exists", true).append("$ne", List.of()))));
        pipeline.add(new Document("$project", new Document(PlayerFields.IP_ADDRESSES, 1)));

        final List<Document> newPlayerFacet = new ArrayList<>();
        newPlayerFacet.add(new Document("$addFields", new Document(EARLIEST_FIRST_LOGIN, new Document("$min", "$" + PlayerFields.IP_FIRST_LOGIN))));

        if (startDate != null) {
            newPlayerFacet.add(new Document("$match", new Document(EARLIEST_FIRST_LOGIN,
                new Document("$gt", startDate))));
        }

        newPlayerFacet.add(new Document("$group", new Document("_id",
            new Document("$dateToString",
                new Document("format", "%Y-%m-%d")
                    .append("date", "$" + EARLIEST_FIRST_LOGIN)
                    .append("timezone", timeZone)))
            .append(ALIAS_COUNT, new Document("$sum", 1))));
        newPlayerFacet.add(new Document("$sort", new Document("_id", 1)));

        final List<Document> countryFacet = new ArrayList<>();
        countryFacet.add(new Document("$unwind", "$" + PlayerFields.IP_ADDRESSES));

        if (startDate != null) {
            countryFacet.add(new Document("$match", new Document("$or", List.of(
                new Document(PlayerFields.IP_FIRST_LOGIN, new Document("$gt", startDate)),
                new Document(PlayerFields.IP_ADDRESSES + ".logins", new Document("$elemMatch", new Document("$gt", startDate)))
            ))));
        }
        countryFacet.add(new Document("$match", new Document(PlayerFields.IP_ADDRESSES + ".country",
            new Document("$exists", true).append("$ne", ""))));

        Document loginCountExpr;
        if (startDate != null) {
            loginCountExpr = new Document("$max", List.of(
                1,
                new Document("$size", new Document("$filter",
                    new Document("input", new Document("$ifNull", List.of("$" + PlayerFields.IP_ADDRESSES + ".logins", List.of())))
                        .append("as", "d")
                        .append("cond", new Document("$gt", List.of("$$d", startDate)))))));
        } else {
            loginCountExpr = new Document("$max", List.of(
                1,
                new Document("$size", new Document("$ifNull", List.of("$" + PlayerFields.IP_ADDRESSES + ".logins", List.of())))));
        }

        countryFacet.add(new Document("$group", new Document("_id", "$" + PlayerFields.IP_ADDRESSES + ".country")
            .append(ALIAS_COUNT, new Document("$sum", loginCountExpr))));
        countryFacet.add(new Document("$sort", new Document(ALIAS_COUNT, -1)));
        countryFacet.add(new Document("$limit", 20));

        final List<Document> suspiciousFacet = new ArrayList<>();
        suspiciousFacet.add(new Document("$unwind", "$" + PlayerFields.IP_ADDRESSES));

        if (startDate != null) {
            suspiciousFacet.add(new Document("$match", new Document("$or", List.of(
                new Document(PlayerFields.IP_FIRST_LOGIN, new Document("$gt", startDate)),
                new Document(PlayerFields.IP_ADDRESSES + ".logins", new Document("$elemMatch", new Document("$gt", startDate)))
            ))));
        }
        suspiciousFacet.add(new Document("$group", new Document("_id", null)
            .append("proxyCount", new Document("$sum", new Document("$cond", List.of("$" + PlayerFields.IP_ADDRESSES + ".proxy", 1, 0))))
            .append("hostingCount", new Document("$sum", new Document("$cond", List.of("$" + PlayerFields.IP_ADDRESSES + ".hosting", 1, 0))))));

        pipeline.add(new Document("$facet", new Document()
            .append(FACET_NEW_PLAYERS, newPlayerFacet)
            .append(FACET_BY_COUNTRY, countryFacet)
            .append(FACET_SUSPICIOUS, suspiciousFacet)));

        final List<Document> results = tenantMongoAccess.forServer(server)
            .getCollection(CollectionName.PLAYERS)
            .aggregate(pipeline)
            .into(new ArrayList<>());

        return results.isEmpty() ? null : results.get(0);
    }

    public record OverviewStats(
        long totalTickets,
        long totalPlayers,
        long totalStaff,
        long activeTickets,
        long recentTickets,
        long previousTickets
    ) {
    }

    public record IdCountResult(String id, int count) {}
}
