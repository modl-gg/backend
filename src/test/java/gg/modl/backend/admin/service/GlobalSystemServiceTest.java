package gg.modl.backend.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.admin.dto.request.UpdateSystemConfigRequest;
import gg.modl.backend.admin.dto.request.UpdateSystemConfigRequest.GeneralConfigRequest;
import gg.modl.backend.admin.dto.request.UpdateSystemConfigRequest.SecurityConfigRequest;
import gg.modl.backend.database.mongo.repository.SystemConfigMongoRepository;
import gg.modl.backend.database.mongo.repository.SystemPromptMongoRepository;
import java.util.List;
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

    @Test
    void updateConfigPreservesUntouchedFieldsInGeneralSection() {
        SystemConfig preSet = new SystemConfig();
        preSet.getGeneral().setMaintenanceMode(true);
        preSet.getGeneral().setTimezone("America/New_York");
        preSet.getGeneral().setAdminEmail("ops@x.com");
        when(systemConfigRepository.findMainConfig()).thenReturn(Optional.of(preSet));
        when(systemConfigRepository.saveEntity(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
            new GeneralConfigRequest("New Name", null, null, null, null, null),
            null, null, null, null, null
        );

        SystemConfig saved = globalSystemService.updateConfig(request);

        assertEquals("New Name", saved.getGeneral().getSystemName());
        assertTrue(saved.getGeneral().isMaintenanceMode());
        assertEquals("America/New_York", saved.getGeneral().getTimezone());
        assertEquals("ops@x.com", saved.getGeneral().getAdminEmail());
    }

    @Test
    void updateConfigPreservesIpWhitelistAndCorsOriginsWhenOmitted() {
        SystemConfig preSet = new SystemConfig();
        preSet.getSecurity().setIpWhitelist(List.of("10.0.0.0/8"));
        preSet.getSecurity().setCorsOrigins(List.of("https://a", "https://b"));
        when(systemConfigRepository.findMainConfig()).thenReturn(Optional.of(preSet));
        when(systemConfigRepository.saveEntity(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
            null,
            null,
            new SecurityConfigRequest(null, null, null, true, null, null, null, null),
            null, null, null
        );

        SystemConfig saved = globalSystemService.updateConfig(request);

        assertTrue(saved.getSecurity().isRequireTwoFactor());
        assertEquals(List.of("10.0.0.0/8"), saved.getSecurity().getIpWhitelist());
        assertEquals(List.of("https://a", "https://b"), saved.getSecurity().getCorsOrigins());
    }

    @Test
    void updateConfigDoesNotTouchSectionsNotProvided() {
        SystemConfig preSet = new SystemConfig();
        preSet.getSecurity().setSessionTimeout(120);
        preSet.getSecurity().setMaxLoginAttempts(9);
        preSet.getPerformance().setCacheTtl(999);
        when(systemConfigRepository.findMainConfig()).thenReturn(Optional.of(preSet));
        when(systemConfigRepository.saveEntity(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
            new GeneralConfigRequest("Only General", null, null, null, null, null),
            null, null, null, null, null
        );

        SystemConfig saved = globalSystemService.updateConfig(request);

        assertEquals(120, saved.getSecurity().getSessionTimeout());
        assertEquals(9, saved.getSecurity().getMaxLoginAttempts());
        assertEquals(999, saved.getPerformance().getCacheTtl());
    }
}
