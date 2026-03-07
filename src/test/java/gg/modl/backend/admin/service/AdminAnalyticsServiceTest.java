package gg.modl.backend.admin.service;

import com.mongodb.client.MongoDatabase;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock
    private ServerMongoRepository serverRepository;

    @Mock
    private TenantMongoAccess tenantMongoAccess;

    @Mock
    private AdminServerService adminServerService;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoDatabase mongoDatabase;

    private AdminAnalyticsService adminAnalyticsService;

    @BeforeEach
    void setUp() {
        adminAnalyticsService = new AdminAnalyticsService(serverRepository, tenantMongoAccess, adminServerService);
    }

    @Test
    void getHistoricalRejectsUnknownMetric() {
        Map<String, Object> response = adminAnalyticsService.getHistorical("unknown", "30d");

        assertFalse((Boolean) response.get("success"));
        assertEquals("Invalid metric type", response.get("error"));
    }

    @Test
    void getUsageReadsStorageStatsFromMongo() {
        when(serverRepository.count(any(Query.class))).thenReturn(4L);
        when(tenantMongoAccess.global()).thenReturn(mongoTemplate);
        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
        when(mongoDatabase.runCommand(any(Document.class))).thenReturn(new Document("storageSize", 2048L));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = adminAnalyticsService.getUsage();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceUtilization = (Map<String, Object>) data.get("resourceUtilization");

        assertEquals(2048L, resourceUtilization.get("storage"));
    }
}
