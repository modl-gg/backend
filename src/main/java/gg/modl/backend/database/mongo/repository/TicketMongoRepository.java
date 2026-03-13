package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.TicketFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.AppealWorkflowStatus;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.ticket.util.TicketAssigneeUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

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

    public TicketSearchPage searchTickets(Server server, TicketSearchFilter filter, TicketSortOption sort, int page, int limit) {
        Query countQuery = buildSearchQuery(filter, true);
        long total = count(server, countQuery);

        Query pagedQuery = Query.of(countQuery);
        pagedQuery.with(sort.toMongoSort());
        pagedQuery.skip(Math.max(page - 1, 0L) * limit).limit(limit);
        List<Ticket> tickets = find(server, pagedQuery);
        return new TicketSearchPage(tickets, total);
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
            List<String> validTypes = filter.types()
                .stream()
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

    public List<Ticket> findAppealsByPunishmentId(Server server, String punishmentId) {
        Query query = Query.query(
            MongoQueries.where(TicketFields.TYPE).is(TicketCategory.APPEAL.getId())
                .and(TicketFields.DATA + ".punishmentId").is(punishmentId)
        );
        return find(server, query);
    }

    public boolean existsAppealForPunishment(Server server, String punishmentId) {
        Query query = Query.query(
            MongoQueries.where(TicketFields.TYPE).is(TicketCategory.APPEAL.getId())
                .and(TicketFields.DATA + ".punishmentId").is(punishmentId)
        );
        return exists(server, query);
    }

    public Ticket saveAppeal(Server server, Ticket appeal) {
        return saveEntity(server, appeal);
    }

    public void pushReply(Server server, String ticketId, TicketReply reply) {
        Query query = Query.query(MongoQueries.where(TicketFields.ID).is(ticketId));
        Update update = new Update()
            .push(TicketFields.REPLIES, reply)
            .set(TicketFields.UPDATED_AT, new Date());
        updateFirst(server, query, update);
    }

    public void updateAppealState(Server server, String ticketId,
                                   AppealWorkflowStatus appealWorkflowStatus,
                                   TicketStatus status, Boolean locked,
                                   Map<String, Object> data,
                                   List<TicketReply> systemReplies) {
        Query query = Query.query(MongoQueries.where(TicketFields.ID).is(ticketId));
        Update update = new Update().set(TicketFields.UPDATED_AT, new Date());

        if (appealWorkflowStatus != null) {
            update.set("appealWorkflowStatus", appealWorkflowStatus.getId());
        }
        if (status != null) {
            update.set(TicketFields.STATUS, status.getId());
        }
        if (locked != null) {
            update.set(TicketFields.LOCKED, locked);
        }
        if (data != null) {
            update.set("data", data);
        }
        if (systemReplies != null) {
            for (TicketReply reply : systemReplies) {
                update.push(TicketFields.REPLIES, reply);
            }
        }
        updateFirst(server, query, update);
    }

    public List<Ticket> findCreatedAfterExcludingUnfinished(Server server, Date after, int limit) {
        Query query = Query.query(
            MongoQueries.where(TicketFields.CREATED).gte(after)
                .and(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId())
        );
        query.limit(limit);
        return find(server, query);
    }

    public long countUnresolvedReports(Server server) {
        Query query = Query.query(new Criteria().andOperator(
            MongoQueries.where(TicketFields.TYPE).in(TicketCategory.reportCategoryIds()),
            MongoQueries.where(TicketFields.STATUS).in(TicketStatus.OPEN.getId(), TicketStatus.UNFINISHED.getId())
        ));
        return count(server, query);
    }

    public long countUnresolvedTickets(Server server) {
        Query query = Query.query(new Criteria().andOperator(
            MongoQueries.where(TicketFields.TYPE).in(
                TicketCategory.SUPPORT.getId(),
                TicketCategory.BUG.getId(),
                TicketCategory.APPEAL.getId()
            ),
            MongoQueries.where(TicketFields.STATUS).in(TicketStatus.OPEN.getId(), TicketStatus.UNFINISHED.getId())
        ));
        return count(server, query);
    }

    public long countAll(Server server) {
        return count(server, new Query());
    }

    public long countByStatus(Server server, TicketStatus status) {
        return count(server, Query.query(MongoQueries.where(TicketFields.STATUS).is(status.getId())));
    }

    public long countCreatedAfter(Server server, Date after) {
        return count(server, Query.query(MongoQueries.where(TicketFields.CREATED).gte(after)));
    }

    public long countCreatedBetween(Server server, Date from, Date to) {
        return count(server, Query.query(MongoQueries.where(TicketFields.CREATED).gte(from).lt(to)));
    }

    public List<Ticket> findRecentWithProjection(Server server, int limit) {
        Query query = Query.query(MongoQueries.where(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId()))
            .with(MongoQueries.sort(Sort.Direction.DESC, TicketFields.CREATED))
            .limit(limit);

        MongoQueries.include(
            query,
            TicketFields.SUBJECT,
            TicketFields.STATUS,
            TicketFields.PRIORITY,
            TicketFields.CREATED,
            TicketFields.CREATOR_NAME,
            TicketFields.TYPE,
            TicketFields.REPLIES
        );
        query.fields().slice(TicketFields.REPLIES, 1);

        return find(server, query);
    }

    public List<Ticket> findStaffActivityTickets(Server server, String staffUsername, String normalizedStaffUsername, Date cutoffDate, int limit) {
        List<Criteria> staffMatchCriteria = new ArrayList<>();
        staffMatchCriteria.add(MongoQueries.where(TicketFields.CREATOR_NAME).is(staffUsername));
        if (normalizedStaffUsername != null) {
            staffMatchCriteria.add(MongoQueries.where(TicketFields.ASSIGNED_TO).is(normalizedStaffUsername));
        }
        staffMatchCriteria.add(MongoQueries.where(TicketFields.REPLY_NAME).is(staffUsername));

        Query query = Query.query(new Criteria().andOperator(
            MongoQueries.where(TicketFields.UPDATED_AT).gte(cutoffDate),
            new Criteria().orOperator(staffMatchCriteria.toArray(new Criteria[0]))
        )).with(MongoQueries.sort(Sort.Direction.DESC, TicketFields.UPDATED_AT)).limit(limit);

        MongoQueries.include(
            query,
            TicketFields.SUBJECT,
            TicketFields.TYPE,
            TicketFields.CREATED,
            TicketFields.CREATOR_NAME,
            TicketFields.REPLY_NAME,
            TicketFields.REPLY_CREATED
        );

        return find(server, query);
    }

    public enum TicketSortOption {
        NEWEST(MongoQueries.sort(Sort.Direction.DESC, TicketFields.CREATED)),
        OLDEST(MongoQueries.sort(Sort.Direction.ASC, TicketFields.CREATED)),
        RECENTLY_UPDATED(MongoQueries.sort(Sort.Direction.DESC, TicketFields.UPDATED_AT)),
        LEAST_RECENTLY_UPDATED(MongoQueries.sort(Sort.Direction.ASC, TicketFields.UPDATED_AT));

        private final Sort mongoSort;

        TicketSortOption(Sort mongoSort) {
            this.mongoSort = mongoSort;
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

        public Sort toMongoSort() {
            return mongoSort;
        }
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
}
