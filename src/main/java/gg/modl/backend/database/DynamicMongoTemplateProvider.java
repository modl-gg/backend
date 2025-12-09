package gg.modl.backend.database;

import com.mongodb.client.MongoClient;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class DynamicMongoTemplateProvider {
    private static final String GLOBAL_DATABASE_NAME = "modl";

    private final MongoClient mongoClient;
    private final ConcurrentMap<String, MongoTemplate> mongoTemplateCache = new ConcurrentHashMap<>();

    public DynamicMongoTemplateProvider(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public MongoTemplate getFromDatabaseName(String databaseName) {
        return mongoTemplateCache.computeIfAbsent(databaseName, dbName -> new MongoTemplate(mongoClient, databaseName));
    }

    public MongoTemplate getGlobalDatabase() {
        return getFromDatabaseName(GLOBAL_DATABASE_NAME);
    }
}