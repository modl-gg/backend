package gg.modl.backend.database;

import com.mongodb.client.MongoClient;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
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
        return mongoTemplateCache.computeIfAbsent(databaseName, dbName -> {
            MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);

            return new MongoTemplate(factory, getDefaultMongoConverter(factory));
        });
    }

    public MongoTemplate getGlobalDatabase() {
        return getFromDatabaseName(GLOBAL_DATABASE_NAME);
    }

    private static MongoConverter getDefaultMongoConverter(MongoDatabaseFactory factory) {
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MongoCustomConversions conversions = new MongoCustomConversions(CustomMongoConverters.CONVERTERS);

        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.afterPropertiesSet();

        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
        converter.setCustomConversions(conversions);
        converter.setCodecRegistryProvider(factory);
        converter.afterPropertiesSet();

        return converter;
    }
}