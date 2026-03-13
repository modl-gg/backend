package gg.modl.backend.database.mongo;

import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantMongoAccess {
    private final DynamicMongoTemplateProvider mongoProvider;

    public MongoTemplate global() {
        return mongoProvider.getGlobalDatabase();
    }

    public MongoTemplate forServer(Server server) {
        return forDatabase(server.getDatabaseName());
    }

    public MongoTemplate forDatabase(String databaseName) {
        return mongoProvider.getFromDatabaseName(databaseName);
    }
}
