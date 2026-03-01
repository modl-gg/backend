# Custom Domain Grandfathering (mongosh)

This guide sets `customDomainGrandfathered: true` for servers that are eligible under the current business rule:

- Eligible server: has at least **10** documents in its tenant `players` collection.

The script is idempotent and safe to re-run.

## Preconditions

1. Run this in staging first.
2. Take a backup/snapshot before running in production.
3. Connect to the same MongoDB deployment used by backend.

## Dry Run (no writes)

```javascript
use modl;

const servers = db.servers.find({}, {
  _id: 1,
  serverName: 1,
  customDomain: 1,
  databaseName: 1,
  plan: 1,
  customDomainGrandfathered: 1
}).toArray();

const eligible = [];

for (const server of servers) {
  const tenantDbName = server.databaseName || `server_${server.customDomain}`;
  const tenantDb = db.getSiblingDB(tenantDbName);
  const playerCount = tenantDb.players.countDocuments({});

  if (playerCount >= 10) {
    eligible.push({
      id: server._id,
      serverName: server.serverName,
      plan: server.plan,
      tenantDbName,
      playerCount,
      alreadyGrandfathered: server.customDomainGrandfathered === true
    });
  }
}

print(`Eligible servers: ${eligible.length}`);
printjson(eligible);
```

## Apply Update

```javascript
use modl;

const servers = db.servers.find({}, {
  _id: 1,
  customDomain: 1,
  databaseName: 1
}).toArray();

let matched = 0;
let modified = 0;

for (const server of servers) {
  const tenantDbName = server.databaseName || `server_${server.customDomain}`;
  const tenantDb = db.getSiblingDB(tenantDbName);
  const playerCount = tenantDb.players.countDocuments({});

  if (playerCount >= 10) {
    const result = db.servers.updateOne(
      { _id: server._id },
      { $set: { customDomainGrandfathered: true } }
    );
    matched += result.matchedCount;
    modified += result.modifiedCount;
  }
}

print(`Matched: ${matched}`);
print(`Modified: ${modified}`);
```

## Verify

```javascript
use modl;

const totalGrandfathered = db.servers.countDocuments({ customDomainGrandfathered: true });
print(`Total grandfathered servers: ${totalGrandfathered}`);

print("Sample:");
printjson(
  db.servers.find(
    { customDomainGrandfathered: true },
    { _id: 1, serverName: 1, plan: 1, customDomainGrandfathered: 1 }
  ).limit(20).toArray()
);
```

## Rollback (targeted)

```javascript
use modl;

// Replace with specific server ids if needed.
const ids = [
  ObjectId("PUT_SERVER_ID_HERE")
];

db.servers.updateMany(
  { _id: { $in: ids } },
  { $unset: { customDomainGrandfathered: "" } }
);
```
