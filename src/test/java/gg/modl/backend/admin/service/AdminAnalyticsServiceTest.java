package gg.modl.backend.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.GlobalMongoAdminRepository;
import gg.modl.backend.database.mongo.repository.MetricSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerInstanceSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock
    private ServerMongoRepository serverRepository;

    @Mock
    private MetricSnapshotMongoRepository metricSnapshotRepository;

    @Mock
    private ServerInstanceSnapshotMongoRepository serverInstanceSnapshotRepository;

    @Mock
    private GlobalMongoAdminRepository globalMongoAdminRepository;

    @Mock
    private AdminServerService adminServerService;

    private AdminAnalyticsService adminAnalyticsService;

    @BeforeEach
    void setUp() {
        adminAnalyticsService = new AdminAnalyticsService(
            serverRepository,
            metricSnapshotRepository,
            serverInstanceSnapshotRepository,
            globalMongoAdminRepository,
            adminServerService
        );
    }

    @Test
    void getHistoricalRejectsUnknownMetric() {
        Map<String, Object> response = adminAnalyticsService.getHistorical("unknown", "30d");

        assertFalse((Boolean) response.get("success"));
        assertEquals("Invalid metric type", response.get("error"));
    }

    @Test
    void getUsageReadsStorageStatsFromMongo() {
        when(serverRepository.countActiveSince(org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        when(serverRepository.countAll()).thenReturn(4L);
        when(globalMongoAdminRepository.getStorageSize()).thenReturn(2048L);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = adminAnalyticsService.getUsage();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceUtilization = (Map<String, Object>) data.get("resourceUtilization");

        assertEquals(2048L, resourceUtilization.get("storage"));
    }
}
