// Ticket enum normalization migration.
//
// Usage:
//   mongosh "<connection-string>" scripts/migrations/2026-03-09-normalize-ticket-enums.mongosh.js
//
// Apply changes instead of dry-run:
//   APPLY=true mongosh "<connection-string>" scripts/migrations/2026-03-09-normalize-ticket-enums.mongosh.js
//
// Optional environment variables:
//   TARGET_DATABASES=db_one,db_two
//   BATCH_SIZE=500
//   ANOMALY_SAMPLE_LIMIT=25
//
// This script:
// 1. Reads tenant database names from modl.servers.databaseName.
// 2. Iterates every tenant's tickets collection.
// 3. Normalizes ticket type/category/status/priority to canonical enum storage values.
// 4. Splits appeal workflow into appealWorkflowStatus where possible.
// 5. Reconciles locked with lifecycle status.
// 6. Prints per-tenant summaries and anomaly samples.

(function () {
    "use strict";

    if (typeof db === "undefined" || db === null) {
        throw new Error("This script must be run inside mongosh.");
    }

    const env = typeof process !== "undefined" && process && process.env ? process.env : {};
    const CONFIG = Object.freeze({
        apply: parseBooleanEnv("APPLY"),
        targetDatabases: parseCsvEnv("TARGET_DATABASES"),
        batchSize: parseIntegerEnv("BATCH_SIZE", 500),
        anomalySampleLimit: parseIntegerEnv("ANOMALY_SAMPLE_LIMIT", 25),
        globalDatabaseName: "modl",
        serversCollectionName: "servers",
        ticketsCollectionName: "tickets"
    });

    const CATEGORY_BY_ALIAS = Object.freeze({
        bug: "bug",
        bug_report: "bug",
        player: "player",
        player_report: "player",
        chat: "chat",
        chat_report: "chat",
        appeal: "appeal",
        ban_appeal: "appeal",
        application: "application",
        staff: "application",
        staff_application: "application",
        apply: "application",
        support: "support",
        general_support: "support"
    });

    const BUCKET_BY_ALIAS = Object.freeze({
        bug: "bug",
        bug_report: "bug",
        report: "report",
        player: "report",
        player_report: "report",
        chat: "report",
        chat_report: "report",
        appeal: "appeal",
        ban_appeal: "appeal",
        support: "support",
        general_support: "support",
        staff: "staff",
        application: "staff",
        staff_application: "staff"
    });

    const LIFECYCLE_STATUS_BY_ALIAS = Object.freeze({
        unfinished: "unfinished",
        draft: "unfinished",
        open: "open",
        new: "open",
        active: "open",
        pending: "open",
        in_progress: "open",
        inprogress: "open",
        closed: "closed",
        resolved: "closed",
        complete: "closed",
        completed: "closed",
        done: "closed"
    });

    const APPEAL_WORKFLOW_BY_ALIAS = Object.freeze({
        open: "open",
        under_review: "under_review",
        underreview: "under_review",
        pending_player_response: "pending_player_response",
        pendingplayerresponse: "pending_player_response",
        approved: "approved",
        approve: "approved",
        accepted: "approved",
        accept: "approved",
        rejected: "rejected",
        reject: "rejected",
        denied: "rejected",
        deny: "rejected"
    });

    const PRIORITY_BY_ALIAS = Object.freeze({
        low: "low",
        minor: "low",
        normal: "normal",
        medium: "normal",
        default: "normal",
        standard: "normal",
        high: "high",
        urgent: "high",
        critical: "high",
        highest: "high"
    });

    const mongo = db.getMongo();
    const modlDb = mongo.getDB(CONFIG.globalDatabaseName);
    const serversCollection = modlDb.getCollection(CONFIG.serversCollectionName);

    const globalSummary = {
        tenantsDiscovered: 0,
        tenantsSelected: 0,
        tenantsProcessed: 0,
        tenantsSkippedNoDatabase: 0,
        tenantsSkippedDuplicateDatabase: 0,
        ticketsScanned: 0,
        changedDocs: 0,
        typeChanged: 0,
        categoryChanged: 0,
        statusChanged: 0,
        priorityChanged: 0,
        legacyPriorityRemoved: 0,
        appealWorkflowStatusChanged: 0,
        lockedChanged: 0,
        anomalyCount: 0
    };

    print("");
    print("Ticket enum normalization migration");
    print("Mode: " + (CONFIG.apply ? "APPLY" : "DRY RUN"));
    print("Global database: " + CONFIG.globalDatabaseName);
    print("Tenant source: " + CONFIG.globalDatabaseName + "." + CONFIG.serversCollectionName + ".databaseName");
    if (CONFIG.targetDatabases.size > 0) {
        print("Target databases: " + Array.from(CONFIG.targetDatabases).sort().join(", "));
    } else {
        print("Target databases: all discovered tenants");
    }
    print("");

    const tenants = discoverTenants(serversCollection, globalSummary);
    const tenantSummaries = [];

    for (const tenant of tenants) {
        const tenantSummary = migrateTenant(mongo, tenant);
        tenantSummaries.push(tenantSummary);
        accumulateGlobalSummary(globalSummary, tenantSummary);
        printTenantSummary(tenantSummary);
    }

    printGlobalSummary(globalSummary, tenantSummaries);

    function discoverTenants(collection, summary) {
        const servers = collection.find(
            {},
            {
                projection: {
                    _id: 1,
                    serverName: 1,
                    databaseName: 1
                }
            }
        ).toArray();

        const tenants = [];
        const seenDatabaseNames = new Set();

        summary.tenantsDiscovered = servers.length;

        for (const server of servers) {
            const databaseName = normalizeDatabaseName(server.databaseName);

            if (!databaseName) {
                summary.tenantsSkippedNoDatabase += 1;
                continue;
            }

            if (CONFIG.targetDatabases.size > 0 && !CONFIG.targetDatabases.has(databaseName)) {
                continue;
            }

            if (seenDatabaseNames.has(databaseName)) {
                summary.tenantsSkippedDuplicateDatabase += 1;
                print(
                    "[skip] duplicate tenant databaseName=\"" +
                    databaseName +
                    "\" from serverId=" +
                    stringifyId(server._id)
                );
                continue;
            }

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

    function migrateTenant(mongoClient, tenant) {
        const tenantDb = mongoClient.getDB(tenant.databaseName);
        const ticketsCollection = tenantDb.getCollection(CONFIG.ticketsCollectionName);
        const summary = {
            serverId: tenant.serverId,
            serverName: tenant.serverName,
            databaseName: tenant.databaseName,
            ticketsScanned: 0,
            changedDocs: 0,
            typeChanged: 0,
            categoryChanged: 0,
            statusChanged: 0,
            priorityChanged: 0,
            legacyPriorityRemoved: 0,
            appealWorkflowStatusChanged: 0,
            lockedChanged: 0,
            anomalyCount: 0,
            anomalySamples: []
        };

        const bulkOperations = [];
        const cursor = ticketsCollection.find(
            {},
            {
                projection: {
                    _id: 1,
                    type: 1,
                    category: 1,
                    status: 1,
                    priority: 1,
                    appealWorkflowStatus: 1,
                    locked: 1,
                    reportedPlayer: 1,
                    reportedPlayerUuid: 1,
                    chatMessages: 1,
                    data: 1
                }
            }
        );

        cursor.forEach(function (ticket) {
            summary.ticketsScanned += 1;

            const normalization = normalizeTicket(ticket, summary);
            if (!normalization.hasChanges) {
                return;
            }

            summary.changedDocs += 1;
            if (CONFIG.apply) {
                bulkOperations.push({
                    updateOne: {
                        filter: { _id: ticket._id },
                        update: normalization.updateDocument
                    }
                });

                if (bulkOperations.length >= CONFIG.batchSize) {
                    flushBulkOperations(ticketsCollection, bulkOperations);
                }
            }
        });

        if (CONFIG.apply && bulkOperations.length > 0) {
            flushBulkOperations(ticketsCollection, bulkOperations);
        }

        return summary;
    }

    function normalizeTicket(ticket, summary) {
        const ticketId = stringifyId(ticket._id);
        const anomalies = [];

        const typeAndCategory = deriveTypeAndCategory(ticket, anomalies);
        const isAppeal =
            typeAndCategory.bucket === "appeal" ||
            typeAndCategory.category === "appeal" ||
            isAppealSignal(ticket);

        const normalizedStatus = isAppeal
            ? deriveAppealStatus(ticket, anomalies)
            : deriveNonAppealStatus(ticket, anomalies);
        const normalizedPriority = derivePriority(ticket, anomalies);

        const nextValues = {
            type: typeAndCategory.bucket,
            category: typeAndCategory.category,
            status: normalizedStatus.status,
            priority: normalizedPriority,
            appealWorkflowStatus: isAppeal ? normalizedStatus.appealWorkflowStatus : null,
            locked: normalizedStatus.status === "closed"
        };

        const setOperations = {};
        const unsetOperations = {};

        if (nextValues.type !== null && ticket.type !== nextValues.type) {
            setOperations.type = nextValues.type;
            summary.typeChanged += 1;
        } else if (nextValues.type === null) {
            recordLocalAnomaly(
                anomalies,
                "unable to derive canonical type from type=\"" +
                safeString(ticket.type) +
                "\", category=\"" +
                safeString(ticket.category) +
                "\", id=\"" +
                ticketId +
                "\""
            );
        }

        if (nextValues.category !== null && ticket.category !== nextValues.category) {
            setOperations.category = nextValues.category;
            summary.categoryChanged += 1;
        } else if (nextValues.category === null) {
            recordLocalAnomaly(
                anomalies,
                "unable to derive canonical category from type=\"" +
                safeString(ticket.type) +
                "\", category=\"" +
                safeString(ticket.category) +
                "\", id=\"" +
                ticketId +
                "\""
            );
        }

        if (nextValues.status !== null && ticket.status !== nextValues.status) {
            setOperations.status = nextValues.status;
            summary.statusChanged += 1;
        }

        if (nextValues.priority !== null && ticket.priority !== nextValues.priority) {
            setOperations.priority = nextValues.priority;
            summary.priorityChanged += 1;
        }

        if (hasLegacyPriorityData(ticket)) {
            unsetOperations["data.priority"] = "";
            summary.legacyPriorityRemoved += 1;
        }

        if (nextValues.appealWorkflowStatus !== null) {
            if (ticket.appealWorkflowStatus !== nextValues.appealWorkflowStatus) {
                setOperations.appealWorkflowStatus = nextValues.appealWorkflowStatus;
                summary.appealWorkflowStatusChanged += 1;
            }
        } else if (ticket.appealWorkflowStatus !== undefined && ticket.appealWorkflowStatus !== null) {
            unsetOperations.appealWorkflowStatus = "";
            summary.appealWorkflowStatusChanged += 1;
        }

        if (ticket.locked !== nextValues.locked) {
            setOperations.locked = nextValues.locked;
            summary.lockedChanged += 1;
        }

        for (const anomaly of anomalies) {
            recordSummaryAnomaly(summary, ticketId, anomaly);
        }

        const updateDocument = {};
        if (Object.keys(setOperations).length > 0) {
            updateDocument.$set = setOperations;
        }
        if (Object.keys(unsetOperations).length > 0) {
            updateDocument.$unset = unsetOperations;
        }

        return {
            hasChanges: Object.keys(updateDocument).length > 0,
            updateDocument: updateDocument
        };
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
        let bucket = null;

        if (category !== null) {
            bucket = categoryToBucket(category);
        } else {
            bucket = typeBucket || bucketFromCategoryField || bucketFromIdPrefix;
        }

        if (typeBucket !== null && bucket !== null && typeBucket !== bucket) {
            recordLocalAnomaly(
                anomalies,
                "type/category conflict; preserving category-derived bucket=\"" +
                bucket +
                "\" over type=\"" +
                safeString(ticket.type) +
                "\""
            );
        }

        if (category === null && bucket !== null) {
            const inferredCategory = inferCategoryFromBucket(ticket, bucket, anomalies);
            category = inferredCategory;
        }

        if (category === null && bucket === null && isAppealSignal(ticket)) {
            category = "appeal";
            bucket = "appeal";
            recordLocalAnomaly(
                anomalies,
                "appeal-specific status/workflow detected without canonical type/category; defaulted to appeal"
            );
        }

        if (category !== null && bucket === null) {
            bucket = categoryToBucket(category);
        }

        return {
            category: category,
            bucket: bucket
        };
    }

    function inferCategoryFromBucket(ticket, bucket, anomalies) {
        if (bucket === "bug") {
            return "bug";
        }
        if (bucket === "appeal") {
            return "appeal";
        }
        if (bucket === "support") {
            return "support";
        }
        if (bucket === "staff") {
            return "application";
        }
        if (bucket !== "report") {
            return null;
        }

        const idPrefix = normalizeTicketIdPrefix(ticket._id);
        const categoryFromIdPrefix = mapCategory(idPrefix);
        if (categoryFromIdPrefix === "player" || categoryFromIdPrefix === "chat") {
            return categoryFromIdPrefix;
        }

        if (Array.isArray(ticket.chatMessages) && ticket.chatMessages.length > 0) {
            recordLocalAnomaly(
                anomalies,
                "report ticket missing category; inferred chat from non-empty chatMessages"
            );
            return "chat";
        }

        if (hasText(ticket.reportedPlayer) || hasText(ticket.reportedPlayerUuid)) {
            recordLocalAnomaly(
                anomalies,
                "report ticket missing category; inferred player from reportedPlayer data"
            );
            return "player";
        }

        recordLocalAnomaly(
            anomalies,
            "report ticket missing category; defaulted category to player for manual review"
        );
        return "player";
    }

    function deriveNonAppealStatus(ticket, anomalies) {
        const lifecycleStatus = mapLifecycleStatus(ticket.status);
        if (lifecycleStatus !== null) {
            return {
                status: lifecycleStatus,
                appealWorkflowStatus: null
            };
        }

        if (typeof ticket.locked === "boolean") {
            recordLocalAnomaly(
                anomalies,
                "unknown non-appeal status=\"" +
                safeString(ticket.status) +
                "\"; derived lifecycle from locked=" +
                ticket.locked
            );
            return {
                status: ticket.locked ? "closed" : "open",
                appealWorkflowStatus: null
            };
        }

        recordLocalAnomaly(
            anomalies,
            "unknown non-appeal status=\"" +
            safeString(ticket.status) +
            "\"; defaulted lifecycle to open"
        );
        return {
            status: "open",
            appealWorkflowStatus: null
        };
    }

    function derivePriority(ticket, anomalies) {
        const priorityFromField = mapPriority(ticket.priority);
        const legacyPriorityValue = readLegacyPriority(ticket);
        const priorityFromLegacyData = mapPriority(legacyPriorityValue);

        if (priorityFromField !== null) {
            return priorityFromField;
        }

        if (priorityFromLegacyData !== null) {
            if (hasText(ticket.priority)) {
                recordLocalAnomaly(
                    anomalies,
                    "invalid priority=\"" +
                    safeString(ticket.priority) +
                    "\"; preserved legacy data.priority=\"" +
                    safeString(legacyPriorityValue) +
                    "\""
                );
            }
            return priorityFromLegacyData;
        }

        if (hasText(ticket.priority) || hasText(legacyPriorityValue)) {
            recordLocalAnomaly(
                anomalies,
                "unknown priority field=\"" +
                safeString(ticket.priority) +
                "\", data.priority=\"" +
                safeString(legacyPriorityValue) +
                "\"; defaulted to normal"
            );
        }

        return "normal";
    }

    function deriveAppealStatus(ticket, anomalies) {
        const workflowFromField = mapAppealWorkflow(ticket.appealWorkflowStatus);
        const workflowFromStatus = mapAppealWorkflow(ticket.status);
        const lifecycleFromStatus = mapLifecycleStatus(ticket.status);
        const workflowFromResolution = mapAppealWorkflow(readAppealResolution(ticket));

        if (workflowFromField !== null) {
            if (workflowFromStatus !== null && workflowFromStatus !== workflowFromField) {
                recordLocalAnomaly(
                    anomalies,
                    "appeal workflow/status conflict; preserving appealWorkflowStatus=\"" +
                    ticket.appealWorkflowStatus +
                    "\""
                );
            }

            return {
                status: workflowToLifecycleStatus(workflowFromField),
                appealWorkflowStatus: workflowFromField
            };
        }

        if (workflowFromStatus !== null) {
            return {
                status: workflowToLifecycleStatus(workflowFromStatus),
                appealWorkflowStatus: workflowFromStatus
            };
        }

        if (lifecycleFromStatus === "closed") {
            if (workflowFromResolution !== null) {
                return {
                    status: "closed",
                    appealWorkflowStatus: workflowFromResolution
                };
            }

            recordLocalAnomaly(
                anomalies,
                "closed/resolved appeal without resolvable data.resolution; leaving appealWorkflowStatus unset for manual review"
            );
            return {
                status: "closed",
                appealWorkflowStatus: null
            };
        }

        if (lifecycleFromStatus === "unfinished") {
            recordLocalAnomaly(
                anomalies,
                "appeal stored as unfinished; normalized lifecycle to open"
            );
            return {
                status: "open",
                appealWorkflowStatus: "open"
            };
        }

        if (lifecycleFromStatus === "open") {
            return {
                status: "open",
                appealWorkflowStatus: "open"
            };
        }

        if (workflowFromResolution !== null) {
            recordLocalAnomaly(
                anomalies,
                "appeal workflow derived from data.resolution because status was unrecognized"
            );
            return {
                status: workflowToLifecycleStatus(workflowFromResolution),
                appealWorkflowStatus: workflowFromResolution
            };
        }

        if (typeof ticket.locked === "boolean") {
            recordLocalAnomaly(
                anomalies,
                "unknown appeal status=\"" +
                safeString(ticket.status) +
                "\"; derived lifecycle from locked=" +
                ticket.locked
            );
            return {
                status: ticket.locked ? "closed" : "open",
                appealWorkflowStatus: ticket.locked ? null : "open"
            };
        }

        recordLocalAnomaly(
            anomalies,
            "unknown appeal status=\"" +
            safeString(ticket.status) +
            "\"; defaulted appeal workflow to open"
        );
        return {
            status: "open",
            appealWorkflowStatus: "open"
        };
    }

    function isAppealSignal(ticket) {
        return (
            mapCategory(ticket.category) === "appeal" ||
            mapCategory(ticket.type) === "appeal" ||
            mapBucket(ticket.type) === "appeal" ||
            mapCategory(normalizeTicketIdPrefix(ticket._id)) === "appeal" ||
            mapAppealWorkflow(ticket.appealWorkflowStatus) !== null ||
            isAppealOnlyStatus(ticket.status)
        );
    }

    function isAppealOnlyStatus(statusValue) {
        const workflow = mapAppealWorkflow(statusValue);
        return workflow !== null && workflow !== "open";
    }

    function mapCategory(value) {
        const normalized = normalizeToken(value);
        return normalized ? (CATEGORY_BY_ALIAS[normalized] || null) : null;
    }

    function mapBucket(value) {
        const normalized = normalizeToken(value);
        return normalized ? (BUCKET_BY_ALIAS[normalized] || null) : null;
    }

    function mapLifecycleStatus(value) {
        const normalized = normalizeToken(value);
        return normalized ? (LIFECYCLE_STATUS_BY_ALIAS[normalized] || null) : null;
    }

    function mapAppealWorkflow(value) {
        const normalized = normalizeToken(value);
        return normalized ? (APPEAL_WORKFLOW_BY_ALIAS[normalized] || null) : null;
    }

    function mapPriority(value) {
        const normalized = normalizeToken(value);
        return normalized ? (PRIORITY_BY_ALIAS[normalized] || null) : null;
    }

    function workflowToLifecycleStatus(workflowStatus) {
        return workflowStatus === "approved" || workflowStatus === "rejected" ? "closed" : "open";
    }

    function categoryToBucket(category) {
        switch (category) {
            case "bug":
                return "bug";
            case "player":
            case "chat":
                return "report";
            case "appeal":
                return "appeal";
            case "application":
                return "staff";
            case "support":
                return "support";
            default:
                return null;
        }
    }

    function readAppealResolution(ticket) {
        if (ticket === null || typeof ticket !== "object" || ticket.data === null || typeof ticket.data !== "object") {
            return null;
        }

        if (typeof ticket.data.resolution === "string") {
            return ticket.data.resolution;
        }

        return null;
    }

    function readLegacyPriority(ticket) {
        if (ticket === null || typeof ticket !== "object" || ticket.data === null || typeof ticket.data !== "object") {
            return null;
        }

        return typeof ticket.data.priority === "string" ? ticket.data.priority : null;
    }

    function hasLegacyPriorityData(ticket) {
        return ticket !== null
            && typeof ticket === "object"
            && ticket.data !== null
            && typeof ticket.data === "object"
            && Object.prototype.hasOwnProperty.call(ticket.data, "priority");
    }

    function normalizeTicketIdPrefix(ticketId) {
        const id = stringifyId(ticketId);
        const hyphenIndex = id.indexOf("-");
        const prefix = hyphenIndex >= 0 ? id.slice(0, hyphenIndex) : id;
        return normalizeToken(prefix);
    }

    function normalizeToken(value) {
        if (typeof value !== "string") {
            return "";
        }

        return value
            .trim()
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, "_")
            .replace(/^_+/, "")
            .replace(/_+$/, "");
    }

    function flushBulkOperations(collection, operations) {
        if (operations.length === 0) {
            return;
        }

        const pending = operations.splice(0, operations.length);
        collection.bulkWrite(pending, { ordered: false });
    }

    function accumulateGlobalSummary(global, tenant) {
        global.tenantsProcessed += 1;
        global.ticketsScanned += tenant.ticketsScanned;
        global.changedDocs += tenant.changedDocs;
        global.typeChanged += tenant.typeChanged;
        global.categoryChanged += tenant.categoryChanged;
        global.statusChanged += tenant.statusChanged;
        global.priorityChanged += tenant.priorityChanged;
        global.legacyPriorityRemoved += tenant.legacyPriorityRemoved;
        global.appealWorkflowStatusChanged += tenant.appealWorkflowStatusChanged;
        global.lockedChanged += tenant.lockedChanged;
        global.anomalyCount += tenant.anomalyCount;
    }

    function printTenantSummary(summary) {
        print(
            "[" +
            summary.databaseName +
            "] scanned=" +
            summary.ticketsScanned +
            ", changed=" +
            summary.changedDocs +
            ", type=" +
            summary.typeChanged +
            ", category=" +
            summary.categoryChanged +
            ", status=" +
            summary.statusChanged +
            ", priority=" +
            summary.priorityChanged +
            ", legacyPriorityRemoved=" +
            summary.legacyPriorityRemoved +
            ", appealWorkflowStatus=" +
            summary.appealWorkflowStatusChanged +
            ", locked=" +
            summary.lockedChanged +
            ", anomalies=" +
            summary.anomalyCount
        );

        if (summary.serverName !== null) {
            print(
                "  serverName=" +
                summary.serverName +
                ", serverId=" +
                summary.serverId
            );
        }

        if (summary.anomalySamples.length > 0) {
            print("  anomaly samples:");
            for (const sample of summary.anomalySamples) {
                print("    - " + sample);
            }
        }

        print("");
    }

    function printGlobalSummary(summary, tenantSummaries) {
        print("Global summary");
        print("  tenants discovered: " + summary.tenantsDiscovered);
        print("  tenants selected: " + summary.tenantsSelected);
        print("  tenants processed: " + summary.tenantsProcessed);
        print("  tenants skipped with no databaseName: " + summary.tenantsSkippedNoDatabase);
        print("  tenants skipped as duplicates: " + summary.tenantsSkippedDuplicateDatabase);
        print("  tickets scanned: " + summary.ticketsScanned);
        print("  changed documents: " + summary.changedDocs);
        print("  type changes: " + summary.typeChanged);
        print("  category changes: " + summary.categoryChanged);
        print("  status changes: " + summary.statusChanged);
        print("  priority changes: " + summary.priorityChanged);
        print("  legacy data.priority removals: " + summary.legacyPriorityRemoved);
        print("  appealWorkflowStatus changes: " + summary.appealWorkflowStatusChanged);
        print("  locked changes: " + summary.lockedChanged);
        print("  anomalies: " + summary.anomalyCount);

        const anomalousTenants = tenantSummaries.filter(function (tenant) {
            return tenant.anomalyCount > 0;
        }).length;
        print("  tenants with anomalies: " + anomalousTenants);
        print("");
    }

    function recordSummaryAnomaly(summary, ticketId, message) {
        summary.anomalyCount += 1;
        if (summary.anomalySamples.length < CONFIG.anomalySampleLimit) {
            summary.anomalySamples.push(ticketId + ": " + message);
        }
    }

    function recordLocalAnomaly(anomalies, message) {
        anomalies.push(message);
    }

    function stringifyId(value) {
        if (value === undefined || value === null) {
            return "";
        }
        return String(value);
    }

    function safeString(value) {
        if (value === undefined || value === null) {
            return "";
        }
        return String(value);
    }

    function hasText(value) {
        return typeof value === "string" && value.trim().length > 0;
    }

    function normalizeDatabaseName(value) {
        if (typeof value !== "string") {
            return "";
        }
        return value.trim();
    }

    function parseBooleanEnv(name) {
        const value = env[name];
        return typeof value === "string" && /^(1|true|yes)$/i.test(value.trim());
    }

    function parseIntegerEnv(name, fallback) {
        const value = env[name];
        if (typeof value !== "string" || value.trim() === "") {
            return fallback;
        }

        const parsed = Number.parseInt(value, 10);
        return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
    }

    function parseCsvEnv(name) {
        const value = env[name];
        if (typeof value !== "string" || value.trim() === "") {
            return new Set();
        }

        const parts = value
            .split(",")
            .map(function (part) {
                return part.trim();
            })
            .filter(function (part) {
                return part.length > 0;
            });

        return new Set(parts);
    }
})();
