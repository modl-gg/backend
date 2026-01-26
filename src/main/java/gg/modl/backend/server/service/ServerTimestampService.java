package gg.modl.backend.server.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class ServerTimestampService {
    private final DynamicMongoTemplateProvider mongoProvider;

    public void updateStaffPermissionsTimestamp(@NotNull Server server) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();
        Query query = Query.query(Criteria.where("_id").is(server.getId()));
        Update update = new Update()
                .set("staffPermissionsUpdatedAt", new Date())
                .set("updatedAt", new Date());
        db.updateFirst(query, update, Server.class, CollectionName.MODL_SERVERS);
    }

    public void updatePunishmentTypesTimestamp(@NotNull Server server) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();
        Query query = Query.query(Criteria.where("_id").is(server.getId()));
        Update update = new Update()
                .set("punishmentTypesUpdatedAt", new Date())
                .set("updatedAt", new Date());
        db.updateFirst(query, update, Server.class, CollectionName.MODL_SERVERS);
    }
}
