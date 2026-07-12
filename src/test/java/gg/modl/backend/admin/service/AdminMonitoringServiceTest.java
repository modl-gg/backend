package gg.modl.backend.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.admin.dto.request.CreateSystemLogRequest;
import gg.modl.backend.admin.dto.response.AdminMonitoringLogs;
import gg.modl.backend.database.mongo.repository.GlobalMongoAdminRepository;
import gg.modl.backend.database.mongo.repository.ServerMetricsRepository;
import gg.modl.backend.database.mongo.repository.SystemLogMongoRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminMonitoringServiceTest {

    @Mock
    private SystemLogMongoRepository systemLogRepository;

    @Mock
    private ServerMetricsRepository serverRepository;

    @Mock
    private GlobalMongoAdminRepository globalMongoAdminRepository;

    private AdminMonitoringService adminMonitoringService;

    @BeforeEach
    void setUp() {
        adminMonitoringService = new AdminMonitoringService(systemLogRepository, serverRepository, globalMongoAdminRepository);
    }

    @Test
    void getLogsPreservesNullFilters() {
        when(systemLogRepository.findLogs(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of());
        when(systemLogRepository.countLogs(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0L);

        AdminMonitoringLogs response = adminMonitoringService.getLogs(
            1,
            50,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "updatedAt",
            "desc"
        );

        AdminMonitoringLogs.Filters filters = response.filters();
        assertNotNull(filters);
        assertNull(filters.level());
        assertNull(filters.search());
    }

    @Test
    void createLogSavesThroughRepository() {
        SystemLog savedLog = new SystemLog();
        savedLog.setId("log-1");
        when(systemLogRepository.saveEntity(any(SystemLog.class))).thenReturn(savedLog);

        SystemLog result = adminMonitoringService.createLog(new CreateSystemLogRequest(
            "warning",
            "CPU spike",
            "monitor",
            "infra",
            "507f1f77bcf86cd799439011",
            Map.of("cpu", 95)
        ));

        ArgumentCaptor<SystemLog> logCaptor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).saveEntity(logCaptor.capture());
        assertEquals("warning", logCaptor.getValue().getLevel());
        assertNotNull(logCaptor.getValue().getTimestamp());
        assertEquals("log-1", result.getId());
    }
}
