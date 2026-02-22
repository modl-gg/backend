# MongoDB Hard Cutover Runbook (Schema + Enum + Index Cleanup)

This runbook migrates production data to the backend's strict cleaned schema and removes legacy compatibility needs from runtime code.

## Scope
- Global DB: `modl`
- Tenant DBs: discovered from `modl.servers.databaseName`
- Collections covered:
  - Global: `servers`, `sessions`, `auth_codes`, `systemprompts`
  - Tenant: `players`, `settings`, `staffs`, `staffroles`, `invitations`, `tickets`, `ticket_verifications`, `knowledgebasecategories`, `knowledgebasearticles`, `homepagecards`

## Safety Strategy
1. Create a full snapshot with `mongodump`.
2. Run preflight audit script in `mongosh`.
3. Run migration script in `DRY_RUN=true`.
4. Review output and duplicate warnings.
5. Re-run migration script with `DRY_RUN=false`.
6. Run post-verification script.
7. Keep snapshot until post-cutover monitoring confirms stability.

## Backup Snapshot (Required)

### PowerShell example
```powershell
$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$archive = "D:\\mongo-backups\\modl-full-$ts.archive.gz"
mongodump --uri "<MONGO_URI>" --archive=$archive --gzip
```

### Restore example
```powershell
mongorestore --uri "<MONGO_URI>" --archive="D:\\mongo-backups\\modl-full-<timestamp>.archive.gz" --gzip --drop
```

## Index Type Policy
- TTL indexes must be single-field ascending (`{ field: 1 }`) and include `expireAfterSeconds`.
- Unique constraints use regular btree indexes.
- Hashed indexes are **not** used here because:
  - TTL cannot be hashed.
  - Unique guarantees are needed on several fields.
  - Some fields are used for sorting/range patterns where btree is preferable.

## Why `_1` index names looked weird
`_1` is a conventional suffix from default Mongo naming (`field_direction`). It is not required. This runbook uses explicit readable names like `uidx_servers_serverName`.

---

## Script 1: Preflight Audit (Read-only)
Run in `mongosh` before migration.

```javascript
(() => {
  const GLOBAL_DB = "modl";
  const globalDb = db.getSiblingDB(GLOBAL_DB);

  function log(section, message, payload) {
    if (payload !== undefined) {
      print(`[${section}] ${message} :: ${JSON.stringify(payload, null, 2)}`);
    } else {
      print(`[${section}] ${message}`);
    }
  }

  function tenantDbNames() {
    const names = globalDb.getCollection("servers")
      .distinct("databaseName", { databaseName: { $type: "string", $nin: ["", GLOBAL_DB] } })
      .filter(Boolean);
    return [...new Set(names)].sort();
  }

  function indexSummary(dbName, collName) {
    const coll = db.getSiblingDB(dbName).getCollection(collName);
    let indexes = [];
    try {
      indexes = coll.getIndexes();
    } catch (e) {
      return { db: dbName, collection: collName, indexes: "collection_missing" };
    }

    return {
      db: dbName,
      collection: collName,
      indexes: indexes.map(i => ({
        name: i.name,
        key: i.key,
        unique: !!i.unique,
        sparse: !!i.sparse,
        expireAfterSeconds: i.expireAfterSeconds ?? null
      }))
    };
  }

  function countGlobalLegacy() {
    return {
      serversOldFieldDocs: globalDb.servers.countDocuments({
        $or: [
          { subscription_status: { $exists: true } },
          { current_period_start: { $exists: true } },
          { current_period_end: { $exists: true } },
          { stripe_customer_id: { $exists: true } },
          { stripe_subscription_id: { $exists: true } },
          { cdn_usage_current_period: { $exists: true } },
          { ai_requests_current_period: { $exists: true } },
          { usage_billing_enabled: { $exists: true } },
          { usage_billing_updated_at: { $exists: true } },
          { max_storage_limit_bytes: { $exists: true } },
          { max_ai_overage_requests: { $exists: true } },
          { customDomain_override: { $exists: true } },
          { customDomain_status: { $exists: true } },
          { customDomain_lastChecked: { $exists: true } },
          { customDomain_error: { $exists: true } },
          { customDomain_cloudflareId: { $exists: true } }
        ]
      }),
      sessionsLegacyExpiresDocs: globalDb.sessions.countDocuments({ expires: { $exists: true } }),
      authCodesLegacyExpiresDocs: globalDb.auth_codes.countDocuments({ expires: { $exists: true } }),
      systemPromptLowercaseStrictnessDocs: globalDb.systemprompts.countDocuments({
        strictnessLevel: { $in: ["lenient", "standard", "strict"] }
      })
    };
  }

  function countTenantLegacy(dbName) {
    const tenantDb = db.getSiblingDB(dbName);

    const playersLegacyCount = tenantDb.players.countDocuments({
      $or: [
        { ipList: { $exists: true } },
        { "usernames.0": { $type: "array" } },
        { "notes.0": { $type: "array" } },
        { "ipAddresses.0": { $type: "array" } },
        { "punishments.0": { $type: "array" } },
        { "notes._id": { $exists: true } },
        { "punishments._id": { $exists: true } },
        { "punishments.type_ordinal": { $exists: true } },
        { "punishments.modifications.0": { $type: "array" } },
        { "punishments.modifications._id": { $exists: true } },
        { "punishments.notes.0": { $type: "array" } },
        { "punishments.notes._id": { $exists: true } },
        { "punishments.data.expires": { $exists: true } },
        { "punishments.data.expiresAt": { $exists: true } }
      ]
    });

    const ticketLegacyExpiresCount = tenantDb.ticket_verifications.countDocuments({ expires: { $exists: true } });

    const generalLegacyTagsCount = tenantDb.settings.countDocuments({
      type: "general",
      $or: [
        { "data.bugReportTags": { $exists: true } },
        { "data.playerReportTags": { $exists: true } },
        { "data.appealTags": { $exists: true } }
      ]
    });

    const generalLegacyLabelsCount = tenantDb.settings.countDocuments({
      type: "general",
      "data.labels": { $exists: true }
    });

    const missingTicketLabelsDocCount = tenantDb.settings.countDocuments({
      type: "ticketLabels"
    }) === 0 ? 1 : 0;

    const aiStrictnessLowercaseCount = tenantDb.settings.countDocuments({
      type: "aiModerationSettings",
      "data.strictnessLevel": { $in: ["lenient", "standard", "strict"] }
    });
    const legacyAiModerationTypeCount = tenantDb.settings.countDocuments({ type: "aiModeration" });

    const invitationsLegacyStatusCount = tenantDb.invitations.countDocuments({ status: { $exists: true } });
    const invitationsLegacyExpiresCount = tenantDb.invitations.countDocuments({ expires: { $exists: true } });
    const invitationsMissingExpiresAtCount = tenantDb.invitations.countDocuments({
      $or: [
        { expiresAt: { $exists: false } },
        { expiresAt: null }
      ]
    });
    const staffRolesLegacyIdFieldCount = tenantDb.staffroles.countDocuments({ id: { $exists: true } });
    const ticketsLegacyCreatorFieldCount = tenantDb.tickets.countDocuments({ creator: { $exists: true } });
    const homepageCardsLegacyFieldCount = tenantDb.homepagecards.countDocuments({
      $or: [
        { icon_color: { $exists: true } },
        { action_type: { $exists: true } },
        { action_url: { $exists: true } },
        { action_button_text: { $exists: true } },
        { category_id: { $exists: true } },
        { background_color: { $exists: true } },
        { is_enabled: { $exists: true } },
        { created_at: { $exists: true } },
        { updated_at: { $exists: true } }
      ]
    });
    const kbCategoriesLegacyFieldCount = tenantDb.knowledgebasecategories.countDocuments({
      $or: [
        { created_at: { $exists: true } },
        { updated_at: { $exists: true } }
      ]
    });
    const kbArticlesLegacyFieldCount = tenantDb.knowledgebasearticles.countDocuments({
      $or: [
        { category_id: { $exists: true } },
        { is_visible: { $exists: true } },
        { created_at: { $exists: true } },
        { updated_at: { $exists: true } }
      ]
    });
    const kbArticlesNullCategoryCount = tenantDb.knowledgebasearticles.countDocuments({
      $or: [
        { categoryId: { $exists: false } },
        { categoryId: null },
        { categoryId: "" }
      ]
    });

    return {
      db: dbName,
      playersLegacyCount,
      ticketLegacyExpiresCount,
      generalLegacyTagsCount,
      generalLegacyLabelsCount,
      missingTicketLabelsDocCount,
      aiStrictnessLowercaseCount,
      legacyAiModerationTypeCount,
      invitationsLegacyStatusCount,
      invitationsLegacyExpiresCount,
      invitationsMissingExpiresAtCount,
      staffRolesLegacyIdFieldCount,
      ticketsLegacyCreatorFieldCount,
      homepageCardsLegacyFieldCount,
      kbCategoriesLegacyFieldCount,
      kbArticlesLegacyFieldCount,
      kbArticlesNullCategoryCount
    };
  }

  const tenants = tenantDbNames();
  log("INFO", `Discovered tenant DB count: ${tenants.length}`);
  log("INFO", "Tenant DB names", tenants);

  log("LEGACY", "Global legacy counts", countGlobalLegacy());
  tenants.forEach(name => log("LEGACY", `Tenant legacy counts for ${name}`, countTenantLegacy(name)));

  const globalCollections = ["servers", "sessions", "auth_codes", "systemprompts"];
  const tenantCollections = ["players", "settings", "staffs", "staffroles", "invitations", "tickets", "ticket_verifications", "knowledgebasecategories", "knowledgebasearticles", "homepagecards"];

  globalCollections.forEach(c => log("INDEX", `Global index summary ${c}`, indexSummary(GLOBAL_DB, c)));
  tenants.forEach(t => {
    tenantCollections.forEach(c => log("INDEX", `Tenant index summary ${t}.${c}`, indexSummary(t, c)));
  });

  const now = new Date();
  log("TTL", "Expired sessions still present (cleanup is async)", globalDb.sessions.countDocuments({ expiresAt: { $lt: now } }));
  log("TTL", "Expired auth codes still present (cleanup is async)", globalDb.auth_codes.countDocuments({ expiresAt: { $lt: now } }));
  tenants.forEach(t => {
    const tenantDb = db.getSiblingDB(t);
    log("TTL", `Expired invitations still present in ${t} (cleanup is async)`, tenantDb.invitations.countDocuments({ expiresAt: { $lt: now } }));
    log("TTL", `Expired ticket verifications still present in ${t} (cleanup is async)`, tenantDb.ticket_verifications.countDocuments({ expiresAt: { $lt: now } }));
  });
})();
```

