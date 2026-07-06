package gg.modl.backend.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.client.MongoClient;
import java.time.Duration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.stereotype.Service;

@Service
public class DynamicMongoTemplateProvider {
    private final MongoClient mongoClient;
    private final MappingMongoConverter mongoConverter;
    private final Cache<String, MongoTemplate> mongoTemplateCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterAccess(Duration.ofMinutes(30))
        .build();
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
        if (databaseName == null) {
            throw new IllegalArgumentException("Database name must not be null");
        }
        return mongoTemplateCache.get(databaseName, dbName -> {
            SimpleMongoClientDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, dbName);
            return new MongoTemplate(factory, mongoConverter);
        });
    }
}
