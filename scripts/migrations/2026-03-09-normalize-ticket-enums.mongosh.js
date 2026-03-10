"use strict";

if (typeof db === "undefined" || db === null) {
    throw new Error("This script must be run inside mongosh.");
}

const CONFIG = Object.freeze({
    apply: true,
    targetDatabases: new Set(), // Running on all 30 servers again
    batchSize: 500,
    anomalySampleLimit: 25,
    globalDatabaseName: "modl",
    serversCollectionName: "servers",
    ticketsCollectionName: "tickets"
});

const CATEGORY_BY_ALIAS = Object.freeze({
    bug: "bug", bug_report: "bug",
    player: "player", player_report: "player",
    chat: "chat", chat_report: "chat",
    appeal: "appeal", ban_appeal: "appeal",
    application: "application", staff: "application", staff_application: "application", apply: "application",
    support: "support", general_support: "support"
});

const BUCKET_BY_ALIAS = Object.freeze({
    bug: "bug", bug_report: "bug",
    report: "report", player: "report", player_report: "report", chat: "report", chat_report: "report",
    appeal: "appeal", ban_appeal: "appeal",
    support: "support", general_support: "support",
    staff: "staff", application: "staff", staff_application: "staff"
});

const LIFECYCLE_STATUS_BY_ALIAS = Object.freeze({
    unfinished: "unfinished", draft: "unfinished",
    open: "open", new: "open", active: "open", pending: "open", in_progress: "open", inprogress: "open",
    closed: "closed", resolved: "closed", complete: "closed", completed: "closed", done: "closed"
});

const APPEAL_WORKFLOW_BY_ALIAS = Object.freeze({
    open: "open",
    under_review: "under_review", underreview: "under_review",
    pending_player_response: "pending_player_response", pendingplayerresponse: "pending_player_response",
    approved: "approved", approve: "approved", accepted: "approved", accept: "approved",
    rejected: "rejected", reject: "rejected", denied: "rejected", deny: "rejected"
});

const PRIORITY_BY_ALIAS = Object.freeze({
    low: "low", minor: "low",
    normal: "normal", medium: "normal", default: "normal", standard: "normal",
    high: "high", urgent: "high", critical: "high", highest: "high"
});

const modlDb = db.getSiblingDB(CONFIG.globalDatabaseName);
const serversCollection = modlDb.getCollection(CONFIG.serversCollectionName);

const globalSummary = {
    tenantsDiscovered: 0, tenantsSelected: 0, tenantsProcessed: 0,
    tenantsSkippedNoDatabase: 0, tenantsSkippedDuplicateDatabase: 0,
    ticketsScanned: 0, changedDocs: 0, typeChanged: 0,
    statusChanged: 0, priorityChanged: 0, legacyPriorityRemoved: 0,
    appealWorkflowStatusChanged: 0, lockedChanged: 0, defaultsAdded: 0, anomalyCount: 0,
    dbMatched: 0, dbModified: 0, writeErrors: 0
};

async function main() {
    print("\nTicket enum normalization migration");
    print(`Mode: ${CONFIG.apply ? "APPLY" : "DRY RUN"}`);
    print(`Global database: ${CONFIG.globalDatabaseName}`);
    print(`Tenant source: ${CONFIG.globalDatabaseName}.${CONFIG.serversCollectionName}.databaseName`);

    if (CONFIG.targetDatabases && CONFIG.targetDatabases.size > 0) {
        print(`Target databases: ${Array.from(CONFIG.targetDatabases).sort().join(", ")}\n`);
    } else {
        print("Target databases: all discovered tenants\n");
    }

    try {
        const tenants = discoverTenants(serversCollection, globalSummary);
        const tenantSummaries = [];

        for (const tenant of tenants) {
            const tenantSummary = await migrateTenant(tenant);
            tenantSummaries.push(tenantSummary);
            accumulateGlobalSummary(globalSummary, tenantSummary);
            printTenantSummary(tenantSummary);
        }

        printGlobalSummary(globalSummary, tenantSummaries);
    } catch (error) {
        print(`\n[FATAL ERROR] Migration failed: ${error.message}`);
    }
}