---

## Script 2: Migration + Canonical Index Rebuild
- Run once with `DRY_RUN=true`.
- Then re-run with `DRY_RUN=false`.

```javascript
(() => {
  const CONFIG = {
    DRY_RUN: true, // Review output first, then set to false for commit mode
    GLOBAL_DB: "modl",
    TARGET_TENANT_DBS: null,
    BATCH_SIZE: 500
  };

  const INDEX_MANIFEST_GLOBAL = {
    sessions: [
      { name: "idx_sessions_email", key: { email: 1 }, options: {} },
      { name: "idx_sessions_expiresAt_ttl", key: { expiresAt: 1 }, options: { expireAfterSeconds: 0 } }
    ],
    auth_codes: [
      { name: "uidx_auth_codes_email", key: { email: 1 }, options: { unique: true } },
      { name: "idx_auth_codes_expiresAt_ttl", key: { expiresAt: 1 }, options: { expireAfterSeconds: 0 } }
    ],
    servers: [
      { name: "uidx_servers_serverName", key: { serverName: 1 }, options: { unique: true } },
      { name: "uidx_servers_customDomain", key: { customDomain: 1 }, options: { unique: true } },
      { name: "uidx_servers_adminEmail", key: { adminEmail: 1 }, options: { unique: true } },
      { name: "idx_servers_emailVerified", key: { emailVerified: 1 }, options: {} },
      { name: "uidx_servers_emailVerificationToken", key: { emailVerificationToken: 1 }, options: { unique: true, sparse: true } },
      { name: "idx_servers_provisioningStatus", key: { provisioningStatus: 1 }, options: {} },
      { name: "uidx_servers_provisioningSignInToken", key: { provisioningSignInToken: 1 }, options: { unique: true, sparse: true } },
      { name: "uidx_servers_stripeCustomerId", key: { stripeCustomerId: 1 }, options: { unique: true, sparse: true } },
      { name: "uidx_servers_stripeSubscriptionId", key: { stripeSubscriptionId: 1 }, options: { unique: true, sparse: true } },
      { name: "uidx_servers_customDomainOverride", key: { customDomainOverride: 1 }, options: { unique: true, sparse: true } },
      { name: "uidx_servers_customDomainCloudflareId", key: { customDomainCloudflareId: 1 }, options: { unique: true, sparse: true } },
      { name: "uidx_servers_apiKey", key: { apiKey: 1 }, options: { unique: true, sparse: true } },
      { name: "idx_servers_createdAt", key: { createdAt: 1 }, options: {} }
    ]
  };

  const INDEX_MANIFEST_TENANT = {
    settings: [
      { name: "uidx_settings_type", key: { type: 1 }, options: { unique: true } }
    ],
    players: [
      { name: "uidx_players_minecraftUuid", key: { minecraftUuid: 1 }, options: { unique: true, sparse: true } }
    ],
    staffs: [
      { name: "uidx_staff_email", key: { email: 1 }, options: { unique: true } },
      { name: "uidx_staff_username", key: { username: 1 }, options: { unique: true } },
      { name: "sidx_staff_assignedMinecraftUuid", key: { assignedMinecraftUuid: 1 }, options: { sparse: true } }
    ],
    staffroles: [
      { name: "uidx_staff_roles_name", key: { name: 1 }, options: { unique: true } },
      { name: "idx_staff_roles_order", key: { order: 1 }, options: {} }
    ],
    invitations: [
      { name: "idx_invitations_email", key: { email: 1 }, options: {} },
      { name: "uidx_invitations_token", key: { token: 1 }, options: { unique: true } },
      { name: "idx_invitations_expiresAt_ttl", key: { expiresAt: 1 }, options: { expireAfterSeconds: 0 } }
    ],
    tickets: [
      { name: "idx_tickets_status_created", key: { status: 1, created: -1 }, options: {} },
      { name: "idx_tickets_created", key: { created: -1 }, options: {} },
      { name: "idx_tickets_updatedAt", key: { updatedAt: -1 }, options: {} },
      { name: "idx_tickets_type_created", key: { type: 1, created: -1 }, options: {} },
      { name: "idx_tickets_creatorUuid_created", key: { creatorUuid: 1, created: -1 }, options: {} },
      { name: "idx_tickets_reportedPlayerUuid_created", key: { reportedPlayerUuid: 1, created: -1 }, options: {} },
      { name: "idx_tickets_locked_created", key: { locked: 1, created: -1 }, options: {} },
      { name: "idx_tickets_tags", key: { tags: 1 }, options: {} }
    ],
    ticket_verifications: [
      { name: "idx_ticket_verifications_expiresAt_ttl", key: { expiresAt: 1 }, options: { expireAfterSeconds: 0 } }
    ],
    knowledgebasecategories: [
      { name: "uidx_knowledgebase_categories_slug", key: { slug: 1 }, options: { unique: true } },
      { name: "idx_knowledgebase_categories_name", key: { name: 1 }, options: {} },
      { name: "idx_knowledgebase_categories_ordinal", key: { ordinal: 1 }, options: {} },
      { name: "idx_knowledgebase_categories_isVisible_ordinal", key: { isVisible: 1, ordinal: 1 }, options: {} }
    ],
    knowledgebasearticles: [
      { name: "uidx_knowledgebase_articles_slug", key: { slug: 1 }, options: { unique: true } },
      { name: "idx_knowledgebase_articles_categoryId_ordinal", key: { categoryId: 1, ordinal: 1 }, options: {} },
      { name: "idx_knowledgebase_articles_isVisible_categoryId_ordinal", key: { isVisible: 1, categoryId: 1, ordinal: 1 }, options: {} }
    ],
    homepagecards: [
      { name: "idx_homepage_cards_ordinal", key: { ordinal: 1 }, options: {} },
      { name: "idx_homepage_cards_isEnabled_ordinal", key: { isEnabled: 1, ordinal: 1 }, options: {} },
      { name: "idx_homepage_cards_categoryId", key: { categoryId: 1 }, options: { sparse: true } }
    ]
  };
  const PLAN_MAP = {
    free: "FREE",
    premium: "PREMIUM"
  };

  const PROVISIONING_MAP = {
    pending: "PENDING",
    in_progress: "IN_PROGRESS",
    inprogress: "IN_PROGRESS",
    provisioning: "IN_PROGRESS",
    completed: "COMPLETED",
    complete: "COMPLETED",
    active: "COMPLETED",
    failed: "FAILED",
    error: "FAILED"
  };

  const SUBSCRIPTION_MAP = {
    active: "ACTIVE",
    canceled: "CANCELED",
    cancelled: "CANCELED",
    past_due: "PAST_DUE",
    pastdue: "PAST_DUE",
    trialing: "TRIALING",
    incomplete: "INCOMPLETE",
    incomplete_expired: "INCOMPLETE_EXPIRED",
    unpaid: "UNPAID",
    paused: "PAUSED",
    inactive: "INACTIVE"
  };

  const DOMAIN_STATUS_MAP = {
    pending: "PENDING",
    active: "ACTIVE",
    verifying: "VERIFYING",
    error: "ERROR",
    not_configured: "PENDING"
  };

  const STRICTNESS_MAP = {
    lenient: "LENIENT",
    standard: "STANDARD",
    strict: "STRICT"
  };

  const globalDb = db.getSiblingDB(CONFIG.GLOBAL_DB);

  function log(section, message, payload) {
    if (payload !== undefined) {
      print(`[${section}] ${message} :: ${JSON.stringify(payload, null, 2)}`);
    } else {
      print(`[${section}] ${message}`);
    }
  }

  function normalizeKey(value) {
    return String(value).trim().toLowerCase().replace(/[\s-]+/g, "_");
  }

  function normalizeEnum(value, map, fallback) {
    if (value === undefined || value === null) return value;
    const k = normalizeKey(value);
    return map[k] || fallback;
  }

  function normalizeHomepageActionType(value) {
    if (value === undefined || value === null || String(value).trim() === "") return "url";
    const normalized = normalizeKey(value);
    if (normalized === "url" || normalized === "link") return "url";
    if (normalized === "category_dropdown" || normalized === "categorydropdown" || normalized === "dropdown" || normalized === "category") {
      return "category_dropdown";
    }
    return "url";
  }

  function normalizeStaffRoleName(value) {
    if (value === undefined || value === null) return "";
    return String(value).trim().replace(/\s+/g, " ");
  }

  function roleNameKey(value) {
    return normalizeStaffRoleName(value).toLowerCase();
  }

  function canonicalDefaultRoleDisplayName(roleId, fallbackName) {
    const id = String(roleId || "");
    if (id === "super-admin") return "Super Admin";
    if (id === "admin") return "Admin";
    if (id === "moderator") return "Moderator";
    if (id === "helper") return "Helper";
    return fallbackName;
  }

  function escapeRegex(value) {
    return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  function toDate(value) {
    if (value === undefined || value === null) return null;
    if (value instanceof Date) return value;
    if (typeof value === "number") return new Date(value);
    if (typeof value === "string") {
      const d = new Date(value);
      if (!isNaN(d.getTime())) return d;
    }
    if (typeof value === "object" && value.$date) {
      const d = new Date(value.$date);
      if (!isNaN(d.getTime())) return d;
    }
    return null;
  }

  function toLong(ms) {
    const normalized = Math.trunc(ms);
    if (typeof NumberLong === "function") {
      return NumberLong(String(normalized));
    }
    return normalized;
  }

  function errorCode(error) {
    if (!error || typeof error !== "object") return null;
    if (typeof error.code === "number") return error.code;
    if (Array.isArray(error.writeErrors) && error.writeErrors.length > 0) {
      const first = error.writeErrors[0];
      if (first && typeof first.code === "number") return first.code;
    }
    return null;
  }

  function errorMessage(error) {
    if (!error) return "";
    if (typeof error.message === "string" && error.message) return error.message;
    return String(error);
  }

  function isDuplicateKeyError(error) {
    const code = errorCode(error);
    const message = errorMessage(error);
    if (code === 11000 || code === 11001 || /E11000|duplicate key/i.test(message)) {
      return true;
    }

    if (Array.isArray(error && error.writeErrors)) {
      return error.writeErrors.some(writeError => {
        const writeCode = writeError && writeError.code;
        const writeMessage = writeError && (writeError.errmsg || writeError.message || "");
        return writeCode === 11000 || /E11000|duplicate key/i.test(String(writeMessage));
      });
    }

    return false;
  }

  function logDuplicateKeySkip(label, error) {
    log("WARN", `${label}: skipped because duplicate key constraint is currently violated`, {
      code: errorCode(error),
      message: errorMessage(error)
    });
  }

  function runStep(label, fn) {
    try {
      fn();
    } catch (error) {
      if (isDuplicateKeyError(error)) {
        logDuplicateKeySkip(label, error);
        return;
      }
      throw error;
    }
  }

  function tenantDbNames() {
    if (Array.isArray(CONFIG.TARGET_TENANT_DBS) && CONFIG.TARGET_TENANT_DBS.length > 0) {
      return [...new Set(CONFIG.TARGET_TENANT_DBS)].sort();
    }

    const names = globalDb.getCollection("servers")
      .distinct("databaseName", { databaseName: { $type: "string", $nin: ["", CONFIG.GLOBAL_DB] } })
      .filter(Boolean);
    return [...new Set(names)].sort();
  }

  function runUpdateMany(collection, filter, update, label) {
    const matched = collection.countDocuments(filter);
    if (matched === 0) {
      log("MIGRATE", `${label}: nothing to update`);
      return;
    }

    if (CONFIG.DRY_RUN) {
      log("DRY_RUN", `${label}: would update ${matched} document(s)`);
      return;
    }

    try {
      const result = collection.updateMany(filter, update);
      log("MIGRATE", `${label}: matched=${result.matchedCount}, modified=${result.modifiedCount}`);
    } catch (error) {
      if (isDuplicateKeyError(error)) {
        logDuplicateKeySkip(label, error);
        return;
      }
      throw error;
    }
  }

  function runDeleteMany(collection, filter, label) {
    const matched = collection.countDocuments(filter);
    if (matched === 0) {
      log("MIGRATE", `${label}: nothing to delete`);
      return;
    }

    if (CONFIG.DRY_RUN) {
      log("DRY_RUN", `${label}: would delete ${matched} document(s)`);
      return;
    }

    const result = collection.deleteMany(filter);
    log("MIGRATE", `${label}: deleted=${result.deletedCount}`);
  }

  function flushBulk(collection, ops, label) {
    if (ops.length === 0) return;

    if (CONFIG.DRY_RUN) {
      log("DRY_RUN", `${label}: would apply ${ops.length} updateOne operation(s)`);
      return;
    }

    try {
      const result = collection.bulkWrite(ops, { ordered: false });
      log("MIGRATE", `${label}: matched=${result.matchedCount}, modified=${result.modifiedCount}`);
    } catch (error) {
      if (isDuplicateKeyError(error)) {
        logDuplicateKeySkip(label, error);
        return;
      }
      throw error;
    }
  }

  function migrateGlobalSessionsAndCodes() {
    runUpdateMany(
      globalDb.sessions,
      { expires: { $exists: true } },
      [
        { $set: { expiresAt: { $ifNull: ["$expiresAt", "$expires"] } } },
        { $unset: ["expires"] }
      ],
      "modl.sessions expires -> expiresAt"
    );

    runUpdateMany(
      globalDb.auth_codes,
      { expires: { $exists: true } },
      [
        { $set: { expiresAt: { $ifNull: ["$expiresAt", "$expires"] } } },
        { $unset: ["expires"] }
      ],
      "modl.auth_codes expires -> expiresAt"
    );
  }

  function migrateGlobalSystemPrompts() {
    const coll = globalDb.systemprompts;
    const ops = [];

    coll.find({ strictnessLevel: { $exists: true } }).forEach(doc => {
      const normalized = normalizeEnum(doc.strictnessLevel, STRICTNESS_MAP, "STANDARD");
      if (normalized !== doc.strictnessLevel) {
        ops.push({
          updateOne: {
            filter: { _id: doc._id },
            update: { $set: { strictnessLevel: normalized } }
          }
        });
      }

      if (ops.length >= CONFIG.BATCH_SIZE) {
        flushBulk(coll, ops.splice(0, ops.length), "modl.systemprompts strictness normalization");
      }
    });

    flushBulk(coll, ops, "modl.systemprompts strictness normalization");
  }

  function migrateGlobalServers() {
    const coll = globalDb.servers;
    const ops = [];

    const legacyToCanonical = {
      subscription_status: "subscriptionStatus",
      current_period_start: "currentPeriodStart",
      current_period_end: "currentPeriodEnd",
      stripe_customer_id: "stripeCustomerId",
      stripe_subscription_id: "stripeSubscriptionId",
      cdn_usage_current_period: "cdnUsageCurrentPeriod",
      ai_requests_current_period: "aiRequestsCurrentPeriod",
      usage_billing_enabled: "usageBillingEnabled",
      usage_billing_updated_at: "usageBillingUpdatedAt",
      max_storage_limit_bytes: "maxStorageLimitBytes",
      max_ai_overage_requests: "maxAiOverageRequests",
      customDomain_override: "customDomainOverride",
      customDomain_status: "customDomainStatus",
      customDomain_lastChecked: "customDomainLastChecked",
      customDomain_error: "customDomainError",
      customDomain_cloudflareId: "customDomainCloudflareId"
    };

    coll.find({}).forEach(doc => {
      const setOps = {};
      const unsetOps = {};

      Object.keys(legacyToCanonical).forEach(oldField => {
        const newField = legacyToCanonical[oldField];
        if (doc[oldField] !== undefined && (doc[newField] === undefined || doc[newField] === null)) {
          setOps[newField] = doc[oldField];
        }
        if (doc[oldField] !== undefined) {
          unsetOps[oldField] = "";
        }
      });

      if (doc.plan !== undefined && doc.plan !== null) {
        const normalized = normalizeEnum(doc.plan, PLAN_MAP, "FREE");
        if (normalized !== doc.plan) setOps.plan = normalized;
      }

      if (doc.provisioningStatus !== undefined && doc.provisioningStatus !== null) {
        const normalized = normalizeEnum(doc.provisioningStatus, PROVISIONING_MAP, "PENDING");
        if (normalized !== doc.provisioningStatus) setOps.provisioningStatus = normalized;
      }

      if (doc.subscriptionStatus !== undefined && doc.subscriptionStatus !== null) {
        const normalized = normalizeEnum(doc.subscriptionStatus, SUBSCRIPTION_MAP, "INACTIVE");
        if (normalized !== doc.subscriptionStatus) setOps.subscriptionStatus = normalized;
      }

      if (doc.customDomainStatus !== undefined && doc.customDomainStatus !== null) {
        const normalized = normalizeEnum(doc.customDomainStatus, DOMAIN_STATUS_MAP, "PENDING");
        if (normalized !== doc.customDomainStatus) setOps.customDomainStatus = normalized;
      }

      if (Object.keys(setOps).length || Object.keys(unsetOps).length) {
        const update = {};
        if (Object.keys(setOps).length) update.$set = setOps;
        if (Object.keys(unsetOps).length) update.$unset = unsetOps;

        ops.push({ updateOne: { filter: { _id: doc._id }, update } });
      }

      if (ops.length >= CONFIG.BATCH_SIZE) {
        flushBulk(coll, ops.splice(0, ops.length), "modl.servers schema + enum normalization");
      }
    });

    flushBulk(coll, ops, "modl.servers schema + enum normalization");
  }

  function normalizeEmbeddedId(obj) {
    let changed = false;
    if (!obj || typeof obj !== "object") return { obj, changed };

    if (obj._id !== undefined) {
      if (obj.id === undefined || obj.id === null || obj.id === "") {
        obj.id = String(obj._id);
      }
      delete obj._id;
      changed = true;
    }

    return { obj, changed };
  }

  function flattenNestedArrays(value) {
    if (!Array.isArray(value)) {
      return { values: value, changed: false };
    }

    const flattened = [];
    let changed = false;

    const append = (item) => {
      if (Array.isArray(item)) {
        changed = true;
        item.forEach(append);
        return;
      }
      flattened.push(item);
    };

    value.forEach(append);
    return { values: flattened, changed };
  }

  function migrateTenantPlayers(tenantDbName) {
    const tenantDb = db.getSiblingDB(tenantDbName);
    const coll = tenantDb.players;
    const ops = [];
    let unresolvedExpiryDocs = 0;

    coll.find({}).forEach(doc => {
      const setOps = {};
      const unsetOps = {};
      let changed = false;
      let unresolvedExpiry = false;

      if (doc.ipList !== undefined) {
        if ((doc.ipAddresses === undefined || doc.ipAddresses === null) && Array.isArray(doc.ipList)) {
          const legacyIpListFlattened = flattenNestedArrays(doc.ipList);
          setOps.ipAddresses = legacyIpListFlattened.values;
        }
        unsetOps.ipList = "";
        changed = true;
      }

      if (Array.isArray(doc.usernames)) {
        const usernamesFlattened = flattenNestedArrays(doc.usernames);
        if (usernamesFlattened.changed) {
          setOps.usernames = usernamesFlattened.values;
          changed = true;
        }
      }

      if (Array.isArray(doc.ipAddresses)) {
        const ipAddressesFlattened = flattenNestedArrays(doc.ipAddresses);
        if (ipAddressesFlattened.changed) {
          setOps.ipAddresses = ipAddressesFlattened.values;
          changed = true;
        }
      }

      if (Array.isArray(doc.notes)) {
        const notesFlattened = flattenNestedArrays(doc.notes);
        let notesChanged = notesFlattened.changed;
        const normalizedNotes = notesFlattened.values.map(note => {
          const copy = note && typeof note === "object" ? { ...note } : note;
          if (copy && typeof copy === "object") {
            const result = normalizeEmbeddedId(copy);
            if (result.changed) notesChanged = true;
          }
          return copy;
        });

        if (notesChanged) {
          setOps.notes = normalizedNotes;
          changed = true;
        }
      }

      if (Array.isArray(doc.punishments)) {
        const punishmentsFlattened = flattenNestedArrays(doc.punishments);
        let punishmentsChanged = false;
        if (punishmentsFlattened.changed) {
          punishmentsChanged = true;
        }

        const normalizedPunishments = punishmentsFlattened.values.map(p => {
          const punishment = p && typeof p === "object" ? { ...p } : p;
          if (!punishment || typeof punishment !== "object") return punishment;

          const baseIdResult = normalizeEmbeddedId(punishment);
          if (baseIdResult.changed) punishmentsChanged = true;

          if (punishment.type_ordinal !== undefined) {
            if (punishment.typeOrdinal === undefined || punishment.typeOrdinal === null) {
              punishment.typeOrdinal = punishment.type_ordinal;
            }
            delete punishment.type_ordinal;
            punishmentsChanged = true;
          }

          if (punishment.typeOrdinal !== undefined && punishment.typeOrdinal !== null) {
            const numeric = Number(punishment.typeOrdinal);
            if (Number.isFinite(numeric)) {
              const intVal = Math.trunc(numeric);
              if (punishment.typeOrdinal !== intVal) {
                punishment.typeOrdinal = intVal;
                punishmentsChanged = true;
              }
            }
          }

          if (Array.isArray(punishment.modifications)) {
            const modificationsFlattened = flattenNestedArrays(punishment.modifications);
            if (modificationsFlattened.changed) {
              punishmentsChanged = true;
            }
            punishment.modifications = modificationsFlattened.values.map(m => {
              const mod = m && typeof m === "object" ? { ...m } : m;
              if (!mod || typeof mod !== "object") return mod;
              const idRes = normalizeEmbeddedId(mod);
              if (idRes.changed) punishmentsChanged = true;
              return mod;
            });
          }

          if (Array.isArray(punishment.notes)) {
            const punishmentNotesFlattened = flattenNestedArrays(punishment.notes);
            if (punishmentNotesFlattened.changed) {
              punishmentsChanged = true;
            }
            punishment.notes = punishmentNotesFlattened.values.map(n => {
              const note = n && typeof n === "object" ? { ...n } : n;
              if (!note || typeof note !== "object") return note;
              const idRes = normalizeEmbeddedId(note);
              if (idRes.changed) punishmentsChanged = true;
              return note;
            });
          }

          if (punishment.data && typeof punishment.data === "object") {
            const data = { ...punishment.data };
            const hadLegacyExpiry = data.expires !== undefined || data.expiresAt !== undefined;

            if ((data.duration === undefined || data.duration === null) && hadLegacyExpiry) {
              const expiryRaw = data.expiresAt !== undefined ? data.expiresAt : data.expires;
              const expiryDate = toDate(expiryRaw);

              // *** ROBUST PATCH ***
              if (!expiryDate) {
                 // CASE: Garbage data in expiry field.
                 // Action: Do not flag unresolved. Just let it fall through to remove the field.
                 // Duration remains unset (permanent).
                 if (CONFIG.DRY_RUN) log("WARN", `${tenantDbName} player ${doc._id} has invalid legacy expiry date. Treating as permanent.`);
              } else {
                 // CASE: Valid expiry date found
                 let baseDate = toDate(punishment.started) || toDate(punishment.issued);

                 // Fallback: If start date missing, assume started at expiry (duration 0)
                 if (!baseDate) {
                    baseDate = expiryDate;
                 }

                 let durationMs = expiryDate.getTime() - baseDate.getTime();

                 // Fallback: If duration is negative (expires before start), clamp to 0
                 if (durationMs < 0) {
                    durationMs = 0;
                 }

                 data.duration = toLong(durationMs);
                 punishmentsChanged = true;
              }
            }

            if (data.expires !== undefined) {
              delete data.expires;
              punishmentsChanged = true;
            }
            if (data.expiresAt !== undefined) {
              delete data.expiresAt;
              punishmentsChanged = true;
            }

            punishment.data = data;
          }

          return punishment;
        });

        if (punishmentsChanged) {
          setOps.punishments = normalizedPunishments;
          changed = true;
        }
      }

      if (unresolvedExpiry) {
        unresolvedExpiryDocs += 1;
      }

      if (changed) {
        const update = {};
        if (Object.keys(setOps).length) update.$set = setOps;
        if (Object.keys(unsetOps).length) update.$unset = unsetOps;

        ops.push({ updateOne: { filter: { _id: doc._id }, update } });
      }

      if (ops.length >= CONFIG.BATCH_SIZE) {
        flushBulk(coll, ops.splice(0, ops.length), `${tenantDbName}.players legacy cleanup`);
      }
    });

    flushBulk(coll, ops, `${tenantDbName}.players legacy cleanup`);

    if (unresolvedExpiryDocs > 0) {
      const message = `${tenantDbName}.players unresolved legacy expiry conversion docs=${unresolvedExpiryDocs}`;
      if (CONFIG.DRY_RUN) {
        log("WARN", `DRY RUN warning: ${message}`);
      } else {
        throw new Error(message);
      }
    }
  }

  function migrateTenantSettings(tenantDbName) {
    const tenantDb = db.getSiblingDB(tenantDbName);
    const settings = tenantDb.settings;

    runUpdateMany(
      settings,
      {
        type: "general",
        $or: [
          { "data.bugReportTags": { $exists: true } },
          { "data.playerReportTags": { $exists: true } },
          { "data.appealTags": { $exists: true } }
        ]
      },
      {
        $unset: {
          "data.bugReportTags": "",
          "data.playerReportTags": "",
          "data.appealTags": ""
        }
      },
      `${tenantDbName}.settings general legacy tag cleanup`
    );

    const legacyAiModerationDoc = settings.findOne({ type: "aiModeration" });
    const canonicalAiModerationDoc = settings.findOne({ type: "aiModerationSettings" });

    if (legacyAiModerationDoc && !canonicalAiModerationDoc) {
      if (CONFIG.DRY_RUN) {
        log("DRY_RUN", `${tenantDbName}.settings rename aiModeration -> aiModerationSettings`, {
          sourceId: String(legacyAiModerationDoc._id)
        });
      } else {
        const result = settings.updateOne(
          { _id: legacyAiModerationDoc._id },
          { $set: { type: "aiModerationSettings" } }
        );
        log("MIGRATE", `${tenantDbName}.settings renamed aiModeration type modified=${result.modifiedCount}`);
      }
    } else if (legacyAiModerationDoc && canonicalAiModerationDoc) {
      const legacyData = legacyAiModerationDoc.data && typeof legacyAiModerationDoc.data === "object"
        ? legacyAiModerationDoc.data
        : {};
      const canonicalData = canonicalAiModerationDoc.data && typeof canonicalAiModerationDoc.data === "object"
        ? canonicalAiModerationDoc.data
        : {};
      const mergedData = Object.assign({}, legacyData, canonicalData);

      if (CONFIG.DRY_RUN) {
        log("DRY_RUN", `${tenantDbName}.settings merge legacy aiModeration into aiModerationSettings and remove duplicate`, {
          sourceId: String(legacyAiModerationDoc._id),
          targetId: String(canonicalAiModerationDoc._id)
        });
      } else {
        settings.updateOne(
          { _id: canonicalAiModerationDoc._id },
          { $set: { data: mergedData, updatedAt: new Date() } }
        );
        settings.deleteOne({ _id: legacyAiModerationDoc._id });
        log("MIGRATE", `${tenantDbName}.settings merged and removed legacy aiModeration document`);
      }
    }

    const generalDoc = settings.findOne({ type: "general" });
    const generalLabels = generalDoc && generalDoc.data && Array.isArray(generalDoc.data.labels)
      ? generalDoc.data.labels
      : null;

    const ticketLabelsDoc = settings.findOne({ type: "ticketLabels" });
    if (!ticketLabelsDoc) {
      const labelPayload = {
        type: "ticketLabels",
        data: { labels: Array.isArray(generalLabels) ? generalLabels : [] },
        version: 0,
        updatedAt: new Date()
      };

      if (CONFIG.DRY_RUN) {
        log("DRY_RUN", `${tenantDbName}.settings create ticketLabels document`, labelPayload);
      } else {
        try {
          settings.insertOne(labelPayload);
          log("MIGRATE", `${tenantDbName}.settings created ticketLabels document`);
        } catch (error) {
          if (isDuplicateKeyError(error)) {
            logDuplicateKeySkip(`${tenantDbName}.settings create ticketLabels document`, error);
          } else {
            throw error;
          }
        }
      }
    } else if (Array.isArray(generalLabels) && generalLabels.length > 0) {
      const existingLabels = ticketLabelsDoc.data && Array.isArray(ticketLabelsDoc.data.labels)
        ? ticketLabelsDoc.data.labels
        : [];

      if (existingLabels.length === 0) {
        if (CONFIG.DRY_RUN) {
          log("DRY_RUN", `${tenantDbName}.settings backfill empty ticketLabels from general.labels`, {
            sourceId: String(generalDoc?._id || ""),
            targetId: String(ticketLabelsDoc._id)
          });
        } else {
          const result = settings.updateOne(
            { _id: ticketLabelsDoc._id },
            {
              $set: {
                "data.labels": generalLabels,
                updatedAt: new Date()
              }
            }
          );
          log("MIGRATE", `${tenantDbName}.settings backfill ticketLabels labels modified=${result.modifiedCount}`);
        }
      }
    }

    runUpdateMany(
      settings,
      { type: "general", "data.labels": { $exists: true } },
      { $unset: { "data.labels": "" } },
      `${tenantDbName}.settings move labels from general -> ticketLabels`
    );

    const strictnessOps = [];
    settings.find({ type: "aiModerationSettings", "data.strictnessLevel": { $exists: true } }).forEach(doc => {
      const current = doc?.data?.strictnessLevel;
      const normalized = normalizeEnum(current, STRICTNESS_MAP, "STANDARD");
      if (normalized !== current) {
        strictnessOps.push({
          updateOne: {
            filter: { _id: doc._id },
            update: { $set: { "data.strictnessLevel": normalized } }
          }
        });
      }

      if (strictnessOps.length >= CONFIG.BATCH_SIZE) {
        flushBulk(settings, strictnessOps.splice(0, strictnessOps.length), `${tenantDbName}.settings aiModerationSettings strictness normalization`);
      }
    });

    flushBulk(settings, strictnessOps, `${tenantDbName}.settings aiModerationSettings strictness normalization`);
  }

  function migrateTenantStaffRoles(tenantDbName) {
    const tenantDb = db.getSiblingDB(tenantDbName);
    const coll = tenantDb.staffroles;
    const staffColl = tenantDb.staffs;
    const invitationColl = tenantDb.invitations;
    let rewritten = 0;
    let removedLegacyId = 0;
    let collisions = 0;
    let normalizedNames = 0;
    let dedupedRoles = 0;
    let mergedPermissions = 0;
    let normalizedStaffAssignments = 0;
    let normalizedInvitationAssignments = 0;

    coll.find({ id: { $exists: true } }).forEach(doc => {
      const legacyId = doc.id;
      if (legacyId === undefined || legacyId === null || String(legacyId).trim() === "") {
        if (CONFIG.DRY_RUN) {
          removedLegacyId += 1;
        } else {
          try {
            const result = coll.updateOne({ _id: doc._id }, { $unset: { id: "" } });
            if (result.modifiedCount > 0) removedLegacyId += 1;
          } catch (error) {
            if (isDuplicateKeyError(error)) {
              logDuplicateKeySkip(`${tenantDbName}.staffroles remove empty legacy id for _id=${String(doc._id)}`, error);
            } else {
              throw error;
            }
          }
        }
        return;
      }

      const canonicalId = String(legacyId);
      const currentId = String(doc._id);

      if (currentId === canonicalId) {
        if (CONFIG.DRY_RUN) {
          removedLegacyId += 1;
        } else {
          try {
            const result = coll.updateOne({ _id: doc._id }, { $unset: { id: "" } });
            if (result.modifiedCount > 0) removedLegacyId += 1;
          } catch (error) {
            if (isDuplicateKeyError(error)) {
              logDuplicateKeySkip(`${tenantDbName}.staffroles remove legacy id for _id=${String(doc._id)}`, error);
            } else {
              throw error;
            }
          }
        }
        return;
      }

      if (coll.findOne({ _id: canonicalId })) {
        collisions += 1;
        const message = `${tenantDbName}.staffroles collision: legacy _id=${String(doc._id)} wants canonical _id=${canonicalId}`;
        if (CONFIG.DRY_RUN) {
          log("WARN", message);
        } else {
          throw new Error(`${message}. Resolve manually before commit-mode migration.`);
        }
        return;
      }

      const replacement = { ...doc, _id: canonicalId };
      delete replacement.id;

      if (CONFIG.DRY_RUN) {
        rewritten += 1;
      } else {
        try {
          coll.insertOne(replacement);
          coll.deleteOne({ _id: doc._id });
          rewritten += 1;
        } catch (error) {
          if (isDuplicateKeyError(error)) {
            collisions += 1;
            logDuplicateKeySkip(`${tenantDbName}.staffroles rewrite _id ${String(doc._id)} -> ${canonicalId}`, error);
          } else {
            throw error;
          }
        }
      }
    });

    const roles = coll.find({}).toArray();
    const grouped = {};

    roles.forEach(roleDoc => {
      const docId = String(roleDoc._id);
      const fallbackName = canonicalDefaultRoleDisplayName(docId, `Role ${docId}`);
      let normalizedName = normalizeStaffRoleName(roleDoc.name);
      if (!normalizedName) {
        normalizedName = fallbackName;
      }

      const canonicalName = canonicalDefaultRoleDisplayName(docId, normalizedName);
      if (canonicalName !== roleDoc.name) {
        normalizedNames += 1;
        if (!CONFIG.DRY_RUN) {
          try {
            coll.updateOne(
              { _id: roleDoc._id },
              { $set: { name: canonicalName, updatedAt: new Date() } }
            );
          } catch (error) {
            if (isDuplicateKeyError(error)) {
              logDuplicateKeySkip(`${tenantDbName}.staffroles normalize name for _id=${String(roleDoc._id)}`, error);
            } else {
              throw error;
            }
          }
        }
      }

      const key = roleNameKey(canonicalName);
      if (!grouped[key]) grouped[key] = [];
      grouped[key].push({ ...roleDoc, name: canonicalName });
    });

    Object.keys(grouped).forEach(roleKey => {
      const group = grouped[roleKey];
      if (!group || group.length === 0) return;

      const sorted = [...group].sort((a, b) => {
        function rank(doc) {
          const id = String(doc._id);
          const defaultRank = id === "super-admin" ? 0
            : id === "admin" ? 1
            : id === "moderator" ? 2
            : id === "helper" ? 3
            : 99;
          const isDefaultRank = doc.isDefault === true ? 0 : 1;
          const orderRank = Number.isFinite(Number(doc.order)) ? Number(doc.order) : Number.MAX_SAFE_INTEGER;
          const createdAtDate = toDate(doc.createdAt);
          const createdAtRank = createdAtDate ? createdAtDate.getTime() : Number.MAX_SAFE_INTEGER;
          return [defaultRank, isDefaultRank, orderRank, createdAtRank, String(doc._id)];
        }

        const aRank = rank(a);
        const bRank = rank(b);
        for (let i = 0; i < aRank.length; i += 1) {
          if (aRank[i] < bRank[i]) return -1;
          if (aRank[i] > bRank[i]) return 1;
        }
        return 0;
      });

      const keeper = sorted[0];
      const duplicates = sorted.slice(1);
      const canonicalName = normalizeStaffRoleName(keeper.name);

      const knownNames = [...new Set(
        sorted
          .map(role => normalizeStaffRoleName(role.name))
          .filter(Boolean)
      )];
      const nameAlternatives = knownNames.map(name => escapeRegex(name)).join("|");
      const assignmentRegex = `^\\s*(?:${nameAlternatives})\\s*$`;

      if (nameAlternatives) {
        if (CONFIG.DRY_RUN) {
          normalizedStaffAssignments += staffColl.countDocuments({ role: { $regex: assignmentRegex, $options: "i" } });
          normalizedInvitationAssignments += invitationColl.countDocuments({ role: { $regex: assignmentRegex, $options: "i" } });
        } else {
          try {
            const staffResult = staffColl.updateMany(
              { role: { $regex: assignmentRegex, $options: "i" } },
              { $set: { role: canonicalName } }
            );
            normalizedStaffAssignments += staffResult.modifiedCount;
          } catch (error) {
            if (isDuplicateKeyError(error)) {
              logDuplicateKeySkip(`${tenantDbName}.staff normalize role assignments -> ${canonicalName}`, error);
            } else {
              throw error;
            }
          }

          try {
            const invitationResult = invitationColl.updateMany(
              { role: { $regex: assignmentRegex, $options: "i" } },
              { $set: { role: canonicalName } }
            );
            normalizedInvitationAssignments += invitationResult.modifiedCount;
          } catch (error) {
            if (isDuplicateKeyError(error)) {
              logDuplicateKeySkip(`${tenantDbName}.invitations normalize role assignments -> ${canonicalName}`, error);
            } else {
              throw error;
            }
          }
        }
      }

      if (duplicates.length === 0) {
        return;
      }

      dedupedRoles += duplicates.length;

      const permissionSeen = {};
      const mergedPermissionList = [];
      sorted.forEach(role => {
        const permissions = Array.isArray(role.permissions) ? role.permissions : [];
        permissions.forEach(permission => {
          if (typeof permission !== "string") return;
          if (permissionSeen[permission]) return;
          permissionSeen[permission] = true;
          mergedPermissionList.push(permission);
        });
      });

      const keeperPermissions = Array.isArray(keeper.permissions)
        ? keeper.permissions.filter(permission => typeof permission === "string")
        : [];
      const permissionsChanged = mergedPermissionList.length !== keeperPermissions.length
        || mergedPermissionList.some((permission, idx) => permission !== keeperPermissions[idx]);

      const keeperDescription = typeof keeper.description === "string" ? keeper.description : "";
      let mergedDescription = keeperDescription;
      if (!mergedDescription) {
        const described = sorted.find(role => typeof role.description === "string" && role.description.trim() !== "");
        if (described) mergedDescription = described.description;
      }

      const mergedIsDefault = sorted.some(role => role.isDefault === true);
      const mergedOrder = Math.min(
        ...sorted.map(role => Number.isFinite(Number(role.order)) ? Number(role.order) : Number.MAX_SAFE_INTEGER)
      );
      const keeperOrder = Number.isFinite(Number(keeper.order)) ? Number(keeper.order) : Number.MAX_SAFE_INTEGER;

      const keeperSet = {};
      if (permissionsChanged) {
        keeperSet.permissions = mergedPermissionList;
        mergedPermissions += 1;
      }
      if (mergedDescription !== keeperDescription) keeperSet.description = mergedDescription;
      if (keeper.isDefault !== mergedIsDefault) keeperSet.isDefault = mergedIsDefault;
      if (mergedOrder !== Number.MAX_SAFE_INTEGER && mergedOrder !== keeperOrder) keeperSet.order = mergedOrder;
      if (Object.keys(keeperSet).length > 0) {
        keeperSet.updatedAt = new Date();
      }

      if (CONFIG.DRY_RUN) {
        log("DRY_RUN", `${tenantDbName}.staffroles duplicate role names for "${canonicalName}"`, {
          keeperId: String(keeper._id),
          duplicateIds: duplicates.map(role => String(role._id)),
          mergedPermissionsChanged: permissionsChanged
        });
      } else {
        duplicates.forEach(duplicate => {
          coll.deleteOne({ _id: duplicate._id });
        });

        if (Object.keys(keeperSet).length > 0) {
          try {
            coll.updateOne({ _id: keeper._id }, { $set: keeperSet });
          } catch (error) {
            if (isDuplicateKeyError(error)) {
              logDuplicateKeySkip(`${tenantDbName}.staffroles merge keeper updates for _id=${String(keeper._id)}`, error);
            } else {
              throw error;
            }
          }
        }
      }
    });

    const section = CONFIG.DRY_RUN ? "DRY_RUN" : "MIGRATE";
    log(section, `${tenantDbName}.staffroles id/name normalization`, {
      rewritten,
      removedLegacyId,
      collisions,
      normalizedNames,
      dedupedRoles,
      mergedPermissions,
      normalizedStaffAssignments,
      normalizedInvitationAssignments
    });
  }

  function migrateTenantInvitations(tenantDbName) {
    const tenantDb = db.getSiblingDB(tenantDbName);
    const coll = tenantDb.invitations;

    runUpdateMany(
      coll,
      { expires: { $exists: true } },
      [
        { $set: { expiresAt: { $ifNull: ["$expiresAt", "$expires"] } } },
        { $unset: ["expires"] }
      ],
      `${tenantDbName}.invitations expires -> expiresAt`
    );

    runDeleteMany(
      coll,
      { status: { $type: "string", $regex: /^accepted$/i } },
      `${tenantDbName}.invitations delete accepted invitations`
    );

    runUpdateMany(
      coll,
      { status: { $exists: true } },
      { $unset: { status: "" } },
      `${tenantDbName}.invitations remove legacy status field`
    );

    runDeleteMany(
      coll,
      {
        $or: [
          { expiresAt: { $exists: false } },
          { expiresAt: null }
        ]
      },
      `${tenantDbName}.invitations delete malformed records without expiresAt`
    );
  }

  function migrateTenantKnowledgebaseCategories(tenantDbName) {
    const tenantDb = db.getSiblingDB(tenantDbName);
    const coll = tenantDb.knowledgebasecategories;

    runUpdateMany(
      coll,
      {
        $or: [
          { is_visible: { $exists: true } },
          { created_at: { $exists: true } },
          { updated_at: { $exists: true } }
        ]
      },
      [
        {
          $set: {
            isVisible: { $ifNull: ["$isVisible", "$is_visible"] },
            createdAt: { $ifNull: ["$createdAt", "$created_at"] },
            updatedAt: { $ifNull: ["$updatedAt", "$updated_at"] }
          }
        },
        { $unset: ["is_visible", "created_at", "updated_at"] }
      ],
      `${tenantDbName}.knowledgebasecategories snake_case -> camelCase`
    );

    runUpdateMany(
      coll,
      { isVisible: { $exists: false } },
      { $set: { isVisible: true } },
      `${tenantDbName}.knowledgebasecategories set default isVisible=true`
    );
  }

  function migrateTenantKnowledgebaseArticles(tenantDbName) {
    const tenantDb = db.getSiblingDB(tenantDbName);
    const coll = tenantDb.knowledgebasearticles;

    runUpdateMany(
      coll,
      {
        $or: [
          { category_id: { $exists: true } },
          { is_visible: { $exists: true } },
          { created_at: { $exists: true } },
          { updated_at: { $exists: true } }
        ]
      },
      [
        {
          $set: {
            categoryId: { $ifNull: ["$categoryId", "$category_id"] },
            isVisible: { $ifNull: ["$isVisible", "$is_visible"] },
            createdAt: { $ifNull: ["$createdAt", "$created_at"] },
            updatedAt: { $ifNull: ["$updatedAt", "$updated_at"] }
          }
        },
        { $unset: ["category_id", "is_visible", "created_at", "updated_at"] }
      ],
      `${tenantDbName}.knowledgebasearticles snake_case -> camelCase`
    );

    runUpdateMany(
      coll,
      { isVisible: { $exists: false } },
      { $set: { isVisible: true } },
      `${tenantDbName}.knowledgebasearticles set default isVisible=true`
    );

    const validCategoryIds = new Set();
    tenantDb.knowledgebasecategories.find({}, { _id: 1 }).forEach(category => {
      validCategoryIds.add(String(category._id));
    });

    const orphanIds = [];
    coll.find({}, { _id: 1, categoryId: 1 }).forEach(article => {
      const rawCategoryId = article.categoryId;
      if (rawCategoryId === undefined || rawCategoryId === null || String(rawCategoryId).trim() === "") {
        orphanIds.push(article._id);
        return;
      }

      if (!validCategoryIds.has(String(rawCategoryId))) {
        orphanIds.push(article._id);
      }
    });

    if (orphanIds.length === 0) {
      log("MIGRATE", `${tenantDbName}.knowledgebasearticles orphan cleanup: nothing to delete`);
      return;
    }

    if (CONFIG.DRY_RUN) {
      log("DRY_RUN", `${tenantDbName}.knowledgebasearticles orphan cleanup: would delete ${orphanIds.length} document(s)`);
      return;
    }

    let deleted = 0;
    for (let i = 0; i < orphanIds.length; i += CONFIG.BATCH_SIZE) {
      const batch = orphanIds.slice(i, i + CONFIG.BATCH_SIZE);
      const result = coll.deleteMany({ _id: { $in: batch } });
      deleted += result.deletedCount;
    }
    log("MIGRATE", `${tenantDbName}.knowledgebasearticles orphan cleanup: deleted=${deleted}`);
  }

  function migrateTenantHomepageCards(tenantDbName) {
    const tenantDb = db.getSiblingDB(tenantDbName);
    const coll = tenantDb.homepagecards;

    runUpdateMany(
      coll,
      {
        $or: [
          { icon_color: { $exists: true } },
          { action_type: { $exists: true } },
          { action_url: { $exists: true } },
          { action_button_text: { $exists: true } },
          { category_id: { $exists: true } },
          { background_color: { $exists: true } },
          { is_enabled: { $exists: true } },
          { created_at: { $exists: true } },
          { updated_at: { $exists: true } }
        ]
      },
      [
        {
          $set: {
            iconColor: { $ifNull: ["$iconColor", "$icon_color"] },
            actionType: { $ifNull: ["$actionType", "$action_type"] },
            actionUrl: { $ifNull: ["$actionUrl", "$action_url"] },
            actionButtonText: { $ifNull: ["$actionButtonText", "$action_button_text"] },
            categoryId: { $ifNull: ["$categoryId", "$category_id"] },
            backgroundColor: { $ifNull: ["$backgroundColor", "$background_color"] },
            isEnabled: { $ifNull: ["$isEnabled", "$is_enabled"] },
            createdAt: { $ifNull: ["$createdAt", "$created_at"] },
            updatedAt: { $ifNull: ["$updatedAt", "$updated_at"] }
          }
        },
        { $unset: ["icon_color", "action_type", "action_url", "action_button_text", "category_id", "background_color", "is_enabled", "created_at", "updated_at"] }
      ],
      `${tenantDbName}.homepagecards snake_case -> camelCase`
    );

    const ops = [];
    coll.find({}).forEach(doc => {
      const setOps = {};
      let changed = false;

      const normalizedActionType = normalizeHomepageActionType(doc.actionType);
      if (doc.actionType !== normalizedActionType) {
        setOps.actionType = normalizedActionType;
        changed = true;
      }

      if (doc.isEnabled === undefined || doc.isEnabled === null) {
        setOps.isEnabled = true;
        changed = true;
      }

      if (changed) {
        ops.push({
          updateOne: {
            filter: { _id: doc._id },
            update: { $set: setOps }
          }
        });
      }

      if (ops.length >= CONFIG.BATCH_SIZE) {
        flushBulk(coll, ops.splice(0, ops.length), `${tenantDbName}.homepagecards enum/default normalization`);
      }
    });

    flushBulk(coll, ops, `${tenantDbName}.homepagecards enum/default normalization`);
  }

  function migrateTenantTickets(tenantDbName) {
    const tenantDb = db.getSiblingDB(tenantDbName);

    runUpdateMany(
      tenantDb.tickets,
      {
        $or: [
          { creator: { $exists: true } },
          { creatorName: { $exists: false } },
          { creatorName: null }
        ]
      },
      [
        {
          $set: {
            creatorName: {
              $ifNull: ["$creatorName", { $ifNull: ["$creator", "Unknown"] }]
            }
          }
        },
        { $unset: ["creator"] }
      ],
      `${tenantDbName}.tickets creator -> creatorName`
    );
  }

  function migrateTenantTicketVerifications(tenantDbName) {
    const tenantDb = db.getSiblingDB(tenantDbName);
    runUpdateMany(
      tenantDb.ticket_verifications,
      { expires: { $exists: true } },
      [
        { $set: { expiresAt: { $ifNull: ["$expiresAt", "$expires"] } } },
        { $unset: ["expires"] }
      ],
      `${tenantDbName}.ticket_verifications expires -> expiresAt`
    );
  }

  function uniqueDuplicates(coll, spec) {
    if (!spec.options || !spec.options.unique) {
      return [];
    }

    const fields = Object.keys(spec.key);
    const groupId = {};
    fields.forEach(f => { groupId[f] = `$${f}`; });

    const pipeline = [];
    if (spec.options.sparse) {
      const sparseMatch = {};
      fields.forEach(f => { sparseMatch[f] = { $exists: true, $ne: null }; });
      pipeline.push({ $match: sparseMatch });
    }

    pipeline.push(
      { $group: { _id: groupId, count: { $sum: 1 } } },
      { $match: { count: { $gt: 1 } } },
      { $limit: 5 }
    );

    return coll.aggregate(pipeline).toArray();
  }

  function rebuildIndexesForCollection(dbName, collName, specs) {
    const database = db.getSiblingDB(dbName);
    const collectionNames = database.getCollectionNames();
    if (!collectionNames.includes(collName)) {
      if (CONFIG.DRY_RUN) {
        log("DRY_RUN", `${dbName}.${collName} collection missing; would create and apply canonical indexes`);
        return;
      }
      database.createCollection(collName);
      log("INDEX", `Created missing collection ${dbName}.${collName}`);
    }

    const coll = database.getCollection(collName);

    const uniqueIndexSkipSet = new Set();
    for (const spec of specs) {
      const dups = uniqueDuplicates(coll, spec);
      if (dups.length > 0) {
        uniqueIndexSkipSet.add(spec.name);
        const msg = `${dbName}.${collName} has duplicate keys for unique index ${spec.name}. Script will skip this unique index and continue.`;
        log("WARN", msg, dups);
      }
    }

    const existingIndexes = coll.getIndexes().filter(i => i.name !== "_id_");

    if (CONFIG.DRY_RUN) {
      log("DRY_RUN", `${dbName}.${collName} indexes to drop`, existingIndexes.map(i => i.name));
      log("DRY_RUN", `${dbName}.${collName} indexes to create`, specs.map(s => ({ name: s.name, key: s.key, options: s.options })));
      if (uniqueIndexSkipSet.size > 0) {
        log("DRY_RUN", `${dbName}.${collName} unique indexes that would be skipped due to duplicate data`, Array.from(uniqueIndexSkipSet));
      }
      return;
    }

    for (const idx of existingIndexes) {
      coll.dropIndex(idx.name);
      log("INDEX", `Dropped ${dbName}.${collName}.${idx.name}`);
    }

    for (const spec of specs) {
      if (uniqueIndexSkipSet.has(spec.name)) {
        log("WARN", `Skipped ${dbName}.${collName}.${spec.name} because duplicate data was detected during pre-check`);
        continue;
      }

      try {
        coll.createIndex(spec.key, { ...spec.options, name: spec.name });
        log("INDEX", `Created ${dbName}.${collName}.${spec.name}`);
      } catch (error) {
        const code = error && error.code;
        const message = error && error.message ? error.message : String(error);

        if (code === 11000 || /E11000/i.test(message)) {
          log("WARN", `Skipped ${dbName}.${collName}.${spec.name} because duplicate data violates uniqueness`, { code, message });
          continue;
        }

        if (code === 85 || code === 86 || /IndexOptionsConflict|IndexKeySpecsConflict|already exists/i.test(message)) {
          log("WARN", `Skipped ${dbName}.${collName}.${spec.name} due to index conflict with existing state`, { code, message });
          continue;
        }

        throw error;
      }
    }
  }

  function rebuildCanonicalIndexes(tenantNames) {
    Object.keys(INDEX_MANIFEST_GLOBAL).forEach(collName => {
      rebuildIndexesForCollection(CONFIG.GLOBAL_DB, collName, INDEX_MANIFEST_GLOBAL[collName]);
    });

    tenantNames.forEach(tenant => {
      Object.keys(INDEX_MANIFEST_TENANT).forEach(collName => {
        rebuildIndexesForCollection(tenant, collName, INDEX_MANIFEST_TENANT[collName]);
      });
    });
  }

  function run() {
    const tenants = tenantDbNames();
    log("INFO", `DRY_RUN=${CONFIG.DRY_RUN}`);
    log("INFO", `Tenant DB count=${tenants.length}`);
    log("INFO", "Tenant DB names", tenants);

    runStep("global migrateGlobalSessionsAndCodes", () => migrateGlobalSessionsAndCodes());
    runStep("global migrateGlobalSystemPrompts", () => migrateGlobalSystemPrompts());
    runStep("global migrateGlobalServers", () => migrateGlobalServers());

    tenants.forEach(tenant => {
      runStep(`${tenant} migrateTenantPlayers`, () => migrateTenantPlayers(tenant));
      runStep(`${tenant} migrateTenantSettings`, () => migrateTenantSettings(tenant));
      runStep(`${tenant} migrateTenantStaffRoles`, () => migrateTenantStaffRoles(tenant));
      runStep(`${tenant} migrateTenantInvitations`, () => migrateTenantInvitations(tenant));
      runStep(`${tenant} migrateTenantTickets`, () => migrateTenantTickets(tenant));
      runStep(`${tenant} migrateTenantKnowledgebaseCategories`, () => migrateTenantKnowledgebaseCategories(tenant));
      runStep(`${tenant} migrateTenantKnowledgebaseArticles`, () => migrateTenantKnowledgebaseArticles(tenant));
      runStep(`${tenant} migrateTenantHomepageCards`, () => migrateTenantHomepageCards(tenant));
      runStep(`${tenant} migrateTenantTicketVerifications`, () => migrateTenantTicketVerifications(tenant));
    });

    runStep("global rebuildCanonicalIndexes", () => rebuildCanonicalIndexes(tenants));
    log("DONE", "Migration script completed");
  }

  run();
})();
```

