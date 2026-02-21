package gg.modl.backend.database;

import com.mongodb.client.MongoClient;
import jakarta.annotation.PostConstruct;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class DynamicMongoTemplateProvider {
    private static final String GLOBAL_DATABASE_NAME = "modl";

    private final MongoClient mongoClient;
    private final MappingMongoConverter mongoConverter;
    private final MongoIndexBootstrapService mongoIndexBootstrapService;
    private final ConcurrentMap<String, MongoTemplate> mongoTemplateCache = new ConcurrentHashMap<>();

    public DynamicMongoTemplateProvider(
            MongoClient mongoClient,
            MappingMongoConverter mongoConverter,
            MongoIndexBootstrapService mongoIndexBootstrapService
    ) {
        this.mongoClient = mongoClient;
        this.mongoConverter = mongoConverter;
        this.mongoIndexBootstrapService = mongoIndexBootstrapService;
    }

    @PostConstruct
    public void initializeGlobalTemplate() {
        getGlobalDatabase();
    }

    public MongoTemplate getFromDatabaseName(String databaseName) {
        return mongoTemplateCache.computeIfAbsent(databaseName, dbName -> {
            SimpleMongoClientDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);
            MongoTemplate template = new MongoTemplate(factory, mongoConverter);
            mongoIndexBootstrapService.ensureIndexesForDatabase(dbName, template);
            return template;
        });
    }

    public MongoTemplate getGlobalDatabase() {
        return getFromDatabaseName(GLOBAL_DATABASE_NAME);
    }
}
