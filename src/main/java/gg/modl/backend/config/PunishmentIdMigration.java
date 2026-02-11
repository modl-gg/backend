package gg.modl.backend.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.CollectionName;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PunishmentIdMigration {

    private static final Logger log = LoggerFactory.getLogger(PunishmentIdMigration.class);

    private final MongoClient mongoClient;

    public PunishmentIdMigration(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @PostConstruct
    public void migrate() {
        MongoDatabase db = mongoClient.getDatabase("modl");
        MongoCollection<Document> collection = db.getCollection(CollectionName.PLAYERS);

        Bson filter = Filters.elemMatch("punishments",
                Filters.and(
                        Filters.exists("_id"),
                        Filters.not(Filters.exists("id"))
                )
        );

        List<Document> pipeline = List.of(
                new Document("$set", new Document("punishments",
                        new Document("$map", new Document()
                                .append("input", "$punishments")
                                .append("as", "p")
                                .append("in", new Document("$mergeObjects", List.of(
                                        "$$p",
                                        new Document("id", new Document("$ifNull", List.of("$$p.id", new Document("$toString", "$$p._id"))))
                                )))
                        )
                ))
        );

        UpdateResult result = collection.updateMany(filter, pipeline);
        if (result.getModifiedCount() > 0) {
            log.info("Migrated punishment IDs for {} player documents", result.getModifiedCount());
        }
    }
}
