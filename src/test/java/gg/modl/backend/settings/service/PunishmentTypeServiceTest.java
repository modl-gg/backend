package gg.modl.backend.settings.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.settings.data.DefaultPunishmentTypes;
import gg.modl.backend.settings.data.PunishmentType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PunishmentTypeServiceTest {

    @Test
    void getPunishmentTypesInitializesDefaultsWithoutRecursiveCacheUpdate() {
        SettingsRepositoryAccess settingsRepositoryAccess = mock(SettingsRepositoryAccess.class);
        ServerTimestampService serverTimestampService = mock(ServerTimestampService.class);
        when(settingsRepositoryAccess.findSettings(any(), eq("punishmentTypes"))).thenReturn(Optional.empty());

        PunishmentTypeService service = new PunishmentTypeService(
            settingsRepositoryAccess, new ObjectMapper(), serverTimestampService);
        Server server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);
        server.setId("507f1f77bcf86cd799439011");

        List<PunishmentType> types = assertDoesNotThrow(() -> service.getPunishmentTypes(server));
        assertEquals(DefaultPunishmentTypes.getAll().size(), types.size());
        assertFalse(types.isEmpty());
    }
}
