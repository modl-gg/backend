package gg.modl.backend.config;

import com.mongodb.client.MongoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

@Configuration
public class MongoConfig {

    @Bean
    public MongoMappingContext mongoMappingContext() {
        MongoMappingContext context = new MongoMappingContext();
        context.setAutoIndexCreation(false);
        return context;
    }

    @Bean
    public MappingMongoConverter mappingMongoConverter(
            MongoClient mongoClient,
            MongoMappingContext mongoMappingContext) {
        MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, "modl");
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mongoMappingContext);
        converter.afterPropertiesSet();
        return converter;
    }
}
