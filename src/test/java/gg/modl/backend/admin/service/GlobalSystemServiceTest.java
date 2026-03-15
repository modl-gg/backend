package gg.modl.backend.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.database.mongo.repository.SystemConfigMongoRepository;
import gg.modl.backend.database.mongo.repository.SystemPromptMongoRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GlobalSystemServiceTest {

    @Mock
    private SystemConfigMongoRepository systemConfigRepository;

    @Mock
    private SystemPromptMongoRepository systemPromptRepository;

    private GlobalSystemService globalSystemService;

    @BeforeEach
    void setUp() {
        globalSystemService = new GlobalSystemService(
            systemConfigRepository,
            systemPromptRepository
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
