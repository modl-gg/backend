package gg.modl.backend.database;

import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MongoIndexWarmup implements ApplicationRunner {
    private final DynamicMongoTemplateProvider mongoProvider;

    @Override
    public void run(ApplicationArguments args) {
        List<Server> servers;
        try {
            MongoTemplate globalTemplate = mongoProvider.getGlobalDatabase();
            servers = globalTemplate.findAll(Server.class, CollectionName.MODL_SERVERS);
        } catch (Exception e) {
            log.warn("Skipping Mongo index warmup: unable to load server list from global database", e);
            return;
        }

        int warmed = 0;
        for (Server server : servers) {
            String databaseName = server.getDatabaseName();
            if (databaseName == null || databaseName.isBlank()) {
                continue;
            }

            try {
                mongoProvider.getFromDatabaseName(databaseName);
                warmed++;
            } catch (Exception e) {
                log.warn("Failed to warm up Mongo indexes for tenant database {}", databaseName, e);
            }
        }

        log.info("Mongo index warmup completed for {} tenant database(s)", warmed);
    }
}
