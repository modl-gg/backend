package gg.modl.backend.database;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.Server;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MongoIndexBootstrapService {
    private static final String TYPE_OPERATOR = "$type";
    private static final Map<String, Integer> BSON_TYPE_CODES = Map.ofEntries(
        Map.entry("double", 1),
        Map.entry("string", 2),
        Map.entry("object", 3),
        Map.entry("array", 4),
        Map.entry("binData", 5),
        Map.entry("objectId", 7),
        Map.entry("bool", 8),
        Map.entry("date", 9),
        Map.entry("null", 10),
        Map.entry("regex", 11),
        Map.entry("javascript", 13),
        Map.entry("int", 16),
        Map.entry("timestamp", 17),
        Map.entry("long", 18),
        Map.entry("decimal", 19),
        Map.entry("minKey", -1),
        Map.entry("maxKey", 127)
    );

    private static final int BOOTSTRAP_PARALLELISM = 4;

    private final TenantMongoAccess tenantMongoAccess;
    private final TenantMigrationService tenantMigrationService;

    @PostConstruct
    public void initGlobalIndexes() {
        try {
            createGlobalIndexes(tenantMongoAccess.global());
        } catch (Exception e) {
            log.error("Failed to create global database indexes", e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapExistingTenants() {
        List<Server> targets;
        try {
            targets = loadBootstrapTargets();
        } catch (Exception e) {
            log.error("Failed to list servers for tenant bootstrap", e);
            return;
        }

        if (targets.isEmpty()) {
            log.info("Bootstrapping schema for 0 existing tenants");
            return;
        }

        dispatchTenantBootstrap(targets);
    }

    private List<Server> loadBootstrapTargets() {
        Query query = new Query();
        query.fields().include(ServerFields.ID).include(ServerFields.DATABASE_NAME);
        return tenantMongoAccess.global()
            .find(query, Server.class, CollectionName.MODL_SERVERS)
            .stream()
            .filter(server -> server != null
                && server.getDatabaseName() != null
                && !server.getDatabaseName().isBlank())
            .toList();
    }

    private void dispatchTenantBootstrap(List<Server> targets) {
        log.info("Bootstrapping schema for {} existing tenants", targets.size());
        int parallelism = Math.min(BOOTSTRAP_PARALLELISM, targets.size());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, bootstrapThreadFactory());
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        CompletableFuture<?>[] tasks = targets.stream()
            .map(server -> CompletableFuture.runAsync(() -> bootstrapTenant(server, succeeded, failed), executor))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(tasks).whenComplete((ignored, throwable) -> {
            log.info("Tenant schema bootstrap complete succeeded={} failed={}", succeeded.get(), failed.get());
            executor.shutdown();
        });
    }

    private void bootstrapTenant(Server server, AtomicInteger succeeded, AtomicInteger failed) {
        try {
            log.debug("Bootstrapping schema for server id={} database={}",
                server.getId(), server.getDatabaseName());
            MongoTemplate template = tenantMongoAccess.forServer(server);
            tenantMigrationService.applyMigrationsForTenant(template);
            createTenantIndexes(template);
            succeeded.incrementAndGet();
        } catch (Exception e) {
            failed.incrementAndGet();
            log.warn("Failed to bootstrap schema for server id={} database={}",
                server.getId(), server.getDatabaseName(), e);
        }
    }

    private ThreadFactory bootstrapThreadFactory() {
        AtomicInteger threadNumber = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "tenant-bootstrap-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private void createGlobalIndexes(MongoTemplate template) {
        ensureIndexes(template, CollectionName.MODL_SERVERS, List.of(
            IndexSpec.standard("uidx_servers_serverName", doc("serverName", 1), true, false),
            IndexSpec.standard("uidx_servers_customDomain", doc("customDomain", 1), true, false),
            IndexSpec.standard("uidx_servers_adminEmail", doc("adminEmail", 1), true, false),
            IndexSpec.standard("idx_servers_emailVerified", doc("emailVerified", 1), false, false),
            IndexSpec.standard("uidx_servers_emailVerificationToken", doc("emailVerificationToken", 1), true, true),
            IndexSpec.standard("idx_servers_provisioningStatus", doc("provisioningStatus", 1), false, false),
            IndexSpec.standard(
                "idx_servers_registration_cleanup",
                doc("emailVerified", 1).append("provisioningStatus", 1).append("createdAt", 1).append("emailVerificationToken", 1),
                false,
                true
            ),
            IndexSpec.standard("uidx_servers_provisioningSignInToken", doc("provisioningSignInToken", 1), true, true),
            IndexSpec.standard("uidx_servers_stripeCustomerId", doc("stripeCustomerId", 1), true, true),
            IndexSpec.standard("uidx_servers_stripeSubscriptionId", doc("stripeSubscriptionId", 1), true, true),
            IndexSpec.standard("uidx_servers_customDomainOverride", doc("customDomainOverride", 1), true, true),
            IndexSpec.standard("uidx_servers_customDomainCloudflareId", doc("customDomainCloudflareId", 1), true, true),
            IndexSpec.standard("uidx_servers_cliSetupToken", doc("cliSetupToken", 1), true, true),
            IndexSpec.standard("uidx_servers_apiKey", doc("apiKey", 1), true, true),
            IndexSpec.standard("idx_servers_userCount", doc("userCount", 1), false, false),
            IndexSpec.standard("idx_servers_ticketCount", doc("ticketCount", 1), false, false),
            IndexSpec.standard("idx_servers_lastStatsUpdatedAt", doc("lastStatsUpdatedAt", 1), false, false),
            IndexSpec.standard("idx_servers_createdAt", doc("createdAt", 1), false, false),
            IndexSpec.standard("idx_servers_lastActivityAt", doc("lastActivityAt", -1), false, true)
        ));

        ensureIndexes(template, CollectionName.METRIC_SNAPSHOTS, List.of(
            IndexSpec.standard("uidx_metric_snapshots_date", doc("date", 1), true, false)
        ));

        ensureIndexes(template, CollectionName.REPLAY_LITE_REPLAYS, List.of(
            IndexSpec.standard("uidx_replay_lite_objectKey", doc("objectKey", 1), true, false),
            IndexSpec.standard("idx_replay_lite_expiresAt", doc("expiresAt", 1), false, true),
            IndexSpec.standard(
                "idx_replay_lite_server_status_confirmedAt",
                doc("pluginServerUuid", 1).append("status", 1).append("confirmedAt", 1),
                false,
                false
            ),
            IndexSpec.standard(
                "idx_replay_lite_server_status_createdAt",
                doc("pluginServerUuid", 1).append("status", 1).append("createdAt", 1),
                false,
                false
            ),
            IndexSpec.standard("idx_replay_lite_status_createdAt", doc("status", 1).append("createdAt", 1), false, false)
        ));

        ensureIndexes(template, CollectionName.REPLAY_LITE_DAILY_QUOTAS, List.of(
            IndexSpec.standard(
                "uidx_replay_lite_daily_quotas_server_day",
                doc("pluginServerUuid", 1).append("day", 1),
                true,
                false
            ),
            IndexSpec.ttl("idx_replay_lite_daily_quotas_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, "admin_users", List.of(
            IndexSpec.standard("uidx_admin_users_email", doc("email", 1), true, false)
        ));

        ensureIndexes(template, CollectionName.SYSTEM_ALERTS, List.of(
            IndexSpec.standard("idx_system_alerts_expiresAt", doc("expiresAt", 1), false, false),
            IndexSpec.standard("idx_system_alerts_createdAt", doc("createdAt", -1), false, false),
            IndexSpec.standard("idx_system_alerts_audience_expiresAt", doc("audience", 1).append("expiresAt", 1), false, false)
        ));

        ensureIndexes(template, CollectionName.SERVER_INSTANCE_SNAPSHOTS, List.of(
            IndexSpec.standard("uidx_server_instance_snapshots_date", doc("date", 1), true, false)
        ));

        ensureIndexes(template, CollectionName.EVIDENCE_UPLOAD_TOKENS, List.of(
            IndexSpec.ttl("idx_evidence_upload_tokens_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, CollectionName.BETA_AUDIT, List.of(
            IndexSpec.standard("idx_beta_audit_serverId_timestamp", doc("serverId", 1).append("timestamp", -1), false, false)
        ));
    }

    public void createTenantIndexes(MongoTemplate template) {
        ensureIndexes(template, CollectionName.SETTINGS, List.of(
            IndexSpec.standard("uidx_settings_type", doc("type", 1), true, false)
        ));

        ensureIndexes(template, CollectionName.PLAYERS, List.of(
            IndexSpec.partialUnique("uidx_players_minecraftUuid", doc("minecraftUuid", 1),
                new Document("minecraftUuid", new Document("$type", "string"))),
            IndexSpec.standard("idx_players_punishments_issued_desc", doc("punishments.issued", -1), false, false),
            IndexSpec.standard(
                "idx_players_punishments_issuerName_issued_desc",
                doc("punishments.issuerName", 1).append("punishments.issued", -1),
                false,
                false
            ),
            IndexSpec.standard(
                "idx_players_punishments_issuerId_issued_desc",
                doc("punishments.issuerId", 1).append("punishments.issued", -1),
                false,
                true
            ),
            IndexSpec.standard("idx_players_ipAddresses_ipAddress", doc("ipAddresses.ipAddress", 1), false, false),
            IndexSpec.standard("idx_players_usernames_username", doc("usernames.username", 1), false, false),
            IndexSpec.collated("idx_players_usernames_username_ci", doc("usernames.username", 1),
                Collation.of("en").strength(2)),
            IndexSpec.standard("idx_players_punishments_id", doc("punishments.id", 1), false, true),
            IndexSpec.standard("idx_players_data_isOnline", doc("data.isOnline", 1), false, true),
            IndexSpec.standard("idx_players_ipAddresses_firstLogin", doc("ipAddresses.firstLogin", -1), false, false)
        ));

        ensureIndexes(template, CollectionName.STAFF, List.of(
            IndexSpec.standard("uidx_staff_email", doc("email", 1), true, false),
            IndexSpec.standard("uidx_staff_username", doc("username", 1), true, false),
            IndexSpec.standard("sidx_staff_assignedMinecraftUuid", doc("assignedMinecraftUuid", 1), false, true)
        ));

        ensureIndexes(template, CollectionName.STAFF_ROLES, List.of(
            IndexSpec.standard("uidx_staff_roles_name", doc("name", 1), true, false),
            IndexSpec.standard("idx_staff_roles_order", doc("order", 1), false, false)
        ));

        ensureIndexes(template, CollectionName.INVITATIONS, List.of(
            IndexSpec.standard("idx_invitations_email", doc("email", 1), false, false),
            IndexSpec.standard("uidx_invitations_token", doc("token", 1), true, false),
            IndexSpec.ttl("idx_invitations_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, CollectionName.TICKET_VERIFICATIONS, List.of(
            IndexSpec.ttl("idx_ticket_verifications_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, CollectionName.TICKETS, List.of(
            IndexSpec.standard("idx_tickets_status_created", doc("status", 1).append("created", -1), false, false),
            IndexSpec.standard("idx_tickets_created", doc("created", -1), false, false),
            IndexSpec.standard("idx_tickets_updatedAt", doc("updatedAt", -1), false, false),
            IndexSpec.standard("idx_tickets_type_created", doc("type", 1).append("created", -1), false, false),
            IndexSpec.standard("idx_tickets_creatorUuid_created", doc("creatorUuid", 1).append("created", -1), false, false),
            IndexSpec.standard("idx_tickets_reportedPlayerUuid_created", doc("reportedPlayerUuid", 1).append("created", -1), false, false),
            IndexSpec.standard("idx_tickets_locked_created", doc("locked", 1).append("created", -1), false, false),
            IndexSpec.standard("idx_tickets_assignedTo_updatedAt", doc("assignedTo", 1).append("updatedAt", -1), false, false),
            IndexSpec.standard("idx_tickets_creatorName_created", doc("creatorName", 1).append("created", -1), false, false),
            IndexSpec.standard("idx_tickets_replies_name_created", doc("replies.name", 1).append("replies.created", -1), false, false),
            IndexSpec.standard("idx_tickets_tags", doc("tags", 1), false, false),
            IndexSpec.standard("idx_tickets_replayUrl", doc("replayUrl", 1), false, true)
        ));

        ensureIndexes(template, CollectionName.REPLAYS, List.of(
            IndexSpec.standard("idx_replays_targetUuid_createdAt", doc("targetUuid", 1).append("createdAt", -1), false, true),
            IndexSpec.standard("idx_replays_status_createdAt", doc("status", 1).append("createdAt", 1), false, false)
        ));

        ensureIndexes(template, CollectionName.STORAGE_FILES, List.of(
            IndexSpec.standard("uidx_storage_files_key", doc("key", 1), true, false),
            IndexSpec.standard("idx_storage_files_key_createdAt", doc("key", 1).append("createdAt", -1), false, false)
        ));

        ensureIndexes(template, CollectionName.KNOWLEDGEBASE_CATEGORIES, List.of(
            IndexSpec.standard("uidx_knowledgebase_categories_slug", doc("slug", 1), true, false),
            IndexSpec.standard("idx_knowledgebase_categories_name", doc("name", 1), false, false),
            IndexSpec.standard("idx_knowledgebase_categories_ordinal", doc("ordinal", 1), false, false),
            IndexSpec.standard("idx_knowledgebase_categories_isVisible_ordinal", doc("isVisible", 1).append("ordinal", 1), false, false)
        ));

        ensureIndexes(template, CollectionName.KNOWLEDGEBASE_ARTICLES, List.of(
            IndexSpec.standard("uidx_knowledgebase_articles_slug", doc("slug", 1), true, false),
            IndexSpec.standard("idx_knowledgebase_articles_categoryId_ordinal", doc("categoryId", 1).append("ordinal", 1), false, false),
            IndexSpec.standard("idx_knowledgebase_articles_isVisible_categoryId_ordinal", doc("isVisible", 1).append("categoryId", 1).append("ordinal", 1),
                false, false)
        ));

        ensureIndexes(template, CollectionName.WEBAUTHN_CREDENTIALS, List.of(
            IndexSpec.standard("idx_webauthn_credentials_email", doc("email", 1), false, false),
            IndexSpec.standard("uidx_webauthn_credentials_credentialId", doc("credentialId", 1), true, false),
            IndexSpec.standard("idx_webauthn_credentials_userHandle", doc("userHandle", 1), false, false)
        ));

        ensureIndexes(template, CollectionName.WEBAUTHN_CHALLENGES, List.of(
            IndexSpec.ttl("idx_webauthn_challenges_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, CollectionName.HOMEPAGE_CARDS, List.of(
            IndexSpec.standard("idx_homepage_cards_ordinal", doc("ordinal", 1), false, false),
            IndexSpec.standard("idx_homepage_cards_isEnabled_ordinal", doc("isEnabled", 1).append("ordinal", 1), false, false),
            IndexSpec.standard("idx_homepage_cards_categoryId", doc("categoryId", 1), false, true)
        ));

        ensureIndexes(template, CollectionName.SESSIONS, List.of(
            IndexSpec.standard("idx_sessions_email", doc("email", 1), false, false),
            IndexSpec.ttl("idx_sessions_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, CollectionName.AUTH_CODES, List.of(
            IndexSpec.ttl("idx_auth_codes_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, CollectionName.SYSTEM_LOGS, List.of(
            IndexSpec.standard("idx_system_logs_timestamp", doc("timestamp", -1), false, false),
            IndexSpec.standard("idx_system_logs_level_timestamp", doc("level", 1).append("timestamp", -1), false, false),
            IndexSpec.standard("idx_system_logs_source_timestamp", doc("source", 1).append("timestamp", -1), false, false)
        ));

        ensureIndexes(template, CollectionName.SECURITY_EVENTS, List.of(
            IndexSpec.standard("idx_security_events_timestamp", doc("timestamp", -1), false, false),
            IndexSpec.standard("idx_security_events_severity_timestamp", doc("severity", 1).append("timestamp", -1), false, false)
        ));

        ensureIndexes(template, CollectionName.CHAT_LOGS, List.of(
            IndexSpec.standard("idx_chat_logs_uuid_timestamp", doc("uuid", 1).append("timestamp", -1), false, false)
        ));

        ensureIndexes(template, CollectionName.COMMAND_LOGS, List.of(
            IndexSpec.standard("idx_command_logs_uuid_timestamp", doc("uuid", 1).append("timestamp", -1), false, false)
        ));

        ensureIndexes(template, CollectionName.LOGS, List.of(
            IndexSpec.standard("idx_logs_created_desc", doc("created", -1), false, false)
        ));

        ensureIndexes(template, CollectionName.MIGRATIONS, List.of(
            IndexSpec.standard("idx_migrations_status_startedAt", doc("status", 1).append("startedAt", -1), false, false)
        ));
    }

    private void ensureIndexes(MongoTemplate template, String collectionName, List<IndexSpec> specs) {
        IndexOperations indexOps = template.indexOps(collectionName);
        List<IndexInfo> existingIndexes = indexOps.getIndexInfo();
        for (IndexSpec spec : specs) {
            if (hasEquivalentIndex(existingIndexes, spec)) {
                continue;
            }
            try {
                boolean nameCollision = existingIndexes.stream()
                    .anyMatch(i -> spec.name().equals(i.getName()));
                if (nameCollision) {
                    log.warn("Index spec drift on collection={} index={}: existing definition differs"
                        + " from source spec; dropping and recreating", collectionName, spec.name());
                    try {
                        indexOps.dropIndex(spec.name());
                    } catch (Exception e) {
                        log.error("Failed to drop conflicting index name={} on collection={};"
                            + " new spec NOT applied", spec.name(), collectionName, e);
                        continue;
                    }
                }
                createIndex(indexOps, spec);
            } catch (Exception e) {
                log.error("Failed to create index name={} on collection={}; continuing",
                    spec.name(), collectionName, e);
            }
        }
    }

    private void createIndex(IndexOperations indexOps, IndexSpec spec) {
        Index index = new Index().named(spec.name());

        for (Map.Entry<String, Object> entry : spec.keys().entrySet()) {
            Sort.Direction direction = directionFrom(entry.getValue());
            index.on(entry.getKey(), direction);
        }

        if (spec.unique()) {
            index.unique();
        }
        if (spec.sparse()) {
            index.sparse();
        }
        if (spec.ttlSeconds() != null) {
            index.expire(Duration.ofSeconds(spec.ttlSeconds()));
        }
        if (spec.partialFilter() != null) {
            index.partial(PartialIndexFilter.of(spec.partialFilter()));
        }
        if (spec.collation() != null) {
            index.collation(spec.collation());
        }

        indexOps.createIndex(index);
    }

    private Sort.Direction directionFrom(Object value) {
        if (value instanceof Number number) {
            return number.intValue() < 0 ? Sort.Direction.DESC : Sort.Direction.ASC;
        }
        throw new ValidationException("Unsupported index direction value: " + value);
    }

    private boolean hasEquivalentIndex(List<IndexInfo> existingIndexes, IndexSpec spec) {
        List<IndexField> expectedFields = fieldsFor(spec.keys());
        for (IndexInfo existingIndex : existingIndexes) {
            if (!existingIndex.getIndexFields().equals(expectedFields)) {
                continue;
            }
            if (existingIndex.isUnique() != spec.unique()) {
                continue;
            }
            if (existingIndex.isSparse() != spec.sparse()) {
                continue;
            }

            long existingTtlSeconds = existingIndex.getExpireAfter().map(Duration::getSeconds).orElse(-1L);
            long expectedTtlSeconds = spec.ttlSeconds() == null ? -1L : spec.ttlSeconds();
            if (existingTtlSeconds != expectedTtlSeconds) {
                continue;
            }

            if (!hasEquivalentCollation(existingIndex, spec)) {
                continue;
            }

            String existingPartialJson = existingIndex.getPartialFilterExpression();
            Document specPartial = spec.partialFilter();
            if (existingPartialJson == null && specPartial == null) {
                return true;
            }
            if (existingPartialJson == null || specPartial == null) {
                continue;
            }
            if (!canonicalPartialFilter(Document.parse(existingPartialJson))
                    .equals(canonicalPartialFilter(specPartial))) {
                continue;
            }

            return true;
        }
        return false;
    }

    private boolean hasEquivalentCollation(IndexInfo existingIndex, IndexSpec spec) {
        Document existingCollation = existingIndex.getCollation().orElse(null);
        if (spec.collation() == null) {
            return existingCollation == null;
        }
        if (existingCollation == null) {
            return false;
        }
        Document specCollation = spec.collation().toDocument();
        for (Map.Entry<String, Object> entry : specCollation.entrySet()) {
            if (!collationValueEquals(entry.getValue(), existingCollation.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private boolean collationValueEquals(Object specValue, Object existingValue) {
        if (specValue instanceof Number specNumber && existingValue instanceof Number existingNumber) {
            return specNumber.doubleValue() == existingNumber.doubleValue();
        }
        return Objects.equals(specValue, existingValue);
    }

    private Document canonicalPartialFilter(Document filter) {
        Document canonical = new Document();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            canonical.put(entry.getKey(), canonicalFilterValue(entry.getKey(), entry.getValue()));
        }
        return canonical;
    }

    private Object canonicalFilterValue(String key, Object value) {
        if (TYPE_OPERATOR.equals(key)) {
            return canonicalBsonType(value);
        }
        if (value instanceof Document nested) {
            return canonicalPartialFilter(nested);
        }
        if (value instanceof List<?> elements) {
            List<Object> canonical = new ArrayList<>(elements.size());
            for (Object element : elements) {
                canonical.add(element instanceof Document nested ? canonicalPartialFilter(nested) : element);
            }
            return canonical;
        }
        return value;
    }

    private Object canonicalBsonType(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String alias) {
            Integer code = BSON_TYPE_CODES.get(alias);
            return code != null ? code : alias;
        }
        if (value instanceof List<?> aliases) {
            List<Object> canonical = new ArrayList<>(aliases.size());
            for (Object alias : aliases) {
                canonical.add(canonicalBsonType(alias));
            }
            return canonical;
        }
        return value;
    }

    private List<IndexField> fieldsFor(Document keys) {
        List<IndexField> fields = new ArrayList<>(keys.size());
        for (Map.Entry<String, Object> entry : keys.entrySet()) {
            fields.add(IndexField.create(entry.getKey(), directionFrom(entry.getValue())));
        }
        return fields;
    }

    private Document doc(String field, int direction) {
        return new Document(field, direction);
    }

    private record IndexSpec(
        String name,
        Document keys,
        boolean unique,
        boolean sparse,
        Long ttlSeconds,
        Document partialFilter,
        Collation collation
    ) {
        static IndexSpec standard(String name, Document keys, boolean unique, boolean sparse) {
            return new IndexSpec(name, keys, unique, sparse, null, null, null);
        }

        static IndexSpec ttl(String name, Document keys, long ttlSeconds) {
            return new IndexSpec(name, keys, false, false, ttlSeconds, null, null);
        }

        static IndexSpec partialUnique(String name, Document keys, Document partialFilter) {
            return new IndexSpec(name, keys, true, false, null, partialFilter, null);
        }

        static IndexSpec collated(String name, Document keys, Collation collation) {
            return new IndexSpec(name, keys, false, false, null, null, collation);
        }
    }
}
