"use strict";

if (typeof db === "undefined" || db === null) {
    throw new Error("This script must be run inside mongosh.");
}

const CONFIG = {
    targetDatabases: [],
    globalDbName: "modl",
    serversCollName: "servers"
};

function main() {
    print(`\n--- Fix Auth Index & Quick Response Fields Migration ---`);

    try {
        const serversDb = db.getSiblingDB(CONFIG.globalDbName);
        const servers = serversDb.getCollection(CONFIG.serversCollName).find({ databaseName: { $exists: true, $ne: null } }).toArray();

        const tenants = CONFIG.targetDatabases.length > 0
            ? servers.filter(s => CONFIG.targetDatabases.includes(s.databaseName))
            : servers;

        print(`Targeting ${tenants.length} tenant databases...\n`);

        let totalIndexesDropped = 0;
        let totalSettingsUpdated = 0;
        let totalPhantomCollectionsDropped = 0;

        for (const tenant of tenants) {
            const tenantDb = db.getSiblingDB(tenant.databaseName);
            const collections = tenantDb.getCollectionNames();

            const indexResult = dropBrokenAuthIndex(tenantDb, tenant.databaseName, collections);
            totalIndexesDropped += indexResult;

            const settingsResult = migrateQuickResponseFields(tenantDb, tenant.databaseName, collections);
            totalSettingsUpdated += settingsResult;

            const phantomResult = dropPhantomGlobalCollections(tenantDb, tenant.databaseName, collections);
            totalPhantomCollectionsDropped += phantomResult;
        }

        print(`\n--- GLOBAL SUMMARY ---`);
        print(`Auth indexes dropped: ${totalIndexesDropped}`);
        print(`Settings documents updated: ${totalSettingsUpdated}`);
        print(`Phantom global collections dropped: ${totalPhantomCollectionsDropped}`);
        print(`----------------------\n`);

    } catch (error) {
        print(`\n[FATAL ERROR] Script execution failed: ${error.message}`);
    }
}

function dropBrokenAuthIndex(tenantDb, dbName, collections) {
    if (!collections.includes("auth_codes")) {
        print(`[${dbName}] auth_codes collection does not exist, skipping`);
        return 0;
    }

    const coll = tenantDb.getCollection("auth_codes");
    const existing = coll.getIndexes().map(idx => idx.name);

    if (existing.includes("uidx_auth_codes_email")) {
        coll.dropIndex("uidx_auth_codes_email");
        print(`[${dbName}] Dropped broken index uidx_auth_codes_email`);
        return 1;
    }

    print(`[${dbName}] Index uidx_auth_codes_email not found, skipping`);
    return 0;
}

function migrateQuickResponseFields(tenantDb, dbName, collections) {
    if (!collections.includes("settings")) {
        print(`[${dbName}] settings collection does not exist, skipping`);
        return 0;
    }

    const coll = tenantDb.getCollection("settings");
    const docs = coll.find({
        type: "quickResponses",
        "data.categories.actions.issuePunishment": { $exists: true }
    }).toArray();

    let updated = 0;

    for (const doc of docs) {
        let changed = false;

        for (const category of (doc.data?.categories || [])) {
            for (const action of (category.actions || [])) {
                if ("issuePunishment" in action) {
                    if (!("showPunishment" in action)) {
                        action.showPunishment = action.issuePunishment;
                    }
                    delete action.issuePunishment;
                    changed = true;
                }
            }
        }

        if (changed) {
            try {
                coll.updateOne({ _id: doc._id }, { $set: { "data.categories": doc.data.categories } });
                updated++;
            } catch (e) {
                print(`[${dbName}] FAILED to update document ${doc._id}: ${e.message}`);
            }
        }
    }

    if (updated > 0) {
        print(`[${dbName}] Updated ${updated} quick response settings document(s)`);
    } else {
        print(`[${dbName}] No quick response settings needed updating`);
    }
    return updated;
}

function dropPhantomGlobalCollections(tenantDb, dbName, collections) {
    const phantomCollections = ["servers", "metric_snapshots"];
    let dropped = 0;

    for (const collName of phantomCollections) {
        if (!collections.includes(collName)) {
            continue;
        }

        const count = tenantDb.getCollection(collName).countDocuments();
        if (count === 0) {
            tenantDb.getCollection(collName).drop();
            print(`[${dbName}] Dropped empty phantom collection: ${collName}`);
            dropped++;
        } else {
            print(`[${dbName}] WARNING: ${collName} has ${count} document(s), skipping drop`);
        }
    }

    return dropped;
}

main();