---

## Script 3: Post-Verification
Run after commit-mode migration.

```javascript
(() => {
  const GLOBAL_DB = "modl";
  const globalDb = db.getSiblingDB(GLOBAL_DB);

  const expectedGlobalIndexes = {
    sessions: ["_id_", "idx_sessions_email", "idx_sessions_expiresAt_ttl"],
    auth_codes: ["_id_", "uidx_auth_codes_email", "idx_auth_codes_expiresAt_ttl"],
    servers: [
      "_id_",
      "uidx_servers_serverName",
      "uidx_servers_customDomain",
      "uidx_servers_adminEmail",
      "idx_servers_emailVerified",
      "uidx_servers_emailVerificationToken",
      "idx_servers_provisioningStatus",
      "uidx_servers_provisioningSignInToken",
      "uidx_servers_stripeCustomerId",
      "uidx_servers_stripeSubscriptionId",
      "uidx_servers_customDomainOverride",
      "uidx_servers_customDomainCloudflareId",
      "uidx_servers_apiKey",
      "idx_servers_createdAt"
    ]
  };

  const expectedTenantIndexes = {
    settings: ["_id_", "uidx_settings_type"],
    players: ["_id_", "uidx_players_minecraftUuid"],
    staffs: ["_id_", "uidx_staff_email", "uidx_staff_username", "sidx_staff_assignedMinecraftUuid"],
    staffroles: ["_id_", "uidx_staff_roles_name", "idx_staff_roles_order"],
    invitations: ["_id_", "idx_invitations_email", "uidx_invitations_token", "idx_invitations_expiresAt_ttl"],
    tickets: [
      "_id_",
      "idx_tickets_status_created",
      "idx_tickets_created",
      "idx_tickets_updatedAt",
      "idx_tickets_type_created",
      "idx_tickets_creatorUuid_created",
      "idx_tickets_reportedPlayerUuid_created",
      "idx_tickets_locked_created",
      "idx_tickets_tags"
    ],
    ticket_verifications: ["_id_", "idx_ticket_verifications_expiresAt_ttl"],
    knowledgebasecategories: [
      "_id_",
      "uidx_knowledgebase_categories_slug",
      "idx_knowledgebase_categories_name",
      "idx_knowledgebase_categories_ordinal",
      "idx_knowledgebase_categories_isVisible_ordinal"
    ],
    knowledgebasearticles: [
      "_id_",
      "uidx_knowledgebase_articles_slug",
      "idx_knowledgebase_articles_categoryId_ordinal",
      "idx_knowledgebase_articles_isVisible_categoryId_ordinal"
    ],
    homepagecards: [
      "_id_",
      "idx_homepage_cards_ordinal",
      "idx_homepage_cards_isEnabled_ordinal",
      "idx_homepage_cards_categoryId"
    ]
  };
  function tenantDbNames() {
    const names = globalDb.getCollection("servers")
      .distinct("databaseName", { databaseName: { $type: "string", $nin: ["", GLOBAL_DB] } })
      .filter(Boolean);
    return [...new Set(names)].sort();
  }

  function checkIndexes(dbName, collectionName, expectedNames) {
    const coll = db.getSiblingDB(dbName).getCollection(collectionName);
    let names = [];
    try {
      names = coll.getIndexes().map(i => i.name).sort();
    } catch {
      print(`[VERIFY] ${dbName}.${collectionName}: missing collection`);
      return;
    }

    const expected = [...expectedNames].sort();
    const missing = expected.filter(n => !names.includes(n));
    const extra = names.filter(n => !expected.includes(n));

    print(`[VERIFY] ${dbName}.${collectionName} index_names=${JSON.stringify(names)}`);
    if (missing.length) print(`[VERIFY][ERROR] missing indexes: ${JSON.stringify(missing)}`);
    if (extra.length) print(`[VERIFY][ERROR] unexpected indexes: ${JSON.stringify(extra)}`);
  }

  function countLegacy(dbName) {
    const d = db.getSiblingDB(dbName);

    const result = {
      legacyServerFields: dbName === GLOBAL_DB
        ? d.servers.countDocuments({
            $or: [
              { subscription_status: { $exists: true } },
              { current_period_start: { $exists: true } },
              { current_period_end: { $exists: true } },
              { stripe_customer_id: { $exists: true } },
              { stripe_subscription_id: { $exists: true } },
              { customDomain_override: { $exists: true } },
              { customDomain_status: { $exists: true } },
              { customDomain_lastChecked: { $exists: true } },
              { customDomain_error: { $exists: true } },
              { customDomain_cloudflareId: { $exists: true } }
            ]
          })
        : null,
      playersLegacyFields: dbName === GLOBAL_DB
        ? null
        : d.players.countDocuments({
            $or: [
              { ipList: { $exists: true } },
              { "usernames.0": { $type: "array" } },
              { "notes.0": { $type: "array" } },
              { "ipAddresses.0": { $type: "array" } },
              { "punishments.0": { $type: "array" } },
              { "notes._id": { $exists: true } },
              { "punishments._id": { $exists: true } },
              { "punishments.type_ordinal": { $exists: true } },
              { "punishments.modifications.0": { $type: "array" } },
              { "punishments.modifications._id": { $exists: true } },
              { "punishments.notes.0": { $type: "array" } },
              { "punishments.notes._id": { $exists: true } },
              { "punishments.data.expires": { $exists: true } },
              { "punishments.data.expiresAt": { $exists: true } }
            ]
          }),
      invitationsLegacyFields: dbName === GLOBAL_DB
        ? null
        : d.invitations.countDocuments({
            $or: [
              { status: { $exists: true } },
              { expires: { $exists: true } },
              { expiresAt: { $exists: false } },
              { expiresAt: null }
            ]
          }),
      settingsLegacyGeneralFields: dbName === GLOBAL_DB
        ? null
        : d.settings.countDocuments({
            type: "general",
            $or: [
              { "data.bugReportTags": { $exists: true } },
              { "data.playerReportTags": { $exists: true } },
              { "data.appealTags": { $exists: true } },
              { "data.labels": { $exists: true } }
            ]
          }),
      settingsMissingTicketLabelsDoc: dbName === GLOBAL_DB
        ? null
        : (d.settings.countDocuments({ type: "ticketLabels" }) === 0 ? 1 : 0),
      settingsAiStrictnessLowercase: dbName === GLOBAL_DB
        ? null
        : d.settings.countDocuments({
            type: "aiModerationSettings",
            "data.strictnessLevel": { $in: ["lenient", "standard", "strict"] }
          }),
      settingsLegacyAiModerationType: dbName === GLOBAL_DB
        ? null
        : d.settings.countDocuments({ type: "aiModeration" }),
      staffRolesLegacyIdField: dbName === GLOBAL_DB ? null : d.staffroles.countDocuments({ id: { $exists: true } }),
      ticketsLegacyCreatorField: dbName === GLOBAL_DB ? null : d.tickets.countDocuments({ creator: { $exists: true } }),
      homepageCardsLegacyFields: dbName === GLOBAL_DB
        ? null
        : d.homepagecards.countDocuments({
            $or: [
              { icon_color: { $exists: true } },
              { action_type: { $exists: true } },
              { action_url: { $exists: true } },
              { action_button_text: { $exists: true } },
              { category_id: { $exists: true } },
              { background_color: { $exists: true } },
              { is_enabled: { $exists: true } },
              { created_at: { $exists: true } },
              { updated_at: { $exists: true } }
            ]
          }),
      kbCategoriesLegacyFields: dbName === GLOBAL_DB
        ? null
        : d.knowledgebasecategories.countDocuments({
            $or: [
              { is_visible: { $exists: true } },
              { created_at: { $exists: true } },
              { updated_at: { $exists: true } }
            ]
          }),
      kbArticlesLegacyFields: dbName === GLOBAL_DB
        ? null
        : d.knowledgebasearticles.countDocuments({
            $or: [
              { category_id: { $exists: true } },
              { is_visible: { $exists: true } },
              { created_at: { $exists: true } },
              { updated_at: { $exists: true } }
            ]
          }),
      kbArticlesNullCategory: dbName === GLOBAL_DB
        ? null
        : d.knowledgebasearticles.countDocuments({
            $or: [
              { categoryId: { $exists: false } },
              { categoryId: null },
              { categoryId: "" }
            ]
          })
    };

    print(`[VERIFY] ${dbName} legacy_counts=${JSON.stringify(result)}`);
  }

  const tenants = tenantDbNames();

  Object.keys(expectedGlobalIndexes).forEach(c => checkIndexes(GLOBAL_DB, c, expectedGlobalIndexes[c]));
  countLegacy(GLOBAL_DB);

  tenants.forEach(t => {
    Object.keys(expectedTenantIndexes).forEach(c => checkIndexes(t, c, expectedTenantIndexes[c]));
    countLegacy(t);
  });

  const now = new Date();
  print(`[VERIFY][TTL] modl.sessions expired_docs_remaining=${globalDb.sessions.countDocuments({ expiresAt: { $lt: now } })}`);
  print(`[VERIFY][TTL] modl.auth_codes expired_docs_remaining=${globalDb.auth_codes.countDocuments({ expiresAt: { $lt: now } })}`);
  tenants.forEach(t => {
    const d = db.getSiblingDB(t);
    print(`[VERIFY][TTL] ${t}.invitations expired_docs_remaining=${d.invitations.countDocuments({ expiresAt: { $lt: now } })}`);
    print(`[VERIFY][TTL] ${t}.ticket_verifications expired_docs_remaining=${d.ticket_verifications.countDocuments({ expiresAt: { $lt: now } })}`);
  });

  print("[VERIFY] Note: TTL deletion is asynchronous (monitor runs periodically). Small temporary non-zero counts are normal immediately after migration.");
})();
```

---

## Operational Notes
- Put the backend in maintenance mode during commit-mode migration if possible.
- If unique indexes are skipped because duplicates exist, deduplicate data and rerun commit mode to restore those unique constraints.
- Keep the backup archive until after at least one full business cycle of monitoring.
- Restarting backend after migration is recommended so index warmup logs clearly confirm canonical index state.
- `servers.currentPeriodEnd` is allowed to be `null` for non-billing/inactive records; this runbook does not force synthetic values.
