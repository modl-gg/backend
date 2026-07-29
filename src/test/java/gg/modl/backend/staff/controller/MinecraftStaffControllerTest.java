package gg.modl.backend.staff.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.staff.service.MinecraftStaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MinecraftStaffControllerTest {
    private MinecraftStaffService staffService;
    private RoleAuthorization roleAuthorization;
    private MockMvc mockMvc;
    private Server server;

    @BeforeEach
    void setUp() {
        staffService = mock(MinecraftStaffService.class);
        roleAuthorization = mock(RoleAuthorization.class);
        server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);
        when(roleAuthorization.minecraftPerformer(any(), any()))
            .thenReturn(RoleAuthorization.PerformerAuthority.unidentified());

        mockMvc = MockMvcBuilders
            .standaloneSetup(new MinecraftStaffController(staffService, roleAuthorization))
            .defaultRequest(patch("/").requestAttr(RequestAttribute.SERVER, server))
            .build();
    }

    @Test
    void updateStaffRoleUsesMinecraftSyncService() throws Exception {
        when(staffService.updateMinecraftStaffRole(
                eq(server), eq("staff-1"), eq("Moderator"), any()))
            .thenReturn(true);

        mockMvc.perform(patch(RESTMappingV1.MINECRAFT_STAFF + "/staff-1/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"Moderator\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.success").value(true));

        verify(staffService).updateMinecraftStaffRole(
            eq(server), eq("staff-1"), eq("Moderator"), any());
    }

    @Test
    void updateStaffRoleReturnsNotFoundForMissingStaff() throws Exception {
        when(staffService.updateMinecraftStaffRole(
                eq(server), eq("missing"), eq("Moderator"), any()))
            .thenReturn(false);

        mockMvc.perform(patch(RESTMappingV1.MINECRAFT_STAFF + "/missing/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"Moderator\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }
}
