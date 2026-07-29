package gg.modl.backend.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.CustomDomainStatus;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomDomainOverrideDedupeMigration {
    private static final String ID_FIELD = "_id";
    private static final Comparator<Document> KEEPER_PREFERENCE = Comparator
        .comparing(CustomDomainOverrideDedupeMigration::isActive)
        .thenComparing(CustomDomainOverrideDedupeMigration::lastChecked,
            Comparator.nullsFirst(Comparator.naturalOrder()))
        .reversed();

    private final TenantMongoAccess tenantMongoAccess;

    @PostConstruct
    public void run() {
        dedupe(tenantMongoAccess.global());
    }

    private void dedupe(MongoTemplate template) {
        MongoCollection<Document> servers = template.getCollection(CollectionName.MODL_SERVERS);
        List<Document> withOverride = servers
            .find(Filters.type(ServerFields.CUSTOM_DOMAIN_OVERRIDE, "string"))
            .into(new ArrayList<>());

        Map<String, List<Document>> byDomain = new LinkedHashMap<>();
        for (Document server : withOverride) {
            byDomain.computeIfAbsent(server.getString(ServerFields.CUSTOM_DOMAIN_OVERRIDE), key -> new ArrayList<>())
                .add(server);
        }

        long clearedLosers = 0;
        for (List<Document> group : byDomain.values()) {
            if (group.size() < 2) {
                continue;
            }
            group.sort(KEEPER_PREFERENCE);
            for (Document loser : group.subList(1, group.size())) {
                clearOverride(servers, loser.get(ID_FIELD));
                clearedLosers++;
            }
        }

        if (clearedLosers > 0) {
            log.warn("Cleared duplicate customDomainOverride values on {} losing server documents", clearedLosers);
        }
    }

    private void clearOverride(MongoCollection<Document> servers, Object serverId) {
        servers.updateOne(Filters.eq(ID_FIELD, serverId), Updates.combine(
            Updates.unset(ServerFields.CUSTOM_DOMAIN_OVERRIDE),
            Updates.unset(ServerFields.CUSTOM_DOMAIN_STATUS),
            Updates.unset(ServerFields.CUSTOM_DOMAIN_CLOUDFLARE_ID),
            Updates.unset(ServerFields.CUSTOM_DOMAIN_LAST_CHECKED),
            Updates.unset(ServerFields.CUSTOM_DOMAIN_ERROR),
            Updates.set(ServerFields.UPDATED_AT, new Date())
        ));
    }

    private static boolean isActive(Document server) {
        return CustomDomainStatus.ACTIVE.name().equals(server.getString(ServerFields.CUSTOM_DOMAIN_STATUS));
    }

    private static Date lastChecked(Document server) {
        return server.get(ServerFields.CUSTOM_DOMAIN_LAST_CHECKED) instanceof Date date ? date : null;
    }
}