function discoverTenants(collection, summary) {
    const servers = collection.find({}).toArray();
    const tenants = [];
    const seenDatabaseNames = new Set();

    summary.tenantsDiscovered = servers.length;

    for (const server of servers) {
        const databaseName = server.databaseName;
        if (!databaseName) { summary.tenantsSkippedNoDatabase += 1; continue; }
        if (CONFIG.targetDatabases && CONFIG.targetDatabases.size > 0 && !CONFIG.targetDatabases.has(databaseName)) continue;
        if (seenDatabaseNames.has(databaseName)) { summary.tenantsSkippedDuplicateDatabase += 1; continue; }

        seenDatabaseNames.add(databaseName);
        tenants.push({
            serverId: stringifyId(server._id),
            serverName: typeof server.serverName === "string" ? server.serverName : null,
            databaseName: databaseName
        });
    }
    summary.tenantsSelected = tenants.length;
    return tenants.sort((left, right) => left.databaseName.localeCompare(right.databaseName));
}

async function migrateTenant(tenant) {
    const tenantDb = db.getSiblingDB(tenant.databaseName);
    const ticketsCollection = tenantDb.getCollection(CONFIG.ticketsCollectionName);

    const summary = {
        serverId: tenant.serverId, serverName: tenant.serverName, databaseName: tenant.databaseName,
        ticketsScanned: 0, changedDocs: 0, typeChanged: 0,
        statusChanged: 0, priorityChanged: 0, legacyPriorityRemoved: 0, defaultsAdded: 0,
        appealWorkflowStatusChanged: 0, lockedChanged: 0, anomalyCount: 0, anomalySamples: [],
        dbMatched: 0, dbModified: 0, writeErrors: 0
    };

    const bulkOperations = [];

    // ⚠️ THE FIX: Using raw projection object for mongosh
    const cursor = ticketsCollection.find({}, {
        _id: 1, type: 1, category: 1, status: 1, priority: 1,
        appealWorkflowStatus: 1, locked: 1, reportedPlayer: 1,
        reportedPlayerUuid: 1, chatMessages: 1, data: 1,
        emailAuthEnabled: 1, hidden: 1
    });

    for await (const ticket of cursor) {
        summary.ticketsScanned += 1;
        const normalization = normalizeTicket(ticket, summary);

        if (!normalization.hasChanges) continue;
        summary.changedDocs += 1;

        if (CONFIG.apply) {
            bulkOperations.push({
                updateOne: { filter: { _id: ticket._id }, update: normalization.updateDocument }
            });

            if (bulkOperations.length >= CONFIG.batchSize) {
                const results = await flushBulkOperations(ticketsCollection, bulkOperations);
                summary.dbMatched += results.matched;
                summary.dbModified += results.modified;
                summary.writeErrors += results.errors;
            }
        }
    }

    if (CONFIG.apply && bulkOperations.length > 0) {
        const results = await flushBulkOperations(ticketsCollection, bulkOperations);
        summary.dbMatched += results.matched;
        summary.dbModified += results.modified;
        summary.writeErrors += results.errors;
    }

    return summary;
}

async function flushBulkOperations(collection, operations) {
    if (operations.length === 0) return { matched: 0, modified: 0, errors: 0 };
    const pending = operations.splice(0, operations.length);

    try {
        const result = await collection.bulkWrite(pending, { ordered: false });
        return {
            matched: result.matchedCount ?? result.nMatched ?? 0,
            modified: result.modifiedCount ?? result.nModified ?? 0,
            errors: 0
        };
    } catch (err) {
        print(`[ERROR] bulkWrite failed: ${err.message}`);
        return {
            matched: err.result?.result?.nMatched ?? 0,
            modified: err.result?.result?.nModified ?? 0,
            errors: err.writeErrors?.length || 1
        };
    }
}

