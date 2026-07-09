"use strict";

if (typeof db === "undefined" || db === null) {
    throw new Error("This script must be run inside mongosh.");
}

const CONFIG = {
    targetDatabases: [],
    globalDbName: "modl",
    serversCollName: "servers",
    playersCollName: "players",
    logsCollName: "logs",
    dryRun: true
};

function main() {
    print("\n--- Player Document Id Normalization ---");
    print(`Mode: ${CONFIG.dryRun ? "DRY RUN" : "LIVE"}`);
    print("[INFO] Legacy ObjectId player _id values are rewritten to their own hex string form.");
    print("[INFO] The minecraftUuid field remains the logical player identity; _id stays opaque.");
    print("[INFO] logs.metadata.playerId references are rewritten to match the new string _id.");

    try {
        const transactionSupport = preflightTransactionSupport();
        if (!transactionSupport.ok) {
            print("[FATAL] Mongo session/transaction preflight failed.");
            print(`[FATAL] ${transactionSupport.reason}`);
            print("[FATAL] Use a maintenance-window collection-rebuild migration instead of continuing partially.");
            return;
        }

        const tenantDatabaseNames = discoverTenantDatabaseNames();
        print(`Discovered ${tenantDatabaseNames.length} tenant database(s).`);

        const preflightResults = tenantDatabaseNames.map(preflightTenant);

        const globalTotals = {
            scanned: 0,
            migrated: 0,
            skipped: 0,
            failed: 0,
            proposed: 0
        };

        for (const result of preflightResults) {
            globalTotals.scanned += result.scanned;
            globalTotals.skipped += result.skipped;
            globalTotals.proposed += result.candidates.length;

            print(`\n[${result.databaseName}] scanned=${result.scanned} candidates=${result.candidates.length} skipped=${result.skipped}`);

            if (result.skipReason !== null) {
                print(`[${result.databaseName}] ${result.skipReason}`);
                continue;
            }

            if (result.candidates.length === 0) {
                print(`[${result.databaseName}] No player documents need normalization.`);
                printTenantMappings(result.databaseName, [], "normalized");
                continue;
            }

            if (CONFIG.dryRun) {
                print(`[${result.databaseName}] Dry run only. No writes will be performed.`);
                printTenantMappings(result.databaseName, result.candidates, "proposed");
                continue;
            }

            const liveResult = migrateTenant(result);
            globalTotals.migrated += liveResult.migrated;
            globalTotals.failed += liveResult.failed;

            printTenantMappings(result.databaseName, liveResult.mappings, "normalized");
            if (liveResult.failures.length > 0) {
                print(`[${result.databaseName}] Failures:`);
                for (const failure of liveResult.failures) {
                    printjson(failure);
                }
            }
        }

        print("\n--- GLOBAL SUMMARY ---");
        print(`Players scanned: ${globalTotals.scanned}`);
        print(`Players normalized: ${globalTotals.migrated}`);
        print(`Players skipped: ${globalTotals.skipped}`);
        print(`Players failed: ${globalTotals.failed}`);
        if (CONFIG.dryRun) {
            print(`Players that would normalize: ${globalTotals.proposed}`);
        }
        print("----------------------\n");
    } catch (error) {
        print(`\n[FATAL ERROR] Script execution failed: ${error.stack || error.message}`);
    }
}

function discoverTenantDatabaseNames() {
    const serversDb = db.getSiblingDB(CONFIG.globalDbName);
    const discovered = serversDb
        .getCollection(CONFIG.serversCollName)
        .find({ databaseName: { $exists: true, $ne: null } }, { projection: { databaseName: 1 } })
        .toArray()
        .map(server => server.databaseName)
        .filter(databaseName => typeof databaseName === "string" && databaseName.length > 0);

    const databaseNames = CONFIG.targetDatabases.length > 0
        ? discovered.filter(databaseName => CONFIG.targetDatabases.includes(databaseName))
        : discovered;

    return Array.from(new Set(databaseNames)).sort();
}

function preflightTransactionSupport() {
    try {
        const adminDb = db.getSiblingDB("admin");
        const hello = adminDb.runCommand({ hello: 1 });
        const hasSessions = hello && hello.logicalSessionTimeoutMinutes !== undefined;
        const supportsTransactions = hasSessions && (Boolean(hello.setName) || hello.msg === "isdbgrid");

        if (!supportsTransactions) {
            return {
                ok: false,
                reason: "The current Mongo topology does not support multi-document transactions."
            };
        }

        const session = db.getMongo().startSession();
        try {
            if (typeof session.startTransaction !== "function") {
                return {
                    ok: false,
                    reason: "The current mongosh session does not expose transaction support."
                };
            }
            session.startTransaction();
            session.abortTransaction();
        } finally {
            session.endSession();
        }

        return { ok: true };
    } catch (error) {
        return {
            ok: false,
            reason: error.message
        };
    }
}

