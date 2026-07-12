package gg.modl.backend.database;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.AdminUserFields;
import gg.modl.backend.database.mongo.fields.AuthCodeFields;
import gg.modl.backend.database.mongo.fields.AuthSessionDataFields;
import gg.modl.backend.database.mongo.fields.BetaAuditFields;
import gg.modl.backend.database.mongo.fields.ChatLogFields;
import gg.modl.backend.database.mongo.fields.CommandLogFields;
import gg.modl.backend.database.mongo.fields.EvidenceUploadTokenDocumentFields;
import gg.modl.backend.database.mongo.fields.HomepageCardFields;
import gg.modl.backend.database.mongo.fields.InvitationFields;
import gg.modl.backend.database.mongo.fields.KnowledgebaseArticleFields;
import gg.modl.backend.database.mongo.fields.KnowledgebaseCategoryFields;
import gg.modl.backend.database.mongo.fields.MetricSnapshotFields;
import gg.modl.backend.database.mongo.fields.MigrationStatusFields;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.ReplayDocumentFields;
import gg.modl.backend.database.mongo.fields.ReplayLiteDailyQuotaDocumentFields;
import gg.modl.backend.database.mongo.fields.ReplayLiteDocumentFields;
import gg.modl.backend.database.mongo.fields.SecurityEventFields;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.database.mongo.fields.ServerInstanceSnapshotFields;
import gg.modl.backend.database.mongo.fields.ServerLogFields;
import gg.modl.backend.database.mongo.fields.SettingsFields;
import gg.modl.backend.database.mongo.fields.StaffFields;
import gg.modl.backend.database.mongo.fields.StaffRoleFields;
import gg.modl.backend.database.mongo.fields.StorageFileDocumentFields;
import gg.modl.backend.database.mongo.fields.SystemAlertFields;
import gg.modl.backend.database.mongo.fields.SystemLogFields;
import gg.modl.backend.database.mongo.fields.TicketFields;
import gg.modl.backend.database.mongo.fields.TicketVerificationFields;
import gg.modl.backend.database.mongo.fields.TrainingSegmentDocumentFields;
import gg.modl.backend.database.mongo.fields.WebAuthnChallengeFields;
import gg.modl.backend.database.mongo.fields.WebAuthnCredentialFields;
import gg.modl.backend.infrastructure.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MongoIndexReconciler {
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

    private final TenantMongoAccess tenantMongoAccess;

    @PostConstruct
    public void initGlobalIndexes() {
        try {
            createGlobalIndexes(tenantMongoAccess.global());
        } catch (Exception e) {
            log.error("Failed to create global database indexes", e);
        }
        try {
            createTrainingDataIndexes(tenantMongoAccess.forDatabase(CollectionName.TRAINING_DATABASE));
        } catch (Exception e) {
            log.error("Failed to create training data indexes", e);
        }
    }

    private void createTrainingDataIndexes(MongoTemplate template) {
        ensureIndexes(template, CollectionName.TRAINING_SEGMENTS, List.of(
            IndexSpec.standard(
                "idx_training_segments_serverDatabaseName_replayId",
                doc(TrainingSegmentDocumentFields.SERVER_DATABASE_NAME, 1).append(TrainingSegmentDocumentFields.REPLAY_ID, 1),
                false,
                false
            )
        ));
    }

    private void createGlobalIndexes(MongoTemplate template) {
        ensureIndexes(template, CollectionName.MODL_SERVERS, List.of(
            IndexSpec.standard("uidx_servers_serverName", doc(ServerFields.SERVER_NAME, 1), true, false),
            IndexSpec.standard("uidx_servers_customDomain", doc(ServerFields.CUSTOM_DOMAIN, 1), true, false),
            IndexSpec.standard("uidx_servers_adminEmail", doc(ServerFields.ADMIN_EMAIL, 1), true, false),
            IndexSpec.standard("idx_servers_emailVerified", doc(ServerFields.EMAIL_VERIFIED, 1), false, false),
            IndexSpec.standard("uidx_servers_emailVerificationToken", doc(ServerFields.EMAIL_VERIFICATION_TOKEN, 1), true, true),
            IndexSpec.standard("idx_servers_provisioningStatus", doc(ServerFields.PROVISIONING_STATUS, 1), false, false),
            IndexSpec.standard(
                "idx_servers_registration_cleanup",
                doc(ServerFields.EMAIL_VERIFIED, 1)
                    .append(ServerFields.PROVISIONING_STATUS, 1)
                    .append(ServerFields.CREATED_AT, 1)
                    .append(ServerFields.EMAIL_VERIFICATION_TOKEN, 1),
                false,
                true
            ),
            IndexSpec.standard("uidx_servers_provisioningSignInToken", doc(ServerFields.PROVISIONING_SIGN_IN_TOKEN, 1), true, true),
            IndexSpec.standard("uidx_servers_stripeCustomerId", doc(ServerFields.STRIPE_CUSTOMER_ID, 1), true, true),
            IndexSpec.standard("uidx_servers_stripeSubscriptionId", doc(ServerFields.STRIPE_SUBSCRIPTION_ID, 1), true, true),
            IndexSpec.standard("uidx_servers_customDomainOverride", doc(ServerFields.CUSTOM_DOMAIN_OVERRIDE, 1), true, true),
            IndexSpec.standard("uidx_servers_customDomainCloudflareId", doc(ServerFields.CUSTOM_DOMAIN_CLOUDFLARE_ID, 1), true, true),
            IndexSpec.standard("uidx_servers_cliSetupToken", doc(ServerFields.CLI_SETUP_TOKEN, 1), true, true),
            IndexSpec.standard("uidx_servers_apiKey", doc(ServerFields.API_KEY, 1), true, true),
            IndexSpec.standard("idx_servers_userCount", doc(ServerFields.USER_COUNT, 1), false, false),
            IndexSpec.standard("idx_servers_ticketCount", doc(ServerFields.TICKET_COUNT, 1), false, false),
            IndexSpec.standard("idx_servers_lastStatsUpdatedAt", doc(ServerFields.LAST_STATS_UPDATED_AT, 1), false, false),
            IndexSpec.standard("idx_servers_createdAt", doc(ServerFields.CREATED_AT, 1), false, false),
            IndexSpec.standard("idx_servers_lastActivityAt", doc(ServerFields.LAST_ACTIVITY_AT, -1), false, true)
        ));

        dropSupersededIndexes(template, CollectionName.METRIC_SNAPSHOTS, List.of("idx_metric_snapshots_date"));
        ensureIndexes(template, CollectionName.METRIC_SNAPSHOTS, List.of(
            IndexSpec.standard("uidx_metric_snapshots_date", doc(MetricSnapshotFields.DATE, 1), true, false)
        ));

        ensureIndexes(template, CollectionName.REPLAY_LITE_REPLAYS, List.of(
            IndexSpec.standard("uidx_replay_lite_objectKey", doc(ReplayLiteDocumentFields.OBJECT_KEY, 1), true, false),
            IndexSpec.standard("idx_replay_lite_expiresAt", doc(ReplayLiteDocumentFields.EXPIRES_AT, 1), false, true),
            IndexSpec.standard(
                "idx_replay_lite_server_status_confirmedAt",
                doc(ReplayLiteDocumentFields.PLUGIN_SERVER_UUID, 1)
                    .append(ReplayLiteDocumentFields.STATUS, 1)
                    .append(ReplayLiteDocumentFields.CONFIRMED_AT, 1),
                false,
                false
            ),
            IndexSpec.standard(
                "idx_replay_lite_server_status_createdAt",
                doc(ReplayLiteDocumentFields.PLUGIN_SERVER_UUID, 1)
                    .append(ReplayLiteDocumentFields.STATUS, 1)
                    .append(ReplayLiteDocumentFields.CREATED_AT, 1),
                false,
                false
            ),
            IndexSpec.standard("idx_replay_lite_status_createdAt",
                doc(ReplayLiteDocumentFields.STATUS, 1).append(ReplayLiteDocumentFields.CREATED_AT, 1).append(ReplayLiteDocumentFields.ID, 1),
                false, false),
            IndexSpec.standard("idx_replay_lite_status_expiresAt",
                doc(ReplayLiteDocumentFields.STATUS, 1).append(ReplayLiteDocumentFields.EXPIRES_AT, 1).append(ReplayLiteDocumentFields.ID, 1),
                false, false)
        ));

        ensureIndexes(template, CollectionName.REPLAY_LITE_DAILY_QUOTAS, List.of(
            IndexSpec.standard(
                "uidx_replay_lite_daily_quotas_server_day",
                doc(ReplayLiteDailyQuotaDocumentFields.PLUGIN_SERVER_UUID, 1).append(ReplayLiteDailyQuotaDocumentFields.DAY, 1),
                true,
                false
            ),
            IndexSpec.ttl("idx_replay_lite_daily_quotas_expiresAt_ttl", doc(ReplayLiteDailyQuotaDocumentFields.EXPIRES_AT, 1), 0)
        ));

        ensureIndexes(template, CollectionName.ADMIN_USERS, List.of(
            IndexSpec.standard("uidx_admin_users_email", doc(AdminUserFields.EMAIL, 1), true, false)
        ));

        ensureIndexes(template, CollectionName.SYSTEM_ALERTS, List.of(
            IndexSpec.standard("idx_system_alerts_expiresAt", doc(SystemAlertFields.EXPIRES_AT, 1), false, false),
            IndexSpec.standard("idx_system_alerts_createdAt", doc(SystemAlertFields.CREATED_AT, -1), false, false),
            IndexSpec.standard("idx_system_alerts_audience_expiresAt", doc(SystemAlertFields.AUDIENCE, 1).append(SystemAlertFields.EXPIRES_AT, 1), false, false)
        ));

        ensureIndexes(template, CollectionName.SERVER_INSTANCE_SNAPSHOTS, List.of(
            IndexSpec.standard("uidx_server_instance_snapshots_date", doc(ServerInstanceSnapshotFields.DATE, 1), true, false)
        ));

        ensureIndexes(template, CollectionName.EVIDENCE_UPLOAD_TOKENS, List.of(
            IndexSpec.ttl("idx_evidence_upload_tokens_expiresAt_ttl", doc(EvidenceUploadTokenDocumentFields.EXPIRES_AT, 1), 0)
        ));

        ensureIndexes(template, CollectionName.BETA_AUDIT, List.of(
            IndexSpec.standard("idx_beta_audit_serverId_timestamp", doc(BetaAuditFields.SERVER_ID, 1).append(BetaAuditFields.TIMESTAMP, -1), false, false)
        ));
    }

    public void createTenantIndexes(MongoTemplate template) {
        ensureIndexes(template, CollectionName.SETTINGS, List.of(
            IndexSpec.standard("uidx_settings_type", doc(SettingsFields.TYPE, 1), true, false)
        ));

        ensureIndexes(template, CollectionName.PLAYERS, List.of(
            IndexSpec.partialUnique("uidx_players_minecraftUuid", doc(PlayerFields.MINECRAFT_UUID, 1),
                new Document(PlayerFields.MINECRAFT_UUID, new Document(TYPE_OPERATOR, "string"))),
            IndexSpec.standard("idx_players_punishments_issued_desc", doc(PlayerFields.PUNISHMENT_ISSUED, -1), false, false),
            IndexSpec.standard(
                "idx_players_punishments_issuerName_issued_desc",
                doc(PlayerFields.PUNISHMENT_ISSUER_NAME, 1).append(PlayerFields.PUNISHMENT_ISSUED, -1),
                false,
                false
            ),
            IndexSpec.standard(
                "idx_players_punishments_issuerId_issued_desc",
                doc(PlayerFields.PUNISHMENT_ISSUER_ID, 1).append(PlayerFields.PUNISHMENT_ISSUED, -1),
                false,
                true
            ),
            IndexSpec.standard("idx_players_ipAddresses_ipAddress", doc(PlayerFields.IP_ADDRESS, 1), false, false),
            IndexSpec.standard("idx_players_usernames_username", doc(PlayerFields.USERNAME, 1), false, false),
            IndexSpec.collated("idx_players_usernames_username_ci", doc(PlayerFields.USERNAME, 1),
                Collation.of("en").strength(2)),
            IndexSpec.standard("idx_players_punishments_id", doc(PlayerFields.PUNISHMENT_ID, 1), false, true),
            IndexSpec.standard("idx_players_data_isOnline", doc(PlayerFields.DATA_IS_ONLINE, 1), false, true),
            IndexSpec.standard("idx_players_ipAddresses_firstLogin", doc(PlayerFields.IP_FIRST_LOGIN, -1), false, false)
        ));

        ensureIndexes(template, CollectionName.STAFF, List.of(
            IndexSpec.standard("uidx_staff_email", doc(StaffFields.EMAIL, 1), true, false),
            IndexSpec.standard("uidx_staff_username", doc(StaffFields.USERNAME, 1), true, false),
            IndexSpec.standard("sidx_staff_assignedMinecraftUuid", doc(StaffFields.ASSIGNED_MINECRAFT_UUID, 1), false, true)
        ));

        ensureIndexes(template, CollectionName.STAFF_ROLES, List.of(
            IndexSpec.standard("uidx_staff_roles_name", doc(StaffRoleFields.NAME, 1), true, false),
            IndexSpec.standard("idx_staff_roles_order", doc(StaffRoleFields.ORDER, 1), false, false)
        ));

        ensureIndexes(template, CollectionName.INVITATIONS, List.of(
            IndexSpec.standard("idx_invitations_email", doc(InvitationFields.EMAIL, 1), false, false),
            IndexSpec.standard("uidx_invitations_token", doc(InvitationFields.TOKEN, 1), true, false),
            IndexSpec.ttl("idx_invitations_expiresAt_ttl", doc(InvitationFields.EXPIRES_AT, 1), 0)
        ));

        ensureIndexes(template, CollectionName.TICKET_VERIFICATIONS, List.of(
            IndexSpec.ttl("idx_ticket_verifications_expiresAt_ttl", doc(TicketVerificationFields.EXPIRES_AT, 1), 0)
        ));

        ensureIndexes(template, CollectionName.TICKETS, List.of(
            IndexSpec.standard("idx_tickets_status_created", doc(TicketFields.STATUS, 1).append(TicketFields.CREATED, -1), false, false),
            IndexSpec.standard("idx_tickets_created", doc(TicketFields.CREATED, -1), false, false),
            IndexSpec.standard("idx_tickets_updatedAt", doc(TicketFields.UPDATED_AT, -1), false, false),
            IndexSpec.standard("idx_tickets_type_created", doc(TicketFields.TYPE, 1).append(TicketFields.CREATED, -1), false, false),
            IndexSpec.standard("idx_tickets_creatorUuid_created", doc(TicketFields.CREATOR_UUID, 1).append(TicketFields.CREATED, -1), false, false),
            IndexSpec.standard("idx_tickets_reportedPlayerUuid_created", doc(TicketFields.REPORTED_PLAYER_UUID, 1).append(TicketFields.CREATED, -1), false, false),
            IndexSpec.standard("idx_tickets_locked_created", doc(TicketFields.LOCKED, 1).append(TicketFields.CREATED, -1), false, false),
            IndexSpec.standard("idx_tickets_assignedTo_updatedAt", doc(TicketFields.ASSIGNED_TO, 1).append(TicketFields.UPDATED_AT, -1), false, false),
            IndexSpec.standard("idx_tickets_creatorName_created", doc(TicketFields.CREATOR_NAME, 1).append(TicketFields.CREATED, -1), false, false),
            IndexSpec.standard("idx_tickets_replies_name_created", doc(TicketFields.REPLY_NAME, 1).append(TicketFields.REPLY_CREATED, -1), false, false),
            IndexSpec.standard("idx_tickets_tags", doc(TicketFields.TAGS, 1), false, false),
            IndexSpec.standard("idx_tickets_replayUrl", doc(TicketFields.REPLAY_URL, 1), false, true),
            IndexSpec.standard("idx_tickets_replayId", doc(TicketFields.REPLAY_ID, 1), false, true)
        ));

        ensureIndexes(template, CollectionName.REPLAYS, List.of(
            IndexSpec.standard("idx_replays_targetUuid_createdAt", doc(ReplayDocumentFields.TARGET_UUID, 1).append(ReplayDocumentFields.CREATED_AT, -1), false, true),
            IndexSpec.standard("idx_replays_status_createdAt",
                doc(ReplayDocumentFields.STATUS, 1).append(ReplayDocumentFields.CREATED_AT, 1).append(ReplayDocumentFields.ID, 1), false, false),
            IndexSpec.standard("idx_replays_storageKey", doc(ReplayDocumentFields.STORAGE_KEY, 1), false, false)
        ));

        ensureIndexes(template, CollectionName.STORAGE_FILES, List.of(
            IndexSpec.standard("uidx_storage_files_key", doc(StorageFileDocumentFields.KEY, 1), true, false),
            IndexSpec.standard("idx_storage_files_key_createdAt", doc(StorageFileDocumentFields.KEY, 1).append(StorageFileDocumentFields.CREATED_AT, -1), false, false)
        ));

        ensureIndexes(template, CollectionName.KNOWLEDGEBASE_CATEGORIES, List.of(
            IndexSpec.standard("uidx_knowledgebase_categories_slug", doc(KnowledgebaseCategoryFields.SLUG, 1), true, false),
            IndexSpec.standard("idx_knowledgebase_categories_name", doc(KnowledgebaseCategoryFields.NAME, 1), false, false),
            IndexSpec.standard("idx_knowledgebase_categories_ordinal", doc(KnowledgebaseCategoryFields.ORDINAL, 1), false, false),
            IndexSpec.standard("idx_knowledgebase_categories_isVisible_ordinal", doc(KnowledgebaseCategoryFields.IS_VISIBLE, 1).append(KnowledgebaseCategoryFields.ORDINAL, 1), false, false)
        ));

        ensureIndexes(template, CollectionName.KNOWLEDGEBASE_ARTICLES, List.of(
            IndexSpec.standard("uidx_knowledgebase_articles_slug", doc(KnowledgebaseArticleFields.SLUG, 1), true, false),
            IndexSpec.standard("idx_knowledgebase_articles_categoryId_ordinal", doc(KnowledgebaseArticleFields.CATEGORY_ID, 1).append(KnowledgebaseArticleFields.ORDINAL, 1), false, false),
            IndexSpec.standard("idx_knowledgebase_articles_isVisible_categoryId_ordinal",
                doc(KnowledgebaseArticleFields.IS_VISIBLE, 1).append(KnowledgebaseArticleFields.CATEGORY_ID, 1).append(KnowledgebaseArticleFields.ORDINAL, 1),
                false, false)
        ));

        ensureIndexes(template, CollectionName.WEBAUTHN_CREDENTIALS, List.of(
            IndexSpec.standard("idx_webauthn_credentials_email", doc(WebAuthnCredentialFields.EMAIL, 1), false, false),
            IndexSpec.standard("uidx_webauthn_credentials_credentialId", doc(WebAuthnCredentialFields.CREDENTIAL_ID, 1), true, false),
            IndexSpec.standard("idx_webauthn_credentials_userHandle", doc(WebAuthnCredentialFields.USER_HANDLE, 1), false, false)
        ));

        ensureIndexes(template, CollectionName.WEBAUTHN_CHALLENGES, List.of(
            IndexSpec.ttl("idx_webauthn_challenges_expiresAt_ttl", doc(WebAuthnChallengeFields.EXPIRES_AT, 1), 0)
        ));

        ensureIndexes(template, CollectionName.HOMEPAGE_CARDS, List.of(
            IndexSpec.standard("idx_homepage_cards_ordinal", doc(HomepageCardFields.ORDINAL, 1), false, false),
            IndexSpec.standard("idx_homepage_cards_isEnabled_ordinal", doc(HomepageCardFields.IS_ENABLED, 1).append(HomepageCardFields.ORDINAL, 1), false, false),
            IndexSpec.standard("idx_homepage_cards_categoryId", doc(HomepageCardFields.CATEGORY_ID, 1), false, true)
        ));

        ensureIndexes(template, CollectionName.SESSIONS, List.of(
            IndexSpec.standard("idx_sessions_email", doc(AuthSessionDataFields.EMAIL, 1), false, false),
            IndexSpec.ttl("idx_sessions_expiresAt_ttl", doc(AuthSessionDataFields.EXPIRES_AT, 1), 0)
        ));

        ensureIndexes(template, CollectionName.AUTH_CODES, List.of(
            IndexSpec.ttl("idx_auth_codes_expiresAt_ttl", doc(AuthCodeFields.EXPIRES_AT, 1), 0)
        ));

        ensureIndexes(template, CollectionName.SYSTEM_LOGS, List.of(
            IndexSpec.standard("idx_system_logs_timestamp", doc(SystemLogFields.TIMESTAMP, -1), false, false),
            IndexSpec.standard("idx_system_logs_level_timestamp", doc(SystemLogFields.LEVEL, 1).append(SystemLogFields.TIMESTAMP, -1), false, false),
            IndexSpec.standard("idx_system_logs_source_timestamp", doc(SystemLogFields.SOURCE, 1).append(SystemLogFields.TIMESTAMP, -1), false, false)
        ));

        ensureIndexes(template, CollectionName.SECURITY_EVENTS, List.of(
            IndexSpec.standard("idx_security_events_timestamp", doc(SecurityEventFields.TIMESTAMP, -1), false, false),
            IndexSpec.standard("idx_security_events_severity_timestamp", doc(SecurityEventFields.SEVERITY, 1).append(SecurityEventFields.TIMESTAMP, -1), false, false)
        ));

        ensureIndexes(template, CollectionName.CHAT_LOGS, List.of(
            IndexSpec.standard("idx_chat_logs_uuid_timestamp", doc(ChatLogFields.UUID, 1).append(ChatLogFields.TIMESTAMP, -1), false, false)
        ));

        ensureIndexes(template, CollectionName.COMMAND_LOGS, List.of(
            IndexSpec.standard("idx_command_logs_uuid_timestamp", doc(CommandLogFields.UUID, 1).append(CommandLogFields.TIMESTAMP, -1), false, false)
        ));

        ensureIndexes(template, CollectionName.LOGS, List.of(
            IndexSpec.standard("idx_logs_created_desc", doc(ServerLogFields.CREATED, -1), false, false)
        ));

        ensureIndexes(template, CollectionName.MIGRATIONS, List.of(
            IndexSpec.standard("idx_migrations_status_startedAt", doc(MigrationStatusFields.STATUS, 1).append(MigrationStatusFields.STARTED_AT, -1), false, false)
        ));
    }

    private void dropSupersededIndexes(MongoTemplate template, String collectionName, List<String> legacyIndexNames) {
        IndexOperations indexOps = template.indexOps(collectionName);
        List<String> existingNames = indexOps.getIndexInfo().stream().map(IndexInfo::getName).toList();
        for (String legacyName : legacyIndexNames) {
            if (!existingNames.contains(legacyName)) {
                continue;
            }
            try {
                indexOps.dropIndex(legacyName);
                log.info("Dropped superseded index name={} on collection={}", legacyName, collectionName);
            } catch (Exception e) {
                log.warn("Failed to drop superseded index name={} on collection={}", legacyName, collectionName, e);
            }
        }
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