// Keep core functions identical
function normalizeTicket(ticket, summary) {
    const ticketId = stringifyId(ticket._id);
    const anomalies = [];

    const typeAndCategory = deriveTypeAndCategory(ticket, anomalies);
    const isAppeal = typeAndCategory.bucket === "appeal" || typeAndCategory.category === "appeal" || isAppealSignal(ticket);
    const normalizedStatus = isAppeal ? deriveAppealStatus(ticket, anomalies) : deriveNonAppealStatus(ticket, anomalies);
    const normalizedPriority = derivePriority(ticket, anomalies);

    const nextValues = {
        type: typeAndCategory.category,
        status: normalizedStatus.status,
        priority: normalizedPriority,
        appealWorkflowStatus: isAppeal ? normalizedStatus.appealWorkflowStatus : null,
        locked: normalizedStatus.status === "closed"
    };

    const setOperations = {};
    const unsetOperations = {};

    if (nextValues.type !== null && ticket.type !== nextValues.type) { setOperations.type = nextValues.type; summary.typeChanged += 1; }
    else if (nextValues.type === null) recordLocalAnomaly(anomalies, `unable to derive canonical type from type="${safeString(ticket.type)}", category="${safeString(ticket.category)}"`);

    if (ticket.category !== undefined && ticket.category !== null) { unsetOperations.category = ""; }

    if (nextValues.status !== null && ticket.status !== nextValues.status) { setOperations.status = nextValues.status; summary.statusChanged += 1; }
    if (nextValues.priority !== null && ticket.priority !== nextValues.priority) { setOperations.priority = nextValues.priority; summary.priorityChanged += 1; }

    if (hasLegacyPriorityData(ticket)) { unsetOperations["data.priority"] = ""; summary.legacyPriorityRemoved += 1; }

    if (nextValues.appealWorkflowStatus !== null) {
        if (ticket.appealWorkflowStatus !== nextValues.appealWorkflowStatus) { setOperations.appealWorkflowStatus = nextValues.appealWorkflowStatus; summary.appealWorkflowStatusChanged += 1; }
    } else if (ticket.appealWorkflowStatus !== undefined && ticket.appealWorkflowStatus !== null) {
        unsetOperations.appealWorkflowStatus = ""; summary.appealWorkflowStatusChanged += 1;
    }

    if (ticket.locked === undefined || ticket.locked === null) { setOperations.locked = nextValues.locked; summary.lockedChanged += 1; }
    else if (ticket.locked !== nextValues.locked) { setOperations.locked = nextValues.locked; summary.lockedChanged += 1; }

    if (ticket.emailAuthEnabled === undefined || ticket.emailAuthEnabled === null) { setOperations.emailAuthEnabled = false; summary.defaultsAdded += 1; }
    if (ticket.hidden === undefined || ticket.hidden === null) { setOperations.hidden = false; summary.defaultsAdded += 1; }

    for (const anomaly of anomalies) recordSummaryAnomaly(summary, ticketId, anomaly);

    const updateDocument = {};
    if (Object.keys(setOperations).length > 0) updateDocument.$set = setOperations;
    if (Object.keys(unsetOperations).length > 0) updateDocument.$unset = unsetOperations;

    return { hasChanges: Object.keys(updateDocument).length > 0, updateDocument };
}

function deriveTypeAndCategory(ticket, anomalies) {
    const typeBucket = mapBucket(ticket.type);
    const categoryFromCategoryField = mapCategory(ticket.category);
    const categoryFromTypeField = mapCategory(ticket.type);
    const idPrefix = normalizeTicketIdPrefix(ticket._id);
    const categoryFromIdPrefix = mapCategory(idPrefix);
    const bucketFromCategoryField = mapBucket(ticket.category);
    const bucketFromIdPrefix = mapBucket(idPrefix);

    let category = categoryFromCategoryField || categoryFromTypeField || categoryFromIdPrefix;
    let bucket = category !== null ? categoryToBucket(category) : (typeBucket || bucketFromCategoryField || bucketFromIdPrefix);

    if (category === null && bucket !== null) category = inferCategoryFromBucket(ticket, bucket, anomalies);
    if (category === null && bucket === null && isAppealSignal(ticket)) { category = "appeal"; bucket = "appeal"; }
    if (category !== null && bucket === null) bucket = categoryToBucket(category);
    return { category, bucket };
}

