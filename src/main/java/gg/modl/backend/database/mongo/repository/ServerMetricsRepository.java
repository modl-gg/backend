package gg.modl.backend.database.mongo.repository;

import static gg.modl.backend.database.mongo.MongoAggregationResults.extractFacetCount;
import static gg.modl.backend.database.mongo.MongoAggregationResults.extractLong;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class ServerMetricsRepository extends AbstractGlobalMongoRepository<Server> {
    private static final String ALIAS_TOTAL = "total";
    private static final String ALIAS_TOTAL_USERS = "totalUsers";
    private static final String ALIAS_TOTAL_TICKETS = "totalTickets";
    private static final String ALIAS_VALUE = "value";
    private static final String ALIAS_NAME = "name";
    private static final String ALIAS_DATE = "date";
    private static final String ALIAS_SERVERS = "servers";
    private static final String ALIAS_VALUE_SOURCE = "valueSource";

    private static final String METRIC_USERS = "users";
    private static final String METRIC_TICKETS = "tickets";

    public ServerMetricsRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public long countAll() {
        return count(new Query());
    }

    public long countByProvisioningStatus(ProvisioningStatus status) {
        return count(Query.query(Criteria.where(ServerFields.PROVISIONING_STATUS).is(status)));
    }

    public long countActiveSince(Date activityCutoff) {
        return count(Query.query(Criteria.where(ServerFields.LAST_ACTIVITY_AT).gte(activityCutoff)));
    }

    public UsageTotals getUsageTotals() {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.group()
                .sum(ServerFields.USER_COUNT).as(ALIAS_TOTAL_USERS)
                .sum(ServerFields.TICKET_COUNT).as(ALIAS_TOTAL_TICKETS)
        );
        Document result = aggregate(aggregation, Document.class).getUniqueMappedResult();
        return new UsageTotals(
            extractLong(result, ALIAS_TOTAL_USERS),
            extractLong(result, ALIAS_TOTAL_TICKETS)
        );
    }

    public List<NameValueResult> aggregatePlanCounts() {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.group(ServerFields.PLAN).count().as(ALIAS_VALUE),
            Aggregation.project().and("_id").as(ALIAS_NAME).and(ALIAS_VALUE).as(ALIAS_VALUE)
        );
        return aggregate(aggregation, NameValueResult.class).getMappedResults();
    }

    public List<NameValueResult> aggregateProvisioningStatusCounts() {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.group(ServerFields.PROVISIONING_STATUS).count().as(ALIAS_VALUE),
            Aggregation.project().and("_id").as(ALIAS_NAME).and(ALIAS_VALUE).as(ALIAS_VALUE)
        );
        return aggregate(aggregation, NameValueResult.class).getMappedResults();
    }

    public List<DateServersResult> findRegistrationTrend(Date startDate) {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(ServerFields.CREATED_AT).gte(startDate)),
            Aggregation.project()
                .and(DateOperators.DateToString.dateOf(ServerFields.CREATED_AT).toString("%Y-%m-%d")).as(ALIAS_DATE),
            Aggregation.group(ALIAS_DATE).count().as(ALIAS_SERVERS),
            Aggregation.sort(Sort.Direction.ASC, "_id"),
            Aggregation.project().and("_id").as(ALIAS_DATE).and(ALIAS_SERVERS).as(ALIAS_SERVERS)
        );
        return aggregate(aggregation, DateServersResult.class).getMappedResults();
    }

    public List<Server> findTopCompletedVerifiedByUserCount(int limit) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.COMPLETED),
            Criteria.where(ServerFields.EMAIL_VERIFIED).is(true),
            Criteria.where(ServerFields.USER_COUNT).gt(0)
        ));
        query.with(Sort.by(Sort.Direction.DESC, ServerFields.USER_COUNT));
        query.limit(limit);
        return find(query);
    }

    public List<DateValueResult> aggregateHistoricalMetric(String metric, Date startDate) {
        ProjectionOperation projectDateStage = Aggregation.project()
            .and(DateOperators.DateToString.dateOf(ServerFields.CREATED_AT).toString("%Y-%m-%d")).as(ALIAS_DATE);

        if (METRIC_USERS.equals(metric) || METRIC_TICKETS.equals(metric)) {
            String sumField = METRIC_USERS.equals(metric) ? ServerFields.USER_COUNT : ServerFields.TICKET_COUNT;
            Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where(ServerFields.CREATED_AT).gte(startDate)),
                projectDateStage.and(sumField).as(ALIAS_VALUE_SOURCE),
                Aggregation.group(ALIAS_DATE).sum(ALIAS_VALUE_SOURCE).as(ALIAS_VALUE),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project().and("_id").as(ALIAS_DATE).and(ALIAS_VALUE).as(ALIAS_VALUE)
            );
            return aggregate(aggregation, DateValueResult.class).getMappedResults();
        }

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(ServerFields.CREATED_AT).gte(startDate)),
            projectDateStage,
            Aggregation.group(ALIAS_DATE).count().as(ALIAS_VALUE),
            Aggregation.sort(Sort.Direction.ASC, "_id"),
            Aggregation.project().and("_id").as(ALIAS_DATE).and(ALIAS_VALUE).as(ALIAS_VALUE)
        );
        return aggregate(aggregation, DateValueResult.class).getMappedResults();
    }

    public DashboardStats aggregateDashboardStats(Date startDate, Date previousStartDate) {
        Document facet = new Document()
            .append(ALIAS_TOTAL, List.of(new Document("$count", "n")))
            .append("active", List.of(
                new Document("$match", new Document(ServerFields.PROVISIONING_STATUS, ProvisioningStatus.COMPLETED.name())
                    .append(ServerFields.EMAIL_VERIFIED, true)),
                new Document("$count", "n")
            ))
            .append("withUsers", List.of(
                new Document("$match", new Document(ServerFields.PROVISIONING_STATUS, ProvisioningStatus.COMPLETED.name())
                    .append(ServerFields.USER_COUNT, new Document("$gt", 0))),
                new Document("$count", "n")
            ))
            .append("currentPeriod", List.of(
                new Document("$match", new Document(ServerFields.CREATED_AT, new Document("$gte", startDate))),
                new Document("$count", "n")
            ))
            .append("previousPeriod", List.of(
                new Document("$match", new Document(ServerFields.CREATED_AT,
                    new Document("$gte", previousStartDate).append("$lt", startDate))),
                new Document("$count", "n")
            ))
            .append("usage", List.of(
                new Document("$group", new Document("_id", null)
                    .append(ALIAS_TOTAL_USERS, new Document("$sum", "$" + ServerFields.USER_COUNT))
                    .append(ALIAS_TOTAL_TICKETS, new Document("$sum", "$" + ServerFields.TICKET_COUNT)))
            ));

        List<Document> pipeline = List.of(new Document("$facet", facet));
        List<Document> results = globalTemplate().getCollection(collectionName())
            .aggregate(pipeline)
            .into(new ArrayList<>());

        if (results.isEmpty()) {
            return new DashboardStats(0, 0, 0, 0, 0, 0, 0);
        }

        Document doc = results.get(0);
        long total = extractFacetCount(doc, ALIAS_TOTAL);
        long active = extractFacetCount(doc, "active");
        long withUsers = extractFacetCount(doc, "withUsers");
        long currentPeriod = extractFacetCount(doc, "currentPeriod");
        long previousPeriod = extractFacetCount(doc, "previousPeriod");

        long totalUsers = 0;
        long totalTickets = 0;
        List<?> usageList = doc.getList("usage", Document.class, List.of());
        if (!usageList.isEmpty() && usageList.getFirst() instanceof Document usageDoc) {
            totalUsers = extractLong(usageDoc, ALIAS_TOTAL_USERS);
            totalTickets = extractLong(usageDoc, ALIAS_TOTAL_TICKETS);
        }

        return new DashboardStats(total, active, withUsers, currentPeriod, previousPeriod, totalUsers, totalTickets);
    }

    public MonitoringServerStats aggregateMonitoringServerStats(Date fiveMinutesAgo, Date oneWeekAgo) {
        Document facet = new Document()
            .append(ALIAS_TOTAL, List.of(new Document("$count", "n")))
            .append("active", List.of(
                new Document("$match", new Document(ServerFields.PROVISIONING_STATUS, ProvisioningStatus.COMPLETED.name())
                    .append(ServerFields.EMAIL_VERIFIED, true)),
                new Document("$count", "n")
            ))
            .append("concurrent", List.of(
                new Document("$match", new Document(ServerFields.LAST_ACTIVITY_AT, new Document("$gte", fiveMinutesAgo))),
                new Document("$count", "n")
            ))
            .append("concurrentPlayers", List.of(
                new Document("$match", new Document(ServerFields.LAST_ACTIVITY_AT, new Document("$gte", fiveMinutesAgo))),
                new Document("$group", new Document("_id", null)
                    .append("sum", new Document("$sum", "$" + ServerFields.ONLINE_PLAYER_COUNT)))
            ))
            .append("pending", List.of(
                new Document("$match", new Document(ServerFields.PROVISIONING_STATUS,
                    new Document("$in", List.of(ProvisioningStatus.PENDING.name(), ProvisioningStatus.IN_PROGRESS.name())))),
                new Document("$count", "n")
            ))
            .append("failed", List.of(
                new Document("$match", new Document(ServerFields.PROVISIONING_STATUS, ProvisioningStatus.FAILED.name())),
                new Document("$count", "n")
            ))
            .append("recentRegistrations", List.of(
                new Document("$match", new Document(ServerFields.CREATED_AT, new Document("$gte", oneWeekAgo))),
                new Document("$count", "n")
            ));

        List<Document> pipeline = List.of(new Document("$facet", facet));
        List<Document> results = globalTemplate().getCollection(collectionName())
            .aggregate(pipeline)
            .into(new ArrayList<>());

        if (results.isEmpty()) {
            return new MonitoringServerStats(0, 0, 0, 0, 0, 0, 0);
        }

        Document doc = results.get(0);
        long concurrentPlayers = 0;
        List<?> cpList = doc.getList("concurrentPlayers", Document.class, List.of());
        if (!cpList.isEmpty() && cpList.getFirst() instanceof Document cpDoc) {
            concurrentPlayers = extractLong(cpDoc, "sum");
        }

        return new MonitoringServerStats(
            extractFacetCount(doc, ALIAS_TOTAL),
            extractFacetCount(doc, "active"),
            extractFacetCount(doc, "concurrent"),
            concurrentPlayers,
            extractFacetCount(doc, "pending"),
            extractFacetCount(doc, "failed"),
            extractFacetCount(doc, "recentRegistrations")
        );
    }

    public record MonitoringServerStats(long total, long active, long concurrent, long concurrentPlayers,
                                         long pending, long failed, long recentRegistrations) {}

    public record DashboardStats(long totalServers, long activeServers, long serversWithData,
                                 long currentPeriodServers, long previousPeriodServers,
                                 long totalUsers, long totalTickets) {}

    public record UsageTotals(long totalUsers, long totalTickets) {}

    public record NameValueResult(String name, int value) {}

    public record DateServersResult(String date, int servers) {}

    public record DateValueResult(String date, long value) {}
}
