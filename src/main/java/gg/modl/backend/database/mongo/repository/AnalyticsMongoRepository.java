package gg.modl.backend.database.mongo.repository;

import static gg.modl.backend.database.mongo.MongoAggregationResults.extractFacetCount;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.AuditLogFields;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.TicketFields;
import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
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
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
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
    private static final String ALIAS_AVG_MILLIS = "avgMillis";
    private static final String EARLIEST_FIRST_LOGIN = "earliestFirstLogin";

    private final TenantMongoAccess tenantMongoAccess;

    private static Document matchDateTyped(String field) {
        return new Document("$match", new Document(field, new Document("$type", "date")));
    }

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

        final List<Document> playerPipeline = List.of(
            new Document("$addFields", new Document(EARLIEST_FIRST_LOGIN, new Document("$min", "$" + PlayerFields.IP_FIRST_LOGIN))),
            matchDateTyped(EARLIEST_FIRST_LOGIN),
            new Document("$facet", new Document()
                .append(FACET_RECENT, List.of(
                    new Document("$match", new Document(EARLIEST_FIRST_LOGIN, new Document("$gte", thirtyDaysAgo))),
                    new Document("$count", ALIAS_N)))
                .append(FACET_PREVIOUS, List.of(
                    new Document("$match", new Document(EARLIEST_FIRST_LOGIN,
                        new Document("$gte", sixtyDaysAgo).append("$lt", thirtyDaysAgo))),
                    new Document("$count", ALIAS_N))))
        );
        final List<Document> playerResult = template.getCollection(CollectionName.PLAYERS).aggregate(playerPipeline).into(new ArrayList<>());

        long recentPlayers = 0, previousPlayers = 0;
        if (!playerResult.isEmpty()) {
            Document facets = playerResult.get(0);
            recentPlayers = extractFacetCount(facets, FACET_RECENT);
            previousPlayers = extractFacetCount(facets, FACET_PREVIOUS);
        }

        return new OverviewStats(totalTickets, totalPlayers, activeTickets, recentTickets, previousTickets,
            recentPlayers, previousPlayers);
    }

    public List<IdCountResult> aggregateTicketStatusCounts(Server server, Date startDate) {
        Criteria criteria = Criteria.where(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId());
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
        Criteria criteria = Criteria.where(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId());
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

    public List<IdCountResult> aggregateDailyTicketCounts(Server server, Date startDate, String timeZone) {
        Criteria criteria = Criteria.where(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId());
        if (startDate != null) {
            criteria = criteria.and(TicketFields.CREATED).gte(startDate);
        }

        final AggregationOperation createdDateTypeMatch = context -> matchDateTyped(TicketFields.CREATED);

        final AggregationOperation dayProjection = context -> new Document("$project",
            new Document(ALIAS_DATE, new Document("$dateToString",
                new Document("format", "%Y-%m-%d")
                    .append("date", "$" + TicketFields.CREATED)
                    .append("timezone", timeZone))));

        final Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            createdDateTypeMatch,
            dayProjection,
            Aggregation.group(ALIAS_DATE).count().as(ALIAS_COUNT),
            Aggregation.sort(Sort.Direction.ASC, "_id")
        );

        return tenantMongoAccess.forServer(server)
            .aggregate(aggregation, CollectionName.TICKETS, IdCountResult.class)
            .getMappedResults();
    }

    public List<CategoryAvgResolution> aggregateAvgResolutionByCategory(Server server, Date startDate) {
        final List<Document> pipeline = new ArrayList<>();
        final Document match = new Document(TicketFields.STATUS, TicketStatus.CLOSED.getId());
        if (startDate != null) {
            match.append(TicketFields.UPDATED_AT, new Document("$gte", startDate));
        }
        pipeline.add(new Document("$match", match));
        pipeline.add(matchDateTyped(TicketFields.CREATED));
        pipeline.add(matchDateTyped(TicketFields.UPDATED_AT));
        pipeline.add(new Document("$group", new Document("_id", "$" + TicketFields.TYPE)
            .append(ALIAS_AVG_MILLIS, new Document("$avg",
                new Document("$subtract", List.of("$" + TicketFields.UPDATED_AT, "$" + TicketFields.CREATED))))));

        final List<Document> results = tenantMongoAccess.forServer(server)
            .getCollection(CollectionName.TICKETS)
            .aggregate(pipeline)
            .into(new ArrayList<>());

        return results.stream()
            .map(doc -> new CategoryAvgResolution(doc.getString("_id"), doubleValueOrZero(doc.get(ALIAS_AVG_MILLIS))))
            .toList();
    }

    public PunishmentAnalyticsFacet aggregatePunishmentAnalytics(Server server, Date startDate, String analyticsTimeZone) {
        final List<Document> pipeline = new ArrayList<>();
        if (startDate != null) {
            pipeline.add(new Document("$match", new Document(PlayerFields.PUNISHMENT_ISSUED, new Document("$gte", startDate))));
        }

        pipeline.add(new Document("$unwind", "$" + PlayerFields.PUNISHMENTS));
        pipeline.add(matchDateTyped(PlayerFields.PUNISHMENT_ISSUED));

        if (startDate != null) {
            pipeline.add(new Document("$match", new Document(PlayerFields.PUNISHMENT_ISSUED, new Document("$gte", startDate))));
        }

        final Document byTypeFacet = new Document("$group",
            new Document("_id", "$" + PlayerFields.PUNISHMENT_TYPE_ORDINAL).append(ALIAS_COUNT, new Document("$sum", 1)));
        final Document sortByCountDesc = new Document("$sort", new Document(ALIAS_COUNT, -1));
        final Document byStaffFacet = new Document("$group",
            new Document("_id", new Document("$ifNull", List.of(
                "$" + PlayerFields.PUNISHMENT_ISSUER_ID,
                "$" + PlayerFields.PUNISHMENT_ISSUER_NAME)))
                .append(ALIAS_COUNT, new Document("$sum", 1)));
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

        if (aggregateResults.isEmpty()) {
            return null;
        }
        Document facets = aggregateResults.get(0);
        return new PunishmentAnalyticsFacet(
            toTypeOrdinalCounts(facets.get(FACET_BY_TYPE)),
            toIssuerCounts(facets.get(FACET_BY_STAFF)),
            toDateCounts(facets.get(FACET_DAILY))
        );
    }

    public List<IdCountResult> aggregateAuditLogLevelCounts(Server server, Date startDate) {
        Criteria criteria = new Criteria();
        if (startDate != null) {
            criteria = Criteria.where(AuditLogFields.CREATED).gte(startDate);
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

    public PlayerActivityFacet aggregatePlayerActivity(Server server, Date startDate, String timeZone) {
        final List<Document> pipeline = new ArrayList<>();

        pipeline.add(new Document("$match", new Document(PlayerFields.IP_ADDRESSES, new Document("$exists", true).append("$ne", List.of()))));
        pipeline.add(new Document("$project", new Document(PlayerFields.IP_ADDRESSES, 1)));

        pipeline.add(new Document("$facet", new Document()
            .append(FACET_NEW_PLAYERS, newPlayerFacet(startDate, timeZone))
            .append(FACET_BY_COUNTRY, countryFacet(startDate))
            .append(FACET_SUSPICIOUS, suspiciousFacet(startDate))));

        final List<Document> results = tenantMongoAccess.forServer(server)
            .getCollection(CollectionName.PLAYERS)
            .aggregate(pipeline)
            .into(new ArrayList<>());

        if (results.isEmpty()) {
            return null;
        }
        Document facets = results.get(0);
        return new PlayerActivityFacet(
            toDateCounts(facets.get(FACET_NEW_PLAYERS)),
            toCountryCounts(facets.get(FACET_BY_COUNTRY)),
            toSuspiciousCounts(facets.get(FACET_SUSPICIOUS))
        );
    }

    private static List<Document> newPlayerFacet(Date startDate, String timeZone) {
        final List<Document> facet = new ArrayList<>();
        facet.add(new Document("$addFields", new Document(EARLIEST_FIRST_LOGIN, new Document("$min", "$" + PlayerFields.IP_FIRST_LOGIN))));
        facet.add(matchDateTyped(EARLIEST_FIRST_LOGIN));

        if (startDate != null) {
            facet.add(new Document("$match", new Document(EARLIEST_FIRST_LOGIN,
                new Document("$gt", startDate))));
        }

        facet.add(new Document("$group", new Document("_id",
            new Document("$dateToString",
                new Document("format", "%Y-%m-%d")
                    .append("date", "$" + EARLIEST_FIRST_LOGIN)
                    .append("timezone", timeZone)))
            .append(ALIAS_COUNT, new Document("$sum", 1))));
        facet.add(new Document("$sort", new Document("_id", 1)));
        return facet;
    }

    private static List<Document> countryFacet(Date startDate) {
        final List<Document> facet = new ArrayList<>();
        facet.add(new Document("$unwind", "$" + PlayerFields.IP_ADDRESSES));

        if (startDate != null) {
            facet.add(new Document("$match", new Document("$or", List.of(
                new Document(PlayerFields.IP_FIRST_LOGIN, new Document("$gt", startDate)),
                new Document(PlayerFields.IP_ADDRESSES + ".logins", new Document("$elemMatch", new Document("$gt", startDate)))
            ))));
        }
        facet.add(new Document("$match", new Document(PlayerFields.IP_ADDRESSES + ".country",
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

        facet.add(new Document("$group", new Document("_id", "$" + PlayerFields.IP_ADDRESSES + ".country")
            .append(ALIAS_COUNT, new Document("$sum", loginCountExpr))));
        facet.add(new Document("$sort", new Document(ALIAS_COUNT, -1)));
        facet.add(new Document("$limit", 20));
        return facet;
    }

    private static List<Document> suspiciousFacet(Date startDate) {
        final List<Document> facet = new ArrayList<>();
        facet.add(new Document("$unwind", "$" + PlayerFields.IP_ADDRESSES));

        if (startDate != null) {
            facet.add(new Document("$match", new Document("$or", List.of(
                new Document(PlayerFields.IP_FIRST_LOGIN, new Document("$gt", startDate)),
                new Document(PlayerFields.IP_ADDRESSES + ".logins", new Document("$elemMatch", new Document("$gt", startDate)))
            ))));
        }
        facet.add(new Document("$group", new Document("_id", null)
            .append("proxyCount", new Document("$sum", new Document("$cond", List.of("$" + PlayerFields.IP_ADDRESSES + ".proxy", 1, 0))))
            .append("hostingCount", new Document("$sum", new Document("$cond", List.of("$" + PlayerFields.IP_ADDRESSES + ".hosting", 1, 0))))));
        return facet;
    }

    public record OverviewStats(
        long totalTickets,
        long totalPlayers,
        long activeTickets,
        long recentTickets,
        long previousTickets,
        long recentPlayers,
        long previousPlayers
    ) {
    }

    public record IdCountResult(String id, int count) {}

    public record CategoryAvgResolution(String id, double avgMillis) {}

    public record PunishmentAnalyticsFacet(
        List<TypeOrdinalCount> byType,
        List<IssuerCount> byStaff,
        List<DateCountResult> daily
    ) {}

    public record PlayerActivityFacet(
        List<DateCountResult> newPlayers,
        List<CountryCount> byCountry,
        SuspiciousCounts suspicious
    ) {}

    public record TypeOrdinalCount(Integer typeOrdinal, int count) {}

    public record IssuerCount(String issuerId, int count) {}

    public record DateCountResult(String date, int count) {}

    public record CountryCount(String country, int count) {}

    public record SuspiciousCounts(int proxyCount, int hostingCount) {}

    private static double doubleValueOrZero(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static List<Document> facetDocuments(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Document> documents = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Document document) {
                documents.add(document);
            }
        }
        return documents;
    }

    private static List<TypeOrdinalCount> toTypeOrdinalCounts(Object value) {
        return facetDocuments(value).stream()
            .map(doc -> new TypeOrdinalCount(
                doc.get("_id") instanceof Number number ? number.intValue() : null,
                ProtoMapperSupport.intValueOrZero(doc.get(ALIAS_COUNT))))
            .toList();
    }

    private static List<IssuerCount> toIssuerCounts(Object value) {
        return facetDocuments(value).stream()
            .map(doc -> new IssuerCount(
                doc.get("_id") instanceof String issuerId ? issuerId : null,
                ProtoMapperSupport.intValueOrZero(doc.get(ALIAS_COUNT))))
            .toList();
    }

    private static List<DateCountResult> toDateCounts(Object value) {
        return facetDocuments(value).stream()
            .map(doc -> new DateCountResult(
                doc.getString("_id"),
                ProtoMapperSupport.intValueOrZero(doc.get(ALIAS_COUNT))))
            .toList();
    }

    private static List<CountryCount> toCountryCounts(Object value) {
        return facetDocuments(value).stream()
            .map(doc -> new CountryCount(
                doc.getString("_id"),
                ProtoMapperSupport.intValueOrZero(doc.get(ALIAS_COUNT))))
            .toList();
    }

    private static SuspiciousCounts toSuspiciousCounts(Object value) {
        List<Document> documents = facetDocuments(value);
        if (documents.isEmpty()) {
            return new SuspiciousCounts(0, 0);
        }
        Document first = documents.get(0);
        return new SuspiciousCounts(
            ProtoMapperSupport.intValueOrZero(first.get("proxyCount")),
            ProtoMapperSupport.intValueOrZero(first.get("hostingCount")));
    }
}