function inferCategoryFromBucket(ticket, bucket, anomalies) {
    if (["bug", "appeal", "support"].includes(bucket)) return bucket;
    if (bucket === "staff") return "application";
    if (bucket !== "report") return null;

    const categoryFromIdPrefix = mapCategory(normalizeTicketIdPrefix(ticket._id));
    if (categoryFromIdPrefix === "player" || categoryFromIdPrefix === "chat") return categoryFromIdPrefix;
    if (Array.isArray(ticket.chatMessages) && ticket.chatMessages.length > 0) return "chat";
    if (hasText(ticket.reportedPlayer) || hasText(ticket.reportedPlayerUuid)) return "player";

    recordLocalAnomaly(anomalies, "report ticket missing category; defaulted category to player for manual review");
    return "player";
}

function deriveNonAppealStatus(ticket, anomalies) {
    const lifecycleStatus = mapLifecycleStatus(ticket.status);
    if (lifecycleStatus !== null) return { status: lifecycleStatus, appealWorkflowStatus: null };
    if (typeof ticket.locked === "boolean") return { status: ticket.locked ? "closed" : "open", appealWorkflowStatus: null };
    recordLocalAnomaly(anomalies, `unknown non-appeal status="${safeString(ticket.status)}"; defaulted lifecycle to open`);
    return { status: "open", appealWorkflowStatus: null };
}

function derivePriority(ticket, anomalies) {
    const priorityFromField = mapPriority(ticket.priority);
    const legacyPriorityValue = readLegacyPriority(ticket);
    const priorityFromLegacyData = mapPriority(legacyPriorityValue);

    if (priorityFromField !== null) return priorityFromField;
    if (priorityFromLegacyData !== null) return priorityFromLegacyData;
    return "normal";
}

function deriveAppealStatus(ticket, anomalies) {
    const workflowFromField = mapAppealWorkflow(ticket.appealWorkflowStatus);
    const workflowFromStatus = mapAppealWorkflow(ticket.status);
    const lifecycleFromStatus = mapLifecycleStatus(ticket.status);
    const workflowFromResolution = mapAppealWorkflow(readAppealResolution(ticket));

    if (workflowFromField !== null) return { status: workflowToLifecycleStatus(workflowFromField), appealWorkflowStatus: workflowFromField };
    if (workflowFromStatus !== null) return { status: workflowToLifecycleStatus(workflowFromStatus), appealWorkflowStatus: workflowFromStatus };
    if (lifecycleFromStatus === "closed") return { status: "closed", appealWorkflowStatus: workflowFromResolution };
    if (lifecycleFromStatus === "unfinished") return { status: "open", appealWorkflowStatus: "open" };
    if (lifecycleFromStatus === "open") return { status: "open", appealWorkflowStatus: "open" };
    if (workflowFromResolution !== null) return { status: workflowToLifecycleStatus(workflowFromResolution), appealWorkflowStatus: workflowFromResolution };
    if (typeof ticket.locked === "boolean") return { status: ticket.locked ? "closed" : "open", appealWorkflowStatus: ticket.locked ? null : "open" };
    return { status: "open", appealWorkflowStatus: "open" };
}

function isAppealSignal(ticket) {
    return mapCategory(ticket.category) === "appeal" || mapCategory(ticket.type) === "appeal" || mapBucket(ticket.type) === "appeal" || mapCategory(normalizeTicketIdPrefix(ticket._id)) === "appeal" || mapAppealWorkflow(ticket.appealWorkflowStatus) !== null || isAppealOnlyStatus(ticket.status);
}

function isAppealOnlyStatus(statusValue) {
    const workflow = mapAppealWorkflow(statusValue);
    return workflow !== null && workflow !== "open";
}

