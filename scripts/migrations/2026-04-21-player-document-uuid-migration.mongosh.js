"use strict";

if (typeof db === "undefined" || db === null) {
    throw new Error("This script must be run inside mongosh.");
}

const CONFIG = {
    targetDatabases: [], // Leave empty to discover all tenant databases.
    globalDbName: "modl",
    serversCollName: "servers",
    playersCollName: "players",
    logsCollName: "logs",
    dryRun: true,
    duplicateSampleLimit: 10
};

const UUID_REGEX =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function main() {
    print("\n--- Player Document UUID Migration ---");
    print(`Mode: ${CONFIG.dryRun ? "DRY RUN" : "LIVE"}`);
    print("[INFO] Codebase verification: player document _id references were found in logs.metadata.playerId.");
    print("[INFO] Tickets use player UUID fields (creatorUuid/reportedPlayerUuid), so no ticket update is required.");

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
        const fatalErrors = preflightResults.flatMap(result =>
            result.fatalErrors.map(error => `[${result.databaseName}] ${error}`));

        if (fatalErrors.length > 0) {
            print("\n--- PREFLIGHT FAILED ---");
            for (const error of fatalErrors) {
                print(error);
            }
            print("[FATAL] Aborting before any writes.");
            print("[FATAL] Use a maintenance-window collection-rebuild migration instead of continuing partially.");
            return;
        }

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
                print(`[${result.databaseName}] No player documents need migration.`);
                printTenantMappings(result.databaseName, [], "migrated");
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

            printTenantMappings(result.databaseName, liveResult.mappings, "migrated");
            if (liveResult.failures.length > 0) {
                print(`[${result.databaseName}] Failures:`);
                for (const failure of liveResult.failures) {
                    printjson(failure);
                }
            }
        }

        print("\n--- GLOBAL SUMMARY ---");
        print(`Players scanned: ${globalTotals.scanned}`);
        print(`Players migrated: ${globalTotals.migrated}`);
        print(`Players skipped: ${globalTotals.skipped}`);
        print(`Players failed: ${globalTotals.failed}`);
        if (CONFIG.dryRun) {
            print(`Players that would migrate: ${globalTotals.proposed}`);
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
        fatalErrors: [],
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

    if (!hasUniqueMinecraftUuidIndex(players)) {
        result.fatalErrors.push("Missing unique index on players.minecraftUuid.");
        return result;
    }

    const duplicateMinecraftUuids = findDuplicateMinecraftUuids(players);
    if (duplicateMinecraftUuids.length > 0) {
        result.fatalErrors.push("Duplicate minecraftUuid documents detected.");
        result.fatalErrors.push(`Duplicate samples: ${tojsononeline(duplicateMinecraftUuids)}`);
        return result;
    }

    const playerDocs = players.find({}, { projection: { _id: 1, minecraftUuid: 1 } }).toArray();
    result.scanned = playerDocs.length;

    for (const playerDoc of playerDocs) {
        const oldId = playerDoc._id;
        if (isUuidString(oldId)) {
            result.skipped++;
            continue;
        }

        if (!isUuidString(playerDoc.minecraftUuid)) {
            result.fatalErrors.push(`Player ${tojsononeline(oldId)} is missing a valid minecraftUuid.`);
            continue;
        }

        result.candidates.push({
            minecraftUuid: playerDoc.minecraftUuid,
            oldId,
            newId: playerDoc.minecraftUuid
        });
    }

    return result;
}

function hasUniqueMinecraftUuidIndex(playersCollection) {
    return playersCollection.getIndexes().some(index => {
        const key = index.key || {};
        return key.minecraftUuid === 1 && index.unique === true;
    });
}

function findDuplicateMinecraftUuids(playersCollection) {
    return playersCollection.aggregate([
        { $match: { minecraftUuid: { $exists: true, $ne: null } } },
        { $group: { _id: "$minecraftUuid", count: { $sum: 1 } } },
        { $match: { count: { $gt: 1 } } },
        { $limit: CONFIG.duplicateSampleLimit }
    ]).toArray();
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

    print(`[${preflightResult.databaseName}] Migrated=${liveResult.migrated} Failed=${liveResult.failed}`);
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

        if (isUuidString(playerDoc._id)) {
            session.abortTransaction();
            return { status: "skipped" };
        }

        const migratedPlayer = Object.assign({}, playerDoc, { _id: candidate.newId });

        const deleteResult = players.deleteOne({ _id: candidate.oldId });
        if (deleteResult.deletedCount !== 1) {
            throw new Error(`Expected to delete 1 player document, deleted ${deleteResult.deletedCount}.`);
        }

        players.insertOne(migratedPlayer);

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
            // Abort failure is secondary to the original migration failure.
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

function isUuidString(value) {
    return typeof value === "string" && UUID_REGEX.test(value);
}

main();
