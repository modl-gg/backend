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
        ensureIndexes(template, databaseName, CollectionName.PLAYERS, List.of(
                IndexSpec.standard("uidx_players_minecraftUuid", doc("minecraftUuid", 1), true, true)
        ));

        ensureIndexes(template, databaseName, CollectionName.STAFF, List.of(
                IndexSpec.standard("uidx_staff_email", doc("email", 1), true, false),
                IndexSpec.standard("uidx_staff_username", doc("username", 1), true, false),
                IndexSpec.standard("sidx_staff_assignedMinecraftUuid", doc("assignedMinecraftUuid", 1), false, true)
        ));

        ensureIndexes(template, databaseName, CollectionName.STAFF_ROLES, List.of(
                IndexSpec.standard("uidx_staff_roles_id", doc("id", 1), true, false)
        ));

        ensureIndexes(template, databaseName, CollectionName.INVITATIONS, List.of(
                IndexSpec.standard("idx_invitations_email", doc("email", 1), false, false),
                IndexSpec.standard("uidx_invitations_token", doc("token", 1), true, false)
        ));

        ensureIndexes(template, databaseName, CollectionName.TICKET_VERIFICATIONS, List.of(
                IndexSpec.ttl("idx_ticket_verifications_expiresAt_ttl", doc("expiresAt", 1), 0)
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
            if (existingIndex.ttl() && existingIndex.singleField(LEGACY_EXPIRES_FIELD) && existingIndex.name() != null) {
                indexOps.dropIndex(existingIndex.name());
                log.info("Dropped legacy TTL index {} on {}.{}", existingIndex.name(), databaseName, collectionName);
            }

            if (CollectionName.MODL_SERVERS.equals(collectionName)
                    && existingIndex.name() != null
                    && existingIndex.singleFieldIn(LEGACY_SERVER_INDEX_FIELDS)) {
                indexOps.dropIndex(existingIndex.name());
                log.info("Dropped legacy server index {} on {}.{}", existingIndex.name(), databaseName, collectionName);
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

            ensureIndex(indexOps, spec);
            log.info("Ensured index {} on {}.{}", spec.name(), databaseName, collectionName);
        }
    }

    private void ensureIndex(IndexOperations indexOps, IndexSpec spec) {
        String field = spec.firstFieldName();
        Index index = new Index()
                .on(field, Sort.Direction.ASC)
                .named(spec.name());

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

        String firstFieldName() {
            return keys.keySet().iterator().next();
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
