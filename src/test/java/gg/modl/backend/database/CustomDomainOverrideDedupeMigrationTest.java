package gg.modl.backend.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

class CustomDomainOverrideDedupeMigrationTest {
    private MongoCollection<Document> collection;
    private CustomDomainOverrideDedupeMigration migration;
    private List<Document> storedServers;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        collection = mock(MongoCollection.class);
        FindIterable<Document> iterable = mock(FindIterable.class);
        storedServers = new ArrayList<>();

        when(tenantMongoAccess.global()).thenReturn(template);
        when(template.getCollection(CollectionName.MODL_SERVERS)).thenReturn(collection);
        when(collection.find(any(Bson.class))).thenReturn(iterable);
        when(iterable.into(any())).thenAnswer(invocation -> {
            List<Document> target = invocation.getArgument(0);
            target.addAll(storedServers);
            return target;
        });

        migration = new CustomDomainOverrideDedupeMigration(tenantMongoAccess);
    }

    @Test
    void activeDocumentWinsDuplicateGroup() {
        storedServers.add(serverDoc("active-server", "dup.example.com", "ACTIVE", date(1)));
        storedServers.add(serverDoc("pending-server", "dup.example.com", "PENDING", date(2)));

        migration.run();

        assertEquals(List.of("pending-server"), clearedServerIds());
    }

    @Test
    void mostRecentlyCheckedWinsWithoutActive() {
        storedServers.add(serverDoc("older-server", "dup.example.com", "PENDING", date(1)));
        storedServers.add(serverDoc("newer-server", "dup.example.com", "PENDING", date(2)));

        migration.run();

        assertEquals(List.of("older-server"), clearedServerIds());
    }

    @Test
    void singletonGroupsUntouched() {
        storedServers.add(serverDoc("only-a", "a.example.com", "ACTIVE", date(1)));
        storedServers.add(serverDoc("only-b", "b.example.com", "PENDING", date(2)));

        migration.run();

        verify(collection, never()).updateOne(any(Bson.class), any(Bson.class));
    }

    private List<String> clearedServerIds() {
        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(filterCaptor.capture(), any(Bson.class));
        List<String> ids = new ArrayList<>();
        for (Bson filter : filterCaptor.getAllValues()) {
            ids.add(filter.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry())
                .getString("_id").getValue());
        }
        return ids;
    }

    private Document serverDoc(String id, String domain, String status, Date lastChecked) {
        return new Document("_id", id)
            .append(ServerFields.CUSTOM_DOMAIN_OVERRIDE, domain)
            .append(ServerFields.CUSTOM_DOMAIN_STATUS, status)
            .append(ServerFields.CUSTOM_DOMAIN_LAST_CHECKED, lastChecked);
    }

    private Date date(int daysAgoInverse) {
        return new Date(1_700_000_000_000L + daysAgoInverse * 86_400_000L);
    }
}
