package gg.modl.backend.database;

import com.mongodb.client.MongoClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
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
    public void initializeTemplates() {
        getGlobalDatabase();

        // Warm known tenant templates at startup so canonical indexes are present
        // even before the first tenant request hits this process.
        try {
            List<String> tenantDatabaseNames = getGlobalDatabase()
                    .getCollection(CollectionName.MODL_SERVERS)
                    .distinct("databaseName", String.class)
                    .into(new ArrayList<>());

            Set<String> uniqueTenantDatabaseNames = new LinkedHashSet<>();
            for (String tenantDatabaseName : tenantDatabaseNames) {
                if (tenantDatabaseName != null && !tenantDatabaseName.isBlank() && !GLOBAL_DATABASE_NAME.equals(tenantDatabaseName)) {
                    uniqueTenantDatabaseNames.add(tenantDatabaseName);
                }
            }

            for (String tenantDatabaseName : uniqueTenantDatabaseNames) {
                ensureIndexesForDatabase(tenantDatabaseName);
            }

            log.info("Mongo template warmup complete: ensured indexes for {} tenant database(s).",
                    uniqueTenantDatabaseNames.size());
        } catch (Exception e) {
            log.warn("Failed to warm tenant Mongo templates at startup. Tenant indexes will be ensured lazily.", e);
        }
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

    private void ensureIndexesForDatabase(String databaseName) {
        SimpleMongoClientDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);
        MongoTemplate template = new MongoTemplate(factory, mongoConverter);
        mongoIndexBootstrapService.ensureIndexesForDatabase(databaseName, template);
    }
}
