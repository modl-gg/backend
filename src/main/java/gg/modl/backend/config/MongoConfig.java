package gg.modl.backend.config;

import com.mongodb.client.MongoClient;
import gg.modl.backend.config.converter.ArrayListToIPEntryConverter;
import gg.modl.backend.config.converter.ArrayListToNoteEntryConverter;
import gg.modl.backend.config.converter.IPEntryReadConverter;
import gg.modl.backend.config.converter.NoteEntryReadConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new IPEntryReadConverter(),
                new ArrayListToIPEntryConverter(),
                new NoteEntryReadConverter(),
                new ArrayListToNoteEntryConverter()
        ));
    }

    @Bean
    public MongoMappingContext mongoMappingContext(MongoCustomConversions mongoCustomConversions) {
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(mongoCustomConversions.getSimpleTypeHolder());
        context.setAutoIndexCreation(false);
        return context;
    }

    @Bean
    public MappingMongoConverter mappingMongoConverter(
            MongoClient mongoClient,
            MongoMappingContext mongoMappingContext,
            MongoCustomConversions mongoCustomConversions) {
        MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, "modl");
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mongoMappingContext);
        converter.setCustomConversions(mongoCustomConversions);
        converter.afterPropertiesSet();
        return converter;
    }
}
