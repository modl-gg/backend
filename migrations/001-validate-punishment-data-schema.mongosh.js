// Migration 001: Validate Punishment Data Schema
// Date: 2026-03-28
// Description: Validates that punishment data map fields have expected types.
//              This is a READ-ONLY validation script. No data is modified.
//              Run against each tenant database to verify data integrity before
//              the backend upgrade that introduces typed PunishmentData accessors.
//
// Usage: mongosh <connection-string>/<database-name> 001-validate-punishment-data-schema.mongosh.js
//
// Known keys in punishments[].data:
//   duration          - Long (ms), may also be Integer/Double in legacy data
//   status            - String: "Unstarted", "Pardoned", or offender level
//   severity          - String: "regular", "minor", "moderate", "major"
//   reason            - String
//   altBlocking       - Boolean
//   wipeAfterExpiry   - Boolean
//   statWipeCompleted - Boolean
//   linkedBanId       - String (punishment ID)
//   linkedBanParentUuid - String (minecraft UUID)
//   blockedName       - String (username at punishment time)
//   blockedSkin       - String (skin hash)
//   offenseLevel      - String (legacy: "first", "medium", "habitual")
//   pendingAcknowledgement - Boolean

const collection = db.getCollection("players");
const totalPlayers = collection.countDocuments({ "punishments.0": { $exists: true } });

print(`\n=== Punishment Data Schema Validation ===`);
print(`Players with punishments: ${totalPlayers}\n`);

// Validate duration field types
const badDurations = collection.countDocuments({
  "punishments.data.duration": { $exists: true },
  "punishments.data.duration": { $not: { $type: ["long", "int", "double"] } }
});
print(`[${badDurations === 0 ? 'OK' : 'WARN'}] duration field type (non-numeric): ${badDurations}`);

// Validate status field values
const validStatuses = ["Unstarted", "Pardoned", "low", "medium", "habitual", "Low", "Medium", "Habitual"];
const badStatuses = collection.countDocuments({
  "punishments.data.status": { $exists: true, $nin: validStatuses }
});
print(`[${badStatuses === 0 ? 'OK' : 'WARN'}] status field unexpected values: ${badStatuses}`);

// Validate boolean fields
const booleanFields = ["altBlocking", "wipeAfterExpiry", "statWipeCompleted", "pendingAcknowledgement"];
for (const field of booleanFields) {
  const path = `punishments.data.${field}`;
  const badCount = collection.countDocuments({
    [path]: { $exists: true, $not: { $type: "bool" } }
  });
  print(`[${badCount === 0 ? 'OK' : 'WARN'}] ${field} non-boolean: ${badCount}`);
}

// Validate string fields
const stringFields = ["reason", "severity", "linkedBanId", "linkedBanParentUuid", "blockedName", "blockedSkin", "offenseLevel"];
for (const field of stringFields) {
  const path = `punishments.data.${field}`;
  const badCount = collection.countDocuments({
    [path]: { $exists: true, $not: { $type: "string" } }
  });
  print(`[${badCount === 0 ? 'OK' : 'WARN'}] ${field} non-string: ${badCount}`);
}

// Validate modification types
const validModTypes = ["MANUAL_PARDON", "APPEAL_ACCEPT", "SYSTEM_PARDON", "MANUAL_DURATION_CHANGE", "ROLLBACK"];
const badModTypes = collection.aggregate([
  { $unwind: "$punishments" },
  { $unwind: "$punishments.modifications" },
  { $match: { "punishments.modifications.type": { $nin: validModTypes } } },
  { $count: "count" }
]).toArray();
const badModCount = badModTypes.length > 0 ? badModTypes[0].count : 0;
print(`[${badModCount === 0 ? 'OK' : 'WARN'}] modification types unexpected: ${badModCount}`);

// Summary stats
const punishmentStats = collection.aggregate([
  { $unwind: "$punishments" },
  { $group: {
    _id: null,
    total: { $sum: 1 },
    withData: { $sum: { $cond: [{ $ne: ["$punishments.data", null] }, 1, 0] } },
    withDuration: { $sum: { $cond: [{ $ifNull: ["$punishments.data.duration", false] }, 1, 0] } },
    withStatus: { $sum: { $cond: [{ $ifNull: ["$punishments.data.status", false] }, 1, 0] } },
    withReason: { $sum: { $cond: [{ $ifNull: ["$punishments.data.reason", false] }, 1, 0] } },
    withAltBlocking: { $sum: { $cond: [{ $eq: ["$punishments.data.altBlocking", true] }, 1, 0] } },
    withLinkedBan: { $sum: { $cond: [{ $ifNull: ["$punishments.data.linkedBanId", false] }, 1, 0] } }
  }}
]).toArray();

if (punishmentStats.length > 0) {
  const s = punishmentStats[0];
  print(`\n--- Stats ---`);
  print(`Total punishments: ${s.total}`);
  print(`With data map: ${s.withData}`);
  print(`With duration: ${s.withDuration}`);
  print(`With status: ${s.withStatus}`);
  print(`With reason: ${s.withReason}`);
  print(`With altBlocking=true: ${s.withAltBlocking}`);
  print(`With linkedBanId: ${s.withLinkedBan}`);
}

print(`\n=== Validation Complete ===\n`);
