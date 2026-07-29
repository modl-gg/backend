"use strict";

if (typeof db === "undefined" || db === null) {
    throw new Error("This script must be run inside mongosh.");
}

const CONFIG = {
    targetDatabases: [], // Leave empty to run on all discovered tenants
    globalDbName: "modl",
    serversCollName: "servers"
};

const INDEX_DEFINITIONS = [
    {
        collection: "system_logs",
        indexes: [
            { name: "idx_system_logs_timestamp", keys: { timestamp: -1 } },
            { name: "idx_system_logs_level_timestamp", keys: { level: 1, timestamp: -1 } },
            { name: "idx_system_logs_source_timestamp", keys: { source: 1, timestamp: -1 } }
        ]
    },
    {
        collection: "security_events",
        indexes: [
            { name: "idx_security_events_timestamp", keys: { timestamp: -1 } },
            { name: "idx_security_events_severity_timestamp", keys: { severity: 1, timestamp: -1 } }
        ]
    },
    {
        collection: "metric_snapshots",
        indexes: [
            { name: "idx_metric_snapshots_date", keys: { date: -1 } }
        ]
    },
    {
        collection: "logs",
        indexes: [
            { name: "idx_logs_created", keys: { created: -1 } },
            { name: "idx_logs_source_created", keys: { source: 1, created: -1 } }
        ]
    },
    {
        collection: "migrations",
        indexes: [
            { name: "idx_migrations_status_startedAt", keys: { status: 1, startedAt: -1 } }
        ]
    },
    {
        collection: "sessions",
        indexes: [
            { name: "idx_sessions_email_expiresAt", keys: { email: 1, expiresAt: 1 } }
        ]
    }
];

async function main() {
    print(`\n--- Consolidate Indexes Migration ---`);

    try {
        const serversDb = db.getSiblingDB(CONFIG.globalDbName);
        const servers = serversDb.getCollection(CONFIG.serversCollName).find({ databaseName: { $exists: true, $ne: null } }).toArray();

        const tenants = CONFIG.targetDatabases.length > 0
            ? servers.filter(s => CONFIG.targetDatabases.includes(s.databaseName))
            : servers;

        print(`Targeting ${tenants.length} tenant databases...\n`);

        let globalCreated = 0;
        let globalSkipped = 0;

        for (const tenant of tenants) {
            const result = ensureIndexesForTenant(tenant.databaseName);
            globalCreated += result.created;
            globalSkipped += result.skipped;
            print(`[${tenant.databaseName}] Created: ${result.created} | Already existed: ${result.skipped}`);
        }

        print(`\n--- GLOBAL SUMMARY ---`);
        print(`Total indexes created: ${globalCreated}`);
        print(`Total indexes already existed: ${globalSkipped}`);
        print(`----------------------\n`);

    } catch (error) {
        print(`\n[FATAL ERROR] Script execution failed: ${error.message}`);
    }
}

function ensureIndexesForTenant(databaseName) {
    const tenantDb = db.getSiblingDB(databaseName);
    let created = 0;
    let skipped = 0;

    for (const def of INDEX_DEFINITIONS) {
        const coll = tenantDb.getCollection(def.collection);
        const existing = coll.getIndexes().map(idx => idx.name);

        for (const idx of def.indexes) {
            if (existing.includes(idx.name)) {
                skipped++;
                continue;
            }

            coll.createIndex(idx.keys, { name: idx.name });
            created++;
        }
    }

    return { created, skipped };
}

main();
