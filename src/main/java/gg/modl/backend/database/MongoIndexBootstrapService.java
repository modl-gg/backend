package gg.modl.backend.database;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class MongoIndexBootstrapService {
    public void createIndexes(MongoTemplate template) {
        ensureIndexes(template, CollectionName.SETTINGS, List.of(
                IndexSpec.standard("uidx_settings_type", doc("type", 1), true, false)
        ));

        ensureIndexes(template, CollectionName.PLAYERS, List.of(
                IndexSpec.standard("uidx_players_minecraftUuid", doc("minecraftUuid", 1), true, true),
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
                )
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
                IndexSpec.standard("idx_tickets_tags", doc("tags", 1), false, false)
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
                IndexSpec.standard("idx_knowledgebase_articles_isVisible_categoryId_ordinal", doc("isVisible", 1).append("categoryId", 1).append("ordinal", 1), false, false)
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
    }

    private void ensureIndexes(MongoTemplate template, String collectionName, List<IndexSpec> specs) {
        IndexOperations indexOps = template.indexOps(collectionName);
        for (IndexSpec spec : specs) {
            createIndex(indexOps, spec);
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

        indexOps.createIndex(index);
    }

    private Sort.Direction directionFrom(Object value) {
        if (value instanceof Number number) {
            return number.intValue() < 0 ? Sort.Direction.DESC : Sort.Direction.ASC;
        }
        throw new IllegalArgumentException("Unsupported index direction value: " + value);
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
    }
}
