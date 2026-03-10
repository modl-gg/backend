"use strict";

if (typeof db === "undefined" || db === null) {
    throw new Error("This script must be run inside mongosh.");
}

const CONFIG = {
    apply: true,
    targetDatabases: [], // Leave empty to run on all discovered tenants
    batchSize: 500,
    globalDbName: "modl",
    serversCollName: "servers",
    ticketsCollName: "tickets"
};

// Consolidated mappings for a cleaner footprint
const ALIASES = {
    category: { bug: "bug", bug_report: "bug", player: "player", player_report: "player", chat: "chat", chat_report: "chat", appeal: "appeal", ban_appeal: "appeal", application: "application", staff: "application", staff_application: "application", apply: "application", support: "support", general_support: "support" },
    bucket: { bug: "bug", bug_report: "bug", report: "report", player: "report", player_report: "report", chat: "report", chat_report: "report", appeal: "appeal", ban_appeal: "appeal", support: "support", general_support: "support", staff: "staff", application: "staff", staff_application: "staff" },
    status: { unfinished: "unfinished", draft: "unfinished", open: "open", new: "open", active: "open", pending: "open", in_progress: "open", inprogress: "open", closed: "closed", resolved: "closed", complete: "closed", completed: "closed", done: "closed" },
    workflow: { open: "open", under_review: "under_review", underreview: "under_review", pending_player_response: "pending_player_response", pendingplayerresponse: "pending_player_response", approved: "approved", approve: "approved", accepted: "approved", accept: "approved", rejected: "rejected", reject: "rejected", denied: "rejected", deny: "rejected" },
    priority: { low: "low", minor: "low", normal: "normal", medium: "normal", default: "normal", standard: "normal", high: "high", urgent: "high", critical: "high", highest: "high" }
};

async function main() {
    print(`\n--- Ticket Normalization Migration [Mode: ${CONFIG.apply ? "APPLY" : "DRY RUN"}] ---`);

    try {
        const serversDb = db.getSiblingDB(CONFIG.globalDbName);
        const servers = serversDb.getCollection(CONFIG.serversCollName).find({ databaseName: { $exists: true, $ne: null } }).toArray();

        let globalModified = 0;
        let globalScanned = 0;

        // Filter targets if specified
        const tenants = CONFIG.targetDatabases.length > 0
            ? servers.filter(s => CONFIG.targetDatabases.includes(s.databaseName))
            : servers;

        print(`Targeting ${tenants.length} tenant databases...\n`);

        for (const tenant of tenants) {
            const result = await migrateTenant(tenant.databaseName);
            globalScanned += result.scanned;
            globalModified += result.modified;
            print(`[${tenant.databaseName}] Scanned: ${result.scanned} | Modified: ${result.modified} | Errors: ${result.errors}`);
        }

        print(`\n--- GLOBAL SUMMARY ---`);
        print(`Total Scanned: ${globalScanned}`);
        print(`Total Modified: ${globalModified}`);
        print(`----------------------\n`);

    } catch (error) {
        print(`\n[FATAL ERROR] Script execution failed: ${error.message}`);
    }
}

async function migrateTenant(databaseName) {
    const tenantDb = db.getSiblingDB(databaseName);
    const ticketsColl = tenantDb.getCollection(CONFIG.ticketsCollName);

    let scanned = 0, modified = 0, errors = 0;
    let bulkOps = [];

    const cursor = ticketsColl.find({}, {
        _id: 1, type: 1, category: 1, status: 1, priority: 1,
        appealWorkflowStatus: 1, locked: 1, reportedPlayer: 1,
        reportedPlayerUuid: 1, chatMessages: 1, data: 1,
        emailAuthEnabled: 1, hidden: 1
    });

    for await (const ticket of cursor) {
        scanned++;
        const updateDoc = buildUpdateDocument(ticket);

        if (Object.keys(updateDoc).length > 0) {
            if (CONFIG.apply) {
                bulkOps.push({ updateOne: { filter: { _id: ticket._id }, update: updateDoc } });

                if (bulkOps.length >= CONFIG.batchSize) {
                    const res = await flushBulk(ticketsColl, bulkOps);
                    modified += res.modified;
                    errors += res.errors;
                }
            } else {
                modified++; // Count as modified for Dry Run metrics
            }
        }
    }

    if (CONFIG.apply && bulkOps.length > 0) {
        const res = await flushBulk(ticketsColl, bulkOps);
        modified += res.modified;
        errors += res.errors;
    }

    return { scanned, modified, errors };
}

function buildUpdateDocument(ticket) {
    const $set = {};
    const $unset = {};

    // 1. Resolve core properties
    const normId = String(ticket._id).split('-')[0].toLowerCase();
    const isAppeal = normalize(ticket.category, 'category') === 'appeal' || normalize(ticket.type, 'bucket') === 'appeal';

    const type = normalize(ticket.category, 'category') || normalize(ticket.type, 'category') || normalize(normId, 'category') || "support";
    const priority = normalize(ticket.priority, 'priority') || normalize(ticket.data?.priority, 'priority') || "normal";

    let status = normalize(ticket.status, 'status') || "open";
    let appealWorkflow = isAppeal ? (normalize(ticket.appealWorkflowStatus, 'workflow') || "open") : null;

    if (ticket.locked === true) status = "closed";
    const locked = status === "closed";

    // 2. Build $set operations for mismatched data
    if (ticket.type !== type) $set.type = type;
    if (ticket.status !== status) $set.status = status;
    if (ticket.priority !== priority) $set.priority = priority;

    if (isAppeal) {
        if (ticket.appealWorkflowStatus !== appealWorkflow) $set.appealWorkflowStatus = appealWorkflow;
    }

    // 3. THE FIX: Strictly enforce boolean fields to prevent Spring Data null crashes
    if (ticket.locked !== locked) $set.locked = locked;
    if (ticket.emailAuthEnabled !== false && ticket.emailAuthEnabled !== true) $set.emailAuthEnabled = false;
    if (ticket.hidden !== false && ticket.hidden !== true) $set.hidden = false;

    // 4. Build $unset operations for deprecated/migrated data
    if ('category' in ticket) $unset.category = "";
    if (!isAppeal && 'appealWorkflowStatus' in ticket) $unset.appealWorkflowStatus = "";
    if (ticket.data && 'priority' in ticket.data) $unset["data.priority"] = "";

    const updateDoc = {};
    if (Object.keys($set).length > 0) updateDoc.$set = $set;
    if (Object.keys($unset).length > 0) updateDoc.$unset = $unset;

    return updateDoc;
}

async function flushBulk(collection, operations) {
    const pending = operations.splice(0, operations.length);
    try {
        const result = await collection.bulkWrite(pending, { ordered: false });
        return { modified: result.modifiedCount || 0, errors: 0 };
    } catch (err) {
        return { modified: err.result?.nModified || 0, errors: err.writeErrors?.length || 1 };
    }
}

function normalize(value, type) {
    if (typeof value !== "string") return null;
    const clean = value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
    return ALIASES[type][clean] || null;
}

main();