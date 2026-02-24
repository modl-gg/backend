package gg.modl.backend.database;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class MongoIndexBootstrapService {
    private static final String GLOBAL_DATABASE_NAME = "modl";
    private static final String LEGACY_EXPIRES_FIELD = "expires";
    private static final List<String> LEGACY_SERVER_INDEX_FIELDS = List.of(
            "stripe_customer_id",
            "stripe_subscription_id",
            "customDomain_override",
            "customDomain_cloudflareId"
    );
    private static final String LEGACY_STAFF_ROLE_ID_FIELD = "id";
    private static final int NAMESPACE_NOT_FOUND_ERROR_CODE = 26;

    public void ensureIndexesForDatabase(String databaseName, MongoTemplate template) {
        if (GLOBAL_DATABASE_NAME.equals(databaseName)) {
            ensureGlobalIndexes(databaseName, template);
            return;
        }
        ensureTenantIndexes(databaseName, template);
    }

    private void ensureGlobalIndexes(String databaseName, MongoTemplate template) {
        ensureIndexes(template, databaseName, CollectionName.SESSIONS, List.of(
                IndexSpec.standard("idx_sessions_email", doc("email", 1), false, false),
                IndexSpec.ttl("idx_sessions_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, databaseName, CollectionName.AUTH_CODES, List.of(
                IndexSpec.standard("uidx_auth_codes_email", doc("email", 1), true, false),
                IndexSpec.ttl("idx_auth_codes_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, databaseName, CollectionName.MODL_SERVERS, List.of(
                IndexSpec.standard("uidx_servers_serverName", doc("serverName", 1), true, false),
                IndexSpec.standard("uidx_servers_customDomain", doc("customDomain", 1), true, false),
                IndexSpec.standard("uidx_servers_adminEmail", doc("adminEmail", 1), true, false),
                IndexSpec.standard("idx_servers_emailVerified", doc("emailVerified", 1), false, false),
                IndexSpec.standard("uidx_servers_emailVerificationToken", doc("emailVerificationToken", 1), true, true),
                IndexSpec.standard("idx_servers_provisioningStatus", doc("provisioningStatus", 1), false, false),
                IndexSpec.standard("uidx_servers_provisioningSignInToken", doc("provisioningSignInToken", 1), true, true),
                IndexSpec.standard("uidx_servers_stripeCustomerId", doc("stripeCustomerId", 1), true, true),
                IndexSpec.standard("uidx_servers_stripeSubscriptionId", doc("stripeSubscriptionId", 1), true, true),
                IndexSpec.standard("uidx_servers_customDomainOverride", doc("customDomainOverride", 1), true, true),
                IndexSpec.standard("uidx_servers_customDomainCloudflareId", doc("customDomainCloudflareId", 1), true, true),
                IndexSpec.standard("uidx_servers_apiKey", doc("apiKey", 1), true, true),
                IndexSpec.standard("idx_servers_createdAt", doc("createdAt", 1), false, false)
        ));
    }

    private void ensureTenantIndexes(String databaseName, MongoTemplate template) {
        ensureIndexes(template, databaseName, CollectionName.SETTINGS, List.of(
                IndexSpec.standard("uidx_settings_type", doc("type", 1), true, false)
        ));

        ensureIndexes(template, databaseName, CollectionName.PLAYERS, List.of(
                IndexSpec.standard("uidx_players_minecraftUuid", doc("minecraftUuid", 1), true, true),
                IndexSpec.standard("idx_players_punishments_issued_desc", doc("punishments.issued", -1), false, false),
                IndexSpec.standard(
                        "idx_players_punishments_issuerName_issued_desc",
                        doc("punishments.issuerName", 1).append("punishments.issued", -1),
                        false,
                        false
                )
        ));

        ensureIndexes(template, databaseName, CollectionName.STAFF, List.of(
                IndexSpec.standard("uidx_staff_email", doc("email", 1), true, false),
                IndexSpec.standard("uidx_staff_username", doc("username", 1), true, false),
                IndexSpec.standard("sidx_staff_assignedMinecraftUuid", doc("assignedMinecraftUuid", 1), false, true)
        ));

        ensureIndexes(template, databaseName, CollectionName.STAFF_ROLES, List.of(
                IndexSpec.standard("uidx_staff_roles_name", doc("name", 1), true, false),
                IndexSpec.standard("idx_staff_roles_order", doc("order", 1), false, false)
        ));

        ensureIndexes(template, databaseName, CollectionName.INVITATIONS, List.of(
                IndexSpec.standard("idx_invitations_email", doc("email", 1), false, false),
                IndexSpec.standard("uidx_invitations_token", doc("token", 1), true, false),
                IndexSpec.ttl("idx_invitations_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, databaseName, CollectionName.TICKET_VERIFICATIONS, List.of(
                IndexSpec.ttl("idx_ticket_verifications_expiresAt_ttl", doc("expiresAt", 1), 0)
        ));

        ensureIndexes(template, databaseName, CollectionName.TICKETS, List.of(
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
                IndexSpec.standard("idx_tickets_tags", doc("tags", 1), false, false)
        ));

        ensureIndexes(template, databaseName, CollectionName.KNOWLEDGEBASE_CATEGORIES, List.of(
                IndexSpec.standard("uidx_knowledgebase_categories_slug", doc("slug", 1), true, false),
                IndexSpec.standard("idx_knowledgebase_categories_name", doc("name", 1), false, false),
                IndexSpec.standard("idx_knowledgebase_categories_ordinal", doc("ordinal", 1), false, false),
                IndexSpec.standard("idx_knowledgebase_categories_isVisible_ordinal", doc("isVisible", 1).append("ordinal", 1), false, false)
        ));

        ensureIndexes(template, databaseName, CollectionName.KNOWLEDGEBASE_ARTICLES, List.of(
                IndexSpec.standard("uidx_knowledgebase_articles_slug", doc("slug", 1), true, false),
                IndexSpec.standard("idx_knowledgebase_articles_categoryId_ordinal", doc("categoryId", 1).append("ordinal", 1), false, false),
                IndexSpec.standard("idx_knowledgebase_articles_isVisible_categoryId_ordinal", doc("isVisible", 1).append("categoryId", 1).append("ordinal", 1), false, false)
        ));

        ensureIndexes(template, databaseName, CollectionName.HOMEPAGE_CARDS, List.of(
                IndexSpec.standard("idx_homepage_cards_ordinal", doc("ordinal", 1), false, false),
                IndexSpec.standard("idx_homepage_cards_isEnabled_ordinal", doc("isEnabled", 1).append("ordinal", 1), false, false),
                IndexSpec.standard("idx_homepage_cards_categoryId", doc("categoryId", 1), false, true)
        ));
    }

    private void ensureIndexes(
            MongoTemplate template,
            String databaseName,
            String collectionName,
            List<IndexSpec> specs
    ) {
        List<IndexInfoSnapshot> existingIndexes = listIndexes(template, collectionName);
        IndexOperations indexOps = template.indexOps(collectionName);

        // Remove known legacy TTL indexes on the old expires field before ensuring canonical indexes.
        for (IndexInfoSnapshot existingIndex : existingIndexes) {
            if (existingIndex.singleField(LEGACY_EXPIRES_FIELD) && existingIndex.name() != null) {
                indexOps.dropIndex(existingIndex.name());
                log.info("Dropped legacy TTL index {} on {}.{}", existingIndex.name(), databaseName, collectionName);
            }

            if (CollectionName.MODL_SERVERS.equals(collectionName)
                    && existingIndex.name() != null
                    && existingIndex.singleFieldIn(LEGACY_SERVER_INDEX_FIELDS)) {
                indexOps.dropIndex(existingIndex.name());
                log.info("Dropped legacy server index {} on {}.{}", existingIndex.name(), databaseName, collectionName);
            }

            if (CollectionName.STAFF_ROLES.equals(collectionName)
                    && existingIndex.name() != null
                    && existingIndex.singleField(LEGACY_STAFF_ROLE_ID_FIELD)) {
                indexOps.dropIndex(existingIndex.name());
                log.info("Dropped legacy staff role index {} on {}.{}", existingIndex.name(), databaseName, collectionName);
            }
        }

        for (IndexSpec spec : specs) {
            existingIndexes = listIndexes(template, collectionName);
            Optional<IndexInfoSnapshot> existingByName = existingIndexes.stream()
                    .filter(existing -> spec.name().equals(existing.name()))
                    .findFirst();

            if (existingByName.isPresent() && matchesSpec(existingByName.get(), spec)) {
                continue;
            }

            String droppedByName = existingByName.map(IndexInfoSnapshot::name).orElse(null);

            // Drop conflicting same-name index.
            existingByName.ifPresent(existing -> {
                indexOps.dropIndex(existing.name());
                log.info("Dropped conflicting index {} on {}.{}", existing.name(), databaseName, collectionName);
            });

            // Drop conflicting same-key index (non-canonical name or mismatched options).
            for (IndexInfoSnapshot existing : existingIndexes) {
                if ("_id_".equals(existing.name())) {
                    continue;
                }
                if (droppedByName != null && droppedByName.equals(existing.name())) {
                    continue;
                }
                if (!existing.keyDocument().equals(spec.keys())) {
                    continue;
                }
                if (spec.name().equals(existing.name()) && matchesSpec(existing, spec)) {
                    continue;
                }
                if (existing.name() != null) {
                    indexOps.dropIndex(existing.name());
                    log.info("Dropped key-conflicting index {} on {}.{}", existing.name(), databaseName, collectionName);
                }
            }

            try {
                ensureIndex(indexOps, spec);
                log.info("Ensured index {} on {}.{}", spec.name(), databaseName, collectionName);
            } catch (RuntimeException exception) {
                if (isDuplicateKeyError(exception)) {
                    log.warn(
                            "Skipped index {} on {}.{} due to duplicate key data: {}",
                            spec.name(),
                            databaseName,
                            collectionName,
                            exception.getMessage()
                    );
                    continue;
                }
                throw exception;
            }
        }
    }

    private void ensureIndex(IndexOperations indexOps, IndexSpec spec) {
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

        indexOps.ensureIndex(index);
    }

    private Sort.Direction directionFrom(Object value) {
        if (value instanceof Number number) {
            return number.intValue() < 0 ? Sort.Direction.DESC : Sort.Direction.ASC;
        }
        throw new IllegalArgumentException("Unsupported index direction value: " + value);
    }

    private boolean matchesSpec(IndexInfoSnapshot existing, IndexSpec spec) {
        return Objects.equals(existing.keyDocument(), spec.keys())
                && existing.unique() == spec.unique()
                && existing.sparse() == spec.sparse()
                && Objects.equals(existing.expireAfterSeconds(), spec.ttlSeconds());
    }

    private List<IndexInfoSnapshot> listIndexes(MongoTemplate template, String collectionName) {
        try {
            MongoCollection<Document> collection = template.getCollection(collectionName);
            List<IndexInfoSnapshot> snapshots = new ArrayList<>();
            for (Document indexDocument : collection.listIndexes()) {
                Document keyDocument = indexDocument.get("key", Document.class);
                if (keyDocument == null) {
                    continue;
                }

                snapshots.add(new IndexInfoSnapshot(
                        indexDocument.getString("name"),
                        keyDocument,
                        Boolean.TRUE.equals(indexDocument.getBoolean("unique")),
                        Boolean.TRUE.equals(indexDocument.getBoolean("sparse")),
                        readExpireAfterSeconds(indexDocument.get("expireAfterSeconds"))
                ));
            }
            return snapshots;
        } catch (MongoCommandException e) {
            if (e.getErrorCode() == NAMESPACE_NOT_FOUND_ERROR_CODE) {
                return List.of();
            }
            throw e;
        }
    }

    private Long readExpireAfterSeconds(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private Document doc(String field, int direction) {
        return new Document(field, direction);
    }

    private boolean isDuplicateKeyError(RuntimeException exception) {
        if (exception instanceof MongoCommandException mongoCommandException
                && mongoCommandException.getErrorCode() == 11000) {
            return true;
        }

        String message = exception.getMessage();
        return message != null && message.contains("E11000");
    }

    private record IndexSpec(
            String name,
            Document keys,
            boolean unique,
            boolean sparse,
            Long ttlSeconds
    ) {
        static IndexSpec standard(String name, Document keys, boolean unique, boolean sparse) {
            return new IndexSpec(name, keys, unique, sparse, null);
        }

        static IndexSpec ttl(String name, Document keys, long ttlSeconds) {
            return new IndexSpec(name, keys, false, false, ttlSeconds);
        }
    }

    private record IndexInfoSnapshot(
            String name,
            Document keyDocument,
            boolean unique,
            boolean sparse,
            Long expireAfterSeconds
    ) {
        boolean ttl() {
            return expireAfterSeconds != null;
        }

        boolean singleField(String fieldName) {
            return keyDocument.size() == 1 && keyDocument.containsKey(fieldName);
        }

        boolean singleFieldIn(List<String> fieldNames) {
            return keyDocument.size() == 1 && fieldNames.stream().anyMatch(keyDocument::containsKey);
        }
    }
}