function mapCategory(value) { return normalizeToken(value) ? (CATEGORY_BY_ALIAS[normalizeToken(value)] || null) : null; }
function mapBucket(value) { return normalizeToken(value) ? (BUCKET_BY_ALIAS[normalizeToken(value)] || null) : null; }
function mapLifecycleStatus(value) { return normalizeToken(value) ? (LIFECYCLE_STATUS_BY_ALIAS[normalizeToken(value)] || null) : null; }
function mapAppealWorkflow(value) { return normalizeToken(value) ? (APPEAL_WORKFLOW_BY_ALIAS[normalizeToken(value)] || null) : null; }
function mapPriority(value) { return normalizeToken(value) ? (PRIORITY_BY_ALIAS[normalizeToken(value)] || null) : null; }
function workflowToLifecycleStatus(workflowStatus) { return (workflowStatus === "approved" || workflowStatus === "rejected") ? "closed" : "open"; }
function categoryToBucket(category) {
    switch (category) {
        case "bug": return "bug";
        case "player": case "chat": return "report";
        case "appeal": return "appeal";
        case "application": return "staff";
        case "support": return "support";
        default: return null;
    }
}

function readAppealResolution(ticket) { return (ticket?.data?.resolution && typeof ticket.data.resolution === "string") ? ticket.data.resolution : null; }
function readLegacyPriority(ticket) { return (ticket?.data?.priority && typeof ticket.data.priority === "string") ? ticket.data.priority : null; }
function hasLegacyPriorityData(ticket) { return ticket?.data !== null && typeof ticket?.data === "object" && Object.prototype.hasOwnProperty.call(ticket.data, "priority"); }
function normalizeTicketIdPrefix(ticketId) {
    const id = stringifyId(ticketId);
    return normalizeToken(id.indexOf("-") >= 0 ? id.slice(0, id.indexOf("-")) : id);
}
function normalizeToken(value) { return typeof value === "string" ? value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "") : ""; }

function accumulateGlobalSummary(global, tenant) {
    global.tenantsProcessed += 1;
    global.ticketsScanned += tenant.ticketsScanned;
    global.changedDocs += tenant.changedDocs;
    global.typeChanged += tenant.typeChanged;
    global.statusChanged += tenant.statusChanged;
    global.priorityChanged += tenant.priorityChanged;
    global.legacyPriorityRemoved += tenant.legacyPriorityRemoved;
    global.appealWorkflowStatusChanged += tenant.appealWorkflowStatusChanged;
    global.lockedChanged += tenant.lockedChanged;
    global.defaultsAdded += tenant.defaultsAdded;
    global.anomalyCount += tenant.anomalyCount;
    global.dbMatched += tenant.dbMatched;
    global.dbModified += tenant.dbModified;
    global.writeErrors += tenant.writeErrors;
}

function printTenantSummary(summary) {
    print(`[${summary.databaseName}] scanned=${summary.ticketsScanned}, changesFound=${summary.changedDocs}, dbModified=${summary.dbModified}`);
    if (summary.anomalySamples.length > 0) {
        print("  🚨 ANOMALY SAMPLES:");
        summary.anomalySamples.forEach(sample => print(`    - ${sample}`));
    }
}

function printGlobalSummary(summary) {
    print("\n--- GLOBAL SUMMARY ---");
    print(`  Tenants processed: ${summary.tenantsProcessed}`);
    print(`  Tickets scanned: ${summary.ticketsScanned}`);
    print(`  Documents needing changes: ${summary.changedDocs}`);
    print(`  Documents successfully modified in DB: ${summary.dbModified}`);
    print(`  Total anomalies: ${summary.anomalyCount}`);
    print("----------------------\n");
}

function recordSummaryAnomaly(summary, ticketId, message) {
    summary.anomalyCount += 1;
    if (summary.anomalySamples.length < CONFIG.anomalySampleLimit) summary.anomalySamples.push(`${ticketId}: ${message}`);
}
function recordLocalAnomaly(anomalies, message) { anomalies.push(message); }
function stringifyId(value) { return (value === undefined || value === null) ? "" : String(value); }
function safeString(value) { return (value === undefined || value === null) ? "" : String(value); }
function hasText(value) { return typeof value === "string" && value.trim().length > 0; }

main().catch(err => print(`\n[FATAL ERROR] Script execution failed: ${err.message}`));