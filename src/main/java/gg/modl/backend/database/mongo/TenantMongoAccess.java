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
        String databaseName = server.getDatabaseName();
        if (databaseName == null) {
            throw new IllegalStateException(
                "Server '" + server.getServerName() + "' (id=" + server.getId() + ") has no database name configured"
            );
        }
        return forDatabase(databaseName);
    }

    public MongoTemplate forDatabase(String databaseName) {
        return mongoProvider.getFromDatabaseName(databaseName);
    }
}
