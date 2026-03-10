package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.TicketFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.ticket.util.TicketAssigneeUtil;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
public class TicketMongoRepository extends AbstractServerMongoRepository<Ticket> {
    private static final String FILTER_ALL = "all";
    private static final String FILTER_OPEN = "open";
    private static final String FILTER_CLOSED = "closed";
    private static final String ASSIGNEE_NONE = "none";

    private static final String SORT_OLDEST = "oldest";
    private static final String SORT_RECENTLY_UPDATED = "recently-updated";
    private static final String SORT_LEAST_RECENTLY_UPDATED = "least-recently-updated";

    public TicketMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(Ticket.class, CollectionName.TICKETS, tenantMongoAccess);
    }

    public void updateState(Server server, Ticket ticket) {
        Query query = Query.query(MongoQueries.where(TicketFields.ID).is(ticket.getId()));
        Update update = new Update()
                .set(TicketFields.REPLIES, ticket.getReplies())
                .set(TicketFields.LOCKED, ticket.isLocked())
                .set(TicketFields.STATUS, ticket.getStatus() != null ? ticket.getStatus().getId() : null)
                .set(TicketFields.UPDATED_AT, ticket.getUpdatedAt());
        updateFirst(server, query, update);
    }

    public List<Ticket> findByIds(Server server, List<String> ticketIds) {
        return find(server, Query.query(MongoQueries.where(TicketFields.ID).in(ticketIds)));
    }

    public Optional<Ticket> findByTicketId(Server server, String ticketId) {
        return findOne(server, Query.query(MongoQueries.where(TicketFields.ID).is(ticketId)));
    }

    public List<Ticket> findReportedPlayerTickets(Server server, String reportedPlayerUuid, int limit) {
        Query query = Query.query(MongoQueries.where(TicketFields.REPORTED_PLAYER_UUID).is(reportedPlayerUuid));
        query.limit(limit);
        return find(server, query);
    }

    public List<Ticket> findMinecraftTickets(Server server, String status, String type, int limit) {
        List<Criteria> conditions = new ArrayList<>();

        if (status != null && !status.isBlank() && !FILTER_ALL.equalsIgnoreCase(status)) {
            conditions.add(MongoQueries.where(TicketFields.STATUS).is(TicketStatus.fromCanonicalId(status).getId()));
        }

        if (type != null && !type.isBlank()) {
            conditions.add(buildTypeCriteria(type));
        } else {
            conditions.add(MongoQueries.where(TicketFields.TYPE).in(
                    TicketCategory.SUPPORT.getId(),
                    TicketCategory.BUG.getId(),
                    TicketCategory.APPEAL.getId()
            ));
        }

        Query query = conditions.isEmpty()
                ? new Query()
                : Query.query(new Criteria().andOperator(conditions.toArray(new Criteria[0])));
        query.with(MongoQueries.sort(Sort.Direction.DESC, TicketFields.CREATED));
        query.limit(Math.min(limit, 100));
        return find(server, query);
    }

    public List<Ticket> findRecentByCreator(Server server, String creatorUuid, int limit) {
        Query query = Query.query(MongoQueries.where(TicketFields.CREATOR_UUID).is(creatorUuid));
        query.with(MongoQueries.sort(Sort.Direction.DESC, TicketFields.CREATED));
        query.limit(Math.min(limit, 50));
        return find(server, query);
    }

    public List<Ticket> findReports(Server server, String status, String playerUuid, int limit, boolean sortByCreatedDesc) {
        Query query = Query.query(buildReportCriteria(status, playerUuid));
        if (sortByCreatedDesc) {
            query.with(MongoQueries.sort(Sort.Direction.DESC, TicketFields.CREATED));
        }
        query.limit(Math.min(limit, 100));
        return find(server, query);
    }

    public TicketSearchPage searchTickets(Server server, TicketSearchFilter filter, TicketSortOption sort, int page, int limit) {
        Query countQuery = buildSearchQuery(filter, true);
        long total = count(server, countQuery);

        Query pagedQuery = Query.of(countQuery);
        pagedQuery.with(sort.toMongoSort());
        pagedQuery.skip(Math.max(page - 1, 0L) * limit).limit(limit);
        List<Ticket> tickets = find(server, pagedQuery);
        return new TicketSearchPage(tickets, total);
    }

    public TicketCounts countTickets(Server server, TicketSearchFilter filter) {
        Query openQuery = buildSearchQuery(filter, false);
        openQuery.addCriteria(MongoQueries.where(TicketFields.LOCKED).ne(true));

        Query closedQuery = buildSearchQuery(filter, false);
        closedQuery.addCriteria(MongoQueries.where(TicketFields.LOCKED).is(true));

        return new TicketCounts(count(server, openQuery), count(server, closedQuery));
    }

    public List<Ticket> findByPlayer(Server server, String playerUuid) {
        Criteria criteria = new Criteria().andOperator(
                new Criteria().orOperator(
                        MongoQueries.where(TicketFields.CREATOR_UUID).is(playerUuid),
                        MongoQueries.where(TicketFields.REPORTED_PLAYER_UUID).is(playerUuid)
                ),
                MongoQueries.where(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId())
        );
        Query query = Query.query(criteria).with(MongoQueries.sort(Sort.Direction.DESC, TicketFields.CREATED));
        return find(server, query);
    }

    public List<Ticket> findByTag(Server server, String tag) {
        return find(server, Query.query(MongoQueries.where(TicketFields.TAGS).is(tag)));
    }

    public List<Ticket> findRecentActiveTicketsWithRepliesByIds(Server server, List<String> ticketIds, int limit) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return List.of();
        }

        Query query = Query.query(
                MongoQueries.where(TicketFields.ID).in(ticketIds)
                        .and(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId())
                        .and(TicketFields.REPLIES + ".0").exists(true)
        );
        query.with(MongoQueries.sort(Sort.Direction.DESC, TicketFields.UPDATED_AT));
        query.limit(limit);
        return find(server, query);
    }

    public List<Ticket> findRecentAssignedTicketsWithReplies(Server server, String assignee, int limit) {
        Query query = Query.query(
                MongoQueries.where(TicketFields.ASSIGNED_TO).is(assignee)
                        .and(TicketFields.REPLIES + ".0").exists(true)
                        .and(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId())
        );
        query.with(MongoQueries.sort(Sort.Direction.DESC, TicketFields.UPDATED_AT));
        query.limit(limit);
        return find(server, query);
    }

    public boolean existsByTicketId(Server server, String ticketId) {
        return exists(server, Query.query(MongoQueries.where(TicketFields.ID).is(ticketId)));
    }

    private Query buildSearchQuery(TicketSearchFilter filter, boolean includeReplySearch) {
        TicketStatus requestedStatus = tryResolveTicketStatus(filter.status());
        Query query = new Query();
        if (requestedStatus != TicketStatus.UNFINISHED) {
            query.addCriteria(MongoQueries.where(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId()));
        }

        if (filter.search() != null && !filter.search().isBlank()) {
            String escapedSearch = Pattern.quote(filter.search());
            List<Criteria> searchCriteria = new ArrayList<>();
            searchCriteria.add(MongoQueries.where(TicketFields.ID).regex(escapedSearch, "i"));
            searchCriteria.add(MongoQueries.where(TicketFields.SUBJECT).regex(escapedSearch, "i"));
            searchCriteria.add(MongoQueries.where(TicketFields.CREATOR_NAME).regex(escapedSearch, "i"));
            if (includeReplySearch) {
                searchCriteria.add(MongoQueries.where(TicketFields.REPLY_NAME).regex(escapedSearch, "i"));
                searchCriteria.add(MongoQueries.where(TicketFields.REPLY_CONTENT).regex(escapedSearch, "i"));
            }
            query.addCriteria(new Criteria().orOperator(searchCriteria.toArray(new Criteria[0])));
        }

        if (filter.status() != null && !filter.status().isBlank() && !filter.status().equalsIgnoreCase(FILTER_ALL)) {
            if (requestedStatus == TicketStatus.UNFINISHED) {
                query.addCriteria(MongoQueries.where(TicketFields.STATUS).is(TicketStatus.UNFINISHED.getId()));
            } else if (requestedStatus != null) {
                query.addCriteria(requestedStatus.isTerminal()
                        ? MongoQueries.where(TicketFields.LOCKED).is(true)
                        : MongoQueries.where(TicketFields.LOCKED).ne(true));
            } else if (filter.status().equalsIgnoreCase(FILTER_OPEN)) {
                query.addCriteria(MongoQueries.where(TicketFields.LOCKED).ne(true));
            } else if (filter.status().equalsIgnoreCase(FILTER_CLOSED)) {
                query.addCriteria(MongoQueries.where(TicketFields.LOCKED).is(true));
            }
        }

        if (filter.types() != null && !filter.types().isEmpty()) {
            List<String> validTypes = filter.types().stream()
                    .filter(type -> type != null && !type.isBlank() && !type.equals(FILTER_ALL))
                    .toList();
            if (!validTypes.isEmpty()) {
                List<Criteria> typeCriteria = validTypes.stream()
                        .map(this::buildTypeCriteria)
                        .toList();
                query.addCriteria(new Criteria().orOperator(typeCriteria.toArray(new Criteria[0])));
            }
        }

        if (filter.author() != null && !filter.author().isBlank()) {
            String escapedAuthor = Pattern.quote(filter.author());
            query.addCriteria(MongoQueries.where(TicketFields.CREATOR_NAME).regex(escapedAuthor, "i"));
        }

        if (filter.labels() != null && !filter.labels().isEmpty()) {
            query.addCriteria(MongoQueries.where(TicketFields.TAGS).all(filter.labels()));
        }

        Criteria assigneeCriteria = buildAssigneeCriteria(filter.assignees());
        if (assigneeCriteria != null) {
            query.addCriteria(assigneeCriteria);
        }

        return query;
    }

    private Criteria buildReportCriteria(String status, String playerUuid) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(MongoQueries.where(TicketFields.TYPE).in(TicketCategory.reportCategoryIds()));

        if (playerUuid != null && !playerUuid.isBlank()) {
            conditions.add(MongoQueries.where(TicketFields.REPORTED_PLAYER_UUID).is(playerUuid));
        }

        if (status != null && !status.isBlank() && !FILTER_ALL.equalsIgnoreCase(status)) {
            conditions.add(MongoQueries.where(TicketFields.STATUS).is(TicketStatus.fromCanonicalId(status).getId()));
        }

        return conditions.size() == 1
                ? conditions.get(0)
                : new Criteria().andOperator(conditions.toArray(new Criteria[0]));
    }

    private Criteria buildAssigneeCriteria(List<String> assignees) {
        if (assignees == null || assignees.isEmpty()) {
            return null;
        }

        List<Criteria> assigneeCriteriaList = new ArrayList<>();
        for (String assignee : assignees) {
            if (assignee == null || assignee.isBlank()) {
                continue;
            }

            if (ASSIGNEE_NONE.equalsIgnoreCase(assignee)) {
                assigneeCriteriaList.add(buildUnassignedCriteria());
                continue;
            }

            String normalizedAssignee = TicketAssigneeUtil.normalizeSingle(assignee);
            if (normalizedAssignee != null) {
                assigneeCriteriaList.add(MongoQueries.where(TicketFields.ASSIGNED_TO).is(normalizedAssignee));
            }
        }

        if (assigneeCriteriaList.isEmpty()) {
            return null;
        }

        return new Criteria().orOperator(assigneeCriteriaList.toArray(new Criteria[0]));
    }

    private Criteria buildUnassignedCriteria() {
        return new Criteria().orOperator(
                Criteria.where(TicketFields.ASSIGNED_TO).exists(false),
                Criteria.where(TicketFields.ASSIGNED_TO).is(null),
                Criteria.where(TicketFields.ASSIGNED_TO).size(0)
        );
    }

    private Criteria buildTypeCriteria(String type) {
        String normalizedType = normalizeTypeValue(type);

        TicketCategory category = tryResolveCategory(type);
        if (category != null && !TicketCategory.isCanonicalBucket(normalizedType)) {
            return MongoQueries.where(TicketFields.TYPE).is(category.getId());
        }

        List<String> bucketCategoryIds = TicketCategory.categoryIdsForBucket(normalizedType);
        if (!bucketCategoryIds.isEmpty()) {
            return MongoQueries.where(TicketFields.TYPE).in(bucketCategoryIds);
        }

        String escapedType = Pattern.quote(normalizedType);
        return MongoQueries.where(TicketFields.TYPE).regex("^" + escapedType + "$", "i");
    }

    private TicketStatus tryResolveTicketStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank() || FILTER_ALL.equalsIgnoreCase(rawStatus)) {
            return null;
        }
        try {
            return TicketStatus.fromCanonicalId(rawStatus);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private TicketCategory tryResolveCategory(String rawType) {
        if (rawType == null || rawType.isBlank() || FILTER_ALL.equalsIgnoreCase(rawType)) {
            return null;
        }
        try {
            return TicketCategory.fromCanonicalId(rawType);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeTypeValue(String type) {
        if (type == null) {
            return "";
        }
        return type.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    public record TicketSearchFilter(
            String search,
            String status,
            List<String> types,
            String author,
            List<String> labels,
            List<String> assignees
    ) {}

    public record TicketSearchPage(List<Ticket> tickets, long total) {}

    public record TicketCounts(long open, long closed) {}

    public enum TicketSortOption {
        NEWEST(MongoQueries.sort(Sort.Direction.DESC, TicketFields.CREATED)),
        OLDEST(MongoQueries.sort(Sort.Direction.ASC, TicketFields.CREATED)),
        RECENTLY_UPDATED(MongoQueries.sort(Sort.Direction.DESC, TicketFields.UPDATED_AT)),
        LEAST_RECENTLY_UPDATED(MongoQueries.sort(Sort.Direction.ASC, TicketFields.UPDATED_AT));

        private final Sort mongoSort;

        TicketSortOption(Sort mongoSort) {
            this.mongoSort = mongoSort;
        }

        public Sort toMongoSort() {
            return mongoSort;
        }

        public static TicketSortOption from(String rawSort) {
            if (rawSort == null || rawSort.isBlank()) {
                return NEWEST;
            }
            return switch (rawSort) {
                case SORT_OLDEST -> OLDEST;
                case SORT_RECENTLY_UPDATED -> RECENTLY_UPDATED;
                case SORT_LEAST_RECENTLY_UPDATED -> LEAST_RECENTLY_UPDATED;
                default -> NEWEST;
            };
        }
    }
}
