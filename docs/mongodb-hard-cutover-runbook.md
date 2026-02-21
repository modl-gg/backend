# MongoDB Hard Cutover Runbook (Schema + Enum + Index Cleanup)

This runbook migrates production data to the backend's strict cleaned schema and removes legacy compatibility needs from runtime code.

## Scope
- Global DB: `modl`
- Tenant DBs: discovered from `modl.servers.databaseName`
- Collections covered:
  - Global: `servers`, `sessions`, `auth_codes`, `systemprompts`
  - Tenant: `players`, `settings`, `staffs`, `staffroles`, `invitations`, `ticket_verifications`

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
      print(`[${section}] ${message} :: ${tojson(payload)}`);
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
        { "notes._id": { $exists: true } },
        { "punishments._id": { $exists: true } },
        { "punishments.type_ordinal": { $exists: true } },
        { "punishments.modifications._id": { $exists: true } },
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

    const aiStrictnessLowercaseCount = tenantDb.settings.countDocuments({
      type: "aiModeration",
      "data.strictnessLevel": { $in: ["lenient", "standard", "strict"] }
    });

    return {
      db: dbName,
      playersLegacyCount,
      ticketLegacyExpiresCount,
      generalLegacyTagsCount,
      aiStrictnessLowercaseCount
    };
  }

  const tenants = tenantDbNames();
  log("INFO", `Discovered tenant DB count: ${tenants.length}`);
  log("INFO", "Tenant DB names", tenants);

  log("LEGACY", "Global legacy counts", countGlobalLegacy());
  tenants.forEach(name => log("LEGACY", `Tenant legacy counts for ${name}`, countTenantLegacy(name)));

  const globalCollections = ["servers", "sessions", "auth_codes", "systemprompts"];
  const tenantCollections = ["players", "staffs", "staffroles", "invitations", "ticket_verifications"];

  globalCollections.forEach(c => log("INDEX", `Global index summary ${c}`, indexSummary(GLOBAL_DB, c)));
  tenants.forEach(t => {
    tenantCollections.forEach(c => log("INDEX", `Tenant index summary ${t}.${c}`, indexSummary(t, c)));
  });

  const now = new Date();
  log("TTL", "Expired sessions still present (cleanup is async)", globalDb.sessions.countDocuments({ expiresAt: { $lt: now } }));
  log("TTL", "Expired auth codes still present (cleanup is async)", globalDb.auth_codes.countDocuments({ expiresAt: { $lt: now } }));
  tenants.forEach(t => {
    const tenantDb = db.getSiblingDB(t);
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
    DRY_RUN: true,
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
    players: [
      { name: "uidx_players_minecraftUuid", key: { minecraftUuid: 1 }, options: { unique: true, sparse: true } }
    ],
    staffs: [
      { name: "uidx_staff_email", key: { email: 1 }, options: { unique: true } },
      { name: "uidx_staff_username", key: { username: 1 }, options: { unique: true } },
      { name: "sidx_staff_assignedMinecraftUuid", key: { assignedMinecraftUuid: 1 }, options: { sparse: true } }
    ],
    staffroles: [
      { name: "uidx_staff_roles_id", key: { id: 1 }, options: { unique: true } }
    ],
    invitations: [
      { name: "idx_invitations_email", key: { email: 1 }, options: {} },
      { name: "uidx_invitations_token", key: { token: 1 }, options: { unique: true } }
    ],
    ticket_verifications: [
      { name: "idx_ticket_verifications_expiresAt_ttl", key: { expiresAt: 1 }, options: { expireAfterSeconds: 0 } }
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
      print(`[${section}] ${message} :: ${tojson(payload)}`);
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

    const result = collection.updateMany(filter, update);
    log("MIGRATE", `${label}: matched=${result.matchedCount}, modified=${result.modifiedCount}`);
  }

  function flushBulk(collection, ops, label) {
    if (ops.length === 0) return;

    if (CONFIG.DRY_RUN) {
      log("DRY_RUN", `${label}: would apply ${ops.length} updateOne operation(s)`);
      return;
    }

    const result = collection.bulkWrite(ops, { ordered: false });
    log("MIGRATE", `${label}: matched=${result.matchedCount}, modified=${result.modifiedCount}`);
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
          setOps.ipAddresses = doc.ipList;
        }
        unsetOps.ipList = "";
        changed = true;
      }

      if (Array.isArray(doc.notes)) {
        let notesChanged = false;
        const normalizedNotes = doc.notes.map(note => {
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
        let punishmentsChanged = false;

        const normalizedPunishments = doc.punishments.map(p => {
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
            punishment.modifications = punishment.modifications.map(m => {
              const mod = m && typeof m === "object" ? { ...m } : m;
              if (!mod || typeof mod !== "object") return mod;
              const idRes = normalizeEmbeddedId(mod);
              if (idRes.changed) punishmentsChanged = true;
              return mod;
            });
          }

          if (Array.isArray(punishment.notes)) {
            punishment.notes = punishment.notes.map(n => {
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
              const baseDate = toDate(punishment.started) || toDate(punishment.issued);

              if (expiryDate && baseDate) {
                const durationMs = expiryDate.getTime() - baseDate.getTime();
                if (durationMs > 0) {
                  data.duration = toLong(durationMs);
                  punishmentsChanged = true;
                }
              } else {
                unresolvedExpiry = true;
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

    const strictnessOps = [];
    settings.find({ type: "aiModeration", "data.strictnessLevel": { $exists: true } }).forEach(doc => {
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
        flushBulk(settings, strictnessOps.splice(0, strictnessOps.length), `${tenantDbName}.settings aiModeration strictness normalization`);
      }
    });

    flushBulk(settings, strictnessOps, `${tenantDbName}.settings aiModeration strictness normalization`);
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

    for (const spec of specs) {
      const dups = uniqueDuplicates(coll, spec);
      if (dups.length > 0) {
        const msg = `${dbName}.${collName} has duplicate keys for unique index ${spec.name}`;
        log("ERROR", msg, dups);
        if (!CONFIG.DRY_RUN) throw new Error(msg);
      }
    }

    const existingIndexes = coll.getIndexes().filter(i => i.name !== "_id_");

    if (CONFIG.DRY_RUN) {
      log("DRY_RUN", `${dbName}.${collName} indexes to drop`, existingIndexes.map(i => i.name));
      log("DRY_RUN", `${dbName}.${collName} indexes to create`, specs.map(s => ({ name: s.name, key: s.key, options: s.options })));
      return;
    }

    for (const idx of existingIndexes) {
      coll.dropIndex(idx.name);
      log("INDEX", `Dropped ${dbName}.${collName}.${idx.name}`);
    }

    for (const spec of specs) {
      coll.createIndex(spec.key, { ...spec.options, name: spec.name });
      log("INDEX", `Created ${dbName}.${collName}.${spec.name}`);
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

    migrateGlobalSessionsAndCodes();
    migrateGlobalSystemPrompts();
    migrateGlobalServers();

    tenants.forEach(tenant => {
      migrateTenantPlayers(tenant);
      migrateTenantSettings(tenant);
      migrateTenantTicketVerifications(tenant);
    });

    rebuildCanonicalIndexes(tenants);
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
    players: ["_id_", "uidx_players_minecraftUuid"],
    staffs: ["_id_", "uidx_staff_email", "uidx_staff_username", "sidx_staff_assignedMinecraftUuid"],
    staffroles: ["_id_", "uidx_staff_roles_id"],
    invitations: ["_id_", "idx_invitations_email", "uidx_invitations_token"],
    ticket_verifications: ["_id_", "idx_ticket_verifications_expiresAt_ttl"]
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

    print(`[VERIFY] ${dbName}.${collectionName} index_names=${tojson(names)}`);
    if (missing.length) print(`[VERIFY][ERROR] missing indexes: ${tojson(missing)}`);
    if (extra.length) print(`[VERIFY][ERROR] unexpected indexes: ${tojson(extra)}`);
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
              { "notes._id": { $exists: true } },
              { "punishments._id": { $exists: true } },
              { "punishments.type_ordinal": { $exists: true } },
              { "punishments.modifications._id": { $exists: true } },
              { "punishments.notes._id": { $exists: true } },
              { "punishments.data.expires": { $exists: true } },
              { "punishments.data.expiresAt": { $exists: true } }
            ]
          })
    };

    print(`[VERIFY] ${dbName} legacy_counts=${tojson(result)}`);
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
    print(`[VERIFY][TTL] ${t}.ticket_verifications expired_docs_remaining=${d.ticket_verifications.countDocuments({ expiresAt: { $lt: now } })}`);
  });

  print("[VERIFY] Note: TTL deletion is asynchronous (monitor runs periodically). Small temporary non-zero counts are normal immediately after migration.");
})();
```

---

## Operational Notes
- Put the backend in maintenance mode during commit-mode migration if possible.
- If unique index creation reports duplicates, resolve duplicates first and rerun commit mode.
- Keep the backup archive until after at least one full business cycle of monitoring.
- Restarting backend after migration is recommended so index warmup logs clearly confirm canonical index state.
