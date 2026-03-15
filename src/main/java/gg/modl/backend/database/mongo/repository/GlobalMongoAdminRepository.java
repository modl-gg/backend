package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GlobalMongoAdminRepository {
    private final TenantMongoAccess tenantMongoAccess;

    public void ping() {
        tenantMongoAccess.global().getDb().runCommand(new Document("ping", 1));
    }

    public long getStorageSize() {
        Document dbStats = tenantMongoAccess.global().getDb().runCommand(new Document("dbStats", 1));
        Object value = dbStats.get("storageSize");
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
