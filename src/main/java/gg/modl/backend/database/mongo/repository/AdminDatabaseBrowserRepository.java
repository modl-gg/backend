package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.server.data.Server;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminDatabaseBrowserRepository {
    private final TenantMongoAccess tenantMongoAccess;

    public List<Document> readTable(Server server, String collectionName, int limit, int skip) {
        Query query = new Query()
            .with(Sort.by(Sort.Direction.DESC, "_id"))
            .skip(skip)
            .limit(limit);
        return tenantMongoAccess.forServer(server).find(query, Document.class, collectionName);
    }

    public long countCollection(Server server, String collectionName) {
        return tenantMongoAccess.forServer(server).count(new Query(), collectionName);
    }
}