function preflightTenant(databaseName) {
    const result = {
        databaseName,
        scanned: 0,
        skipped: 0,
        candidates: [],
        skipReason: null
    };

    const existingDatabases = db.getMongo().getDBNames();
    if (!existingDatabases.includes(databaseName)) {
        result.skipReason = "Database does not exist, skipping.";
        return result;
    }

    const tenantDb = db.getSiblingDB(databaseName);
    const collections = tenantDb.getCollectionNames();
    if (!collections.includes(CONFIG.playersCollName)) {
        result.skipReason = "Players collection does not exist, skipping.";
        return result;
    }

    const players = tenantDb.getCollection(CONFIG.playersCollName);
    const playerDocs = players.find({}, { projection: { _id: 1, minecraftUuid: 1 } }).toArray();
    result.scanned = playerDocs.length;

    for (const playerDoc of playerDocs) {
        const oldId = playerDoc._id;
        if (isStringId(oldId)) {
            result.skipped++;
            continue;
        }

        result.candidates.push({
            minecraftUuid: playerDoc.minecraftUuid,
            oldId,
            newId: String(oldId)
        });
    }

    return result;
}

function migrateTenant(preflightResult) {
    const liveResult = {
        migrated: 0,
        failed: 0,
        mappings: [],
        failures: []
    };

    for (const candidate of preflightResult.candidates) {
        const outcome = migratePlayer(preflightResult.databaseName, candidate);
        if (outcome.status === "migrated") {
            liveResult.migrated++;
            liveResult.mappings.push(outcome.mapping);
            continue;
        }

        if (outcome.status === "skipped") {
            continue;
        }

        liveResult.failed++;
        liveResult.failures.push(outcome.failure);
    }

    print(`[${preflightResult.databaseName}] Normalized=${liveResult.migrated} Failed=${liveResult.failed}`);
    return liveResult;
}

function migratePlayer(databaseName, candidate) {
    const session = db.getMongo().startSession();

    try {
        session.startTransaction();

        const sessionDb = session.getDatabase(databaseName);
        const players = sessionDb.getCollection(CONFIG.playersCollName);
        const logs = sessionDb.getCollection(CONFIG.logsCollName);

        const playerDoc = players.findOne({ _id: candidate.oldId });
        if (playerDoc === null) {
            session.abortTransaction();
            return {
                status: "failed",
                failure: {
                    oldId: candidate.oldId,
                    newId: candidate.newId,
                    minecraftUuid: candidate.minecraftUuid,
                    error: "Player document not found at migration time."
                }
            };
        }

        if (isStringId(playerDoc._id)) {
            session.abortTransaction();
            return { status: "skipped" };
        }

        const normalizedPlayer = Object.assign({}, playerDoc, { _id: candidate.newId });

        const deleteResult = players.deleteOne({ _id: candidate.oldId });
        if (deleteResult.deletedCount !== 1) {
            throw new Error(`Expected to delete 1 player document, deleted ${deleteResult.deletedCount}.`);
        }

        players.insertOne(normalizedPlayer);

        const logUpdateResult = logs.updateMany(
            { "metadata.playerId": candidate.oldId },
            { $set: { "metadata.playerId": candidate.newId } }
        );

        session.commitTransaction();

        return {
            status: "migrated",
            mapping: {
                minecraftUuid: candidate.minecraftUuid,
                oldId: candidate.oldId,
                newId: candidate.newId,
                logsUpdated: logUpdateResult.modifiedCount
            }
        };
    } catch (error) {
        try {
            session.abortTransaction();
        } catch (abortError) {
            print(`[WARN] Abort after normalization failure also failed: ${abortError.message}`);
        }

        return {
            status: "failed",
            failure: {
                oldId: candidate.oldId,
                newId: candidate.newId,
                minecraftUuid: candidate.minecraftUuid,
                error: error.message
            }
        };
    } finally {
        session.endSession();
    }
}

function printTenantMappings(databaseName, mappings, label) {
    print(`[${databaseName}] ${label} mapping count=${mappings.length}`);
    for (const mapping of mappings) {
        printjson(mapping);
    }
}

function isStringId(value) {
    return typeof value === "string";
}

main();
