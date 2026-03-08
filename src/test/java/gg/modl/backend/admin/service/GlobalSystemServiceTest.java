package gg.modl.backend.admin.service;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.ai.service.AITicketAnalysisService;
import gg.modl.backend.database.mongo.repository.SystemConfigMongoRepository;
import gg.modl.backend.database.mongo.repository.SystemPromptMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSystemServiceTest {

    @Mock
    private SystemConfigMongoRepository systemConfigRepository;

    @Mock
    private SystemPromptMongoRepository systemPromptRepository;

    @Mock
    private AITicketAnalysisService ticketAnalysisService;

    private GlobalSystemService globalSystemService;

    @BeforeEach
    void setUp() {
        globalSystemService = new GlobalSystemService(
                systemConfigRepository,
                systemPromptRepository,
                ticketAnalysisService
        );
    }

    @Test
    void getGeneralConfigOrDefaultReturnsDefaultsWhenMissing() {
        when(systemConfigRepository.findMainConfig()).thenReturn(Optional.empty());

        SystemConfig.GeneralConfig config = globalSystemService.getGeneralConfigOrDefault();

        assertFalse(config.isMaintenanceMode());
        assertEquals("System under maintenance. Please check back later.", config.getMaintenanceMessage());
    }

    @Test
    void getGeneralConfigOrDefaultReturnsStoredConfigWhenPresent() {
        SystemConfig systemConfig = new SystemConfig();
        systemConfig.getGeneral().setMaintenanceMode(true);
        systemConfig.getGeneral().setMaintenanceMessage("Maintenance in progress.");
        when(systemConfigRepository.findMainConfig()).thenReturn(Optional.of(systemConfig));

        SystemConfig.GeneralConfig config = globalSystemService.getGeneralConfigOrDefault();

        assertTrue(config.isMaintenanceMode());
        assertEquals("Maintenance in progress.", config.getMaintenanceMessage());
    }
}
