// Migration 001: Validate & Fix Punishment Data Schema
// Date: 2026-03-28
// Description: Validates and fixes punishment data map field types and legacy values
//              across all tenant databases.
//
// Usage:
//   Dry run (default):  mongosh <connection-string>/modl 001-validate-punishment-data-schema.mongosh.js
//   Apply fixes:        mongosh --eval "var DRY_RUN = false" <connection-string>/modl 001-validate-punishment-data-schema.mongosh.js
//
// Fixes applied:
//   1. duration: non-numeric → NumberLong
//   2. Boolean fields stored as non-bool → true/false
//   3. String fields stored as non-string → String()
//   4. Legacy modification types: PARDON → MANUAL_PARDON, DURATION_CHANGE → MANUAL_DURATION_CHANGE
//   5. Legacy offenseLevel without status: migrates to status field (first→low, medium/habitual as-is)

if (typeof DRY_RUN === "undefined") {
  DRY_RUN = false;
}

const BOOLEAN_FIELDS = ["altBlocking", "wipeAfterExpiry", "statWipeCompleted", "pendingAcknowledgement"];
const STRING_FIELDS = ["reason", "severity", "linkedBanId", "linkedBanParentUuid", "blockedName", "blockedSkin", "offenseLevel"];
const VALID_MOD_TYPES = ["MANUAL_PARDON", "APPEAL_ACCEPT", "SYSTEM_PARDON", "MANUAL_DURATION_CHANGE", "ROLLBACK", "REMOVE", "REVOKE"];
const LEGACY_MOD_TYPE_MAP = {
  "PARDON": "MANUAL_PARDON",
  "DURATION_CHANGE": "MANUAL_DURATION_CHANGE",
};

const globalDb = db.getSiblingDB("modl");
const servers = globalDb.getCollection("servers")
  .find({ provisioningStatus: "COMPLETED" }, { databaseName: 1, serverName: 1 })
  .toArray();

if (servers.length === 0) {
  print("No provisioned servers found. Exiting.");
  quit(1);
}

print(`\n=== Punishment Data Schema Migration ${DRY_RUN ? "(DRY RUN)" : "(APPLYING FIXES)"} ===`);
print(`Found ${servers.length} provisioned server(s)\n`);

const totals = { servers: 0, players: 0, punishments: 0, fixed: 0 };

function processTenant(tenantDb, serverName) {
  const collection = tenantDb.getCollection("players");
  const playerCount = collection.countDocuments({ "punishments.0": { $exists: true } });

  if (playerCount === 0) {
    print(`  (no players with punishments — skipping)`);
    return;
  }

  totals.servers++;
  totals.players += playerCount;
  print(`  Players with punishments: ${playerCount}`);

  let tenantFixed = 0;

  const cursor = collection.find(
    { "punishments.0": { $exists: true } },
    { punishments: 1 }
  );

  while (cursor.hasNext()) {
    const player = cursor.next();
    let dirty = false;

    for (const p of player.punishments) {
      totals.punishments++;
      const data = p.data;

      if (data) {
        // Fix 1: duration → NumberLong
        if (data.duration !== undefined && data.duration !== null) {
          const t = typeof data.duration;
          if (t === "string") {
            const parsed = parseInt(data.duration, 10);
            if (!isNaN(parsed)) {
              data.duration = NumberLong(parsed);
              dirty = true;
            }
          } else if (t === "number" && !NumberLong.prototype.isPrototypeOf(data.duration)) {
            data.duration = NumberLong(data.duration);
            dirty = true;
          }
        }

        // Fix 2: boolean fields
        for (const field of BOOLEAN_FIELDS) {
          if (data[field] !== undefined && typeof data[field] !== "boolean") {
            data[field] = !!data[field];
            dirty = true;
          }
        }

        // Fix 3: string fields
        for (const field of STRING_FIELDS) {
          if (data[field] !== undefined && data[field] !== null && typeof data[field] !== "string") {
            data[field] = String(data[field]);
            dirty = true;
          }
        }

        // Fix 5: legacy offenseLevel → status
        if (data.offenseLevel && !data.status) {
          const level = typeof data.offenseLevel === "string" ? data.offenseLevel.toLowerCase() : String(data.offenseLevel).toLowerCase();
          data.status = level === "first" ? "low" : level;
          dirty = true;
        }
      }

      // Fix 4: legacy modification types
      if (p.modifications) {
        for (const mod of p.modifications) {
          const mapped = LEGACY_MOD_TYPE_MAP[mod.type];
          if (mapped) {
            mod.type = mapped;
            dirty = true;
          }
        }
      }
    }

    if (dirty) {
      tenantFixed++;
      if (!DRY_RUN) {
        collection.updateOne(
          { _id: player._id },
          { $set: { punishments: player.punishments } }
        );
      }
    }
  }

  totals.fixed += tenantFixed;
  if (tenantFixed > 0) {
    print(`  ${DRY_RUN ? "[DRY RUN] Would fix" : "[FIXED]"} ${tenantFixed} player document(s)`);
  } else {
    print(`  [OK] No issues found`);
  }

  reportValidationCounts(collection);
}

function reportValidationCounts(collection) {
  const badDurations = collection.countDocuments({
    "punishments.data.duration": { $exists: true, $not: { $type: ["long", "int", "double"] } },
  });

  const badBooleans = BOOLEAN_FIELDS.reduce((sum, field) => {
    return sum + collection.countDocuments({
      [`punishments.data.${field}`]: { $exists: true, $not: { $type: "bool" } },
    });
  }, 0);

  const badStrings = STRING_FIELDS.reduce((sum, field) => {
    return sum + collection.countDocuments({
      [`punishments.data.${field}`]: { $exists: true, $not: { $type: "string" } },
    });
  }, 0);

  const legacyModTypes = Object.keys(LEGACY_MOD_TYPE_MAP);
  const badMods = collection.aggregate([
    { $unwind: "$punishments" },
    { $unwind: "$punishments.modifications" },
    { $match: { "punishments.modifications.type": { $in: legacyModTypes } } },
    { $count: "count" },
  ]).toArray();
  const badModCount = badMods.length > 0 ? badMods[0].count : 0;

  const remaining = badDurations + badBooleans + badStrings + badModCount;
  if (remaining > 0) {
    print(`  Remaining issues: ${remaining} (duration=${badDurations}, booleans=${badBooleans}, strings=${badStrings}, legacyMods=${badModCount})`);
  }
}

for (const server of servers) {
  const databaseName = server.databaseName;
  const serverName = server.serverName || databaseName;
  print(`\n--- ${serverName} (${databaseName}) ---`);

  if (!databaseName) {
    print(`  [ERROR] No databaseName field — skipping`);
    continue;
  }

  processTenant(db.getSiblingDB(databaseName), serverName);
}

print(`\n=== Summary ===`);
print(`Servers processed: ${totals.servers}`);
print(`Players scanned: ${totals.players}`);
print(`Punishments scanned: ${totals.punishments}`);
print(`Player documents ${DRY_RUN ? "needing fixes" : "fixed"}: ${totals.fixed}`);
if (DRY_RUN && totals.fixed > 0) {
  print(`\nRe-run with --eval "var DRY_RUN = false" to apply fixes.`);
}
print(`\n=== Done ===\n`);
