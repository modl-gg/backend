package gg.modl.backend.database;

import com.mongodb.client.MongoClient;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.stereotype.Service;

@Service
public class DynamicMongoTemplateProvider {
    private final MongoClient mongoClient;
    private final MappingMongoConverter mongoConverter;
    private final ConcurrentMap<String, MongoTemplate> mongoTemplateCache = new ConcurrentHashMap<>();
    private static final String GLOBAL_DATABASE_NAME = "modl";

    public DynamicMongoTemplateProvider(
        MongoClient mongoClient,
        MappingMongoConverter mongoConverter
    ) {
        this.mongoClient = mongoClient;
        this.mongoConverter = mongoConverter;
    }

    public MongoTemplate getGlobalDatabase() {
        return getFromDatabaseName(GLOBAL_DATABASE_NAME);
    }

    public MongoTemplate getFromDatabaseName(String databaseName) {
        return mongoTemplateCache.computeIfAbsent(databaseName, dbName -> {
            SimpleMongoClientDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);
            return new MongoTemplate(factory, mongoConverter);
        });
    }
}
