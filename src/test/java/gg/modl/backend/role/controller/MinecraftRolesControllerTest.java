package gg.modl.backend.role.controller;

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
import gg.modl.backend.role.service.RoleService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MinecraftRolesControllerTest {
    private RoleService roleService;
    private RoleAuthorization roleAuthorization;
    private MockMvc mockMvc;
    private Server server;

    @BeforeEach
    void setUp() {
        roleService = mock(RoleService.class);
        roleAuthorization = mock(RoleAuthorization.class);
        server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);
        when(roleAuthorization.minecraftPerformer(any(), any()))
            .thenReturn(RoleAuthorization.PerformerAuthority.unidentified());

        mockMvc = MockMvcBuilders
            .standaloneSetup(new MinecraftRolesController(roleService, roleAuthorization))
            .defaultRequest(patch("/").requestAttr(RequestAttribute.SERVER, server))
            .build();
    }

    @Test
    void updateRolePermissionsUsesMinecraftSyncService() throws Exception {
        when(roleService.updateRolePermissions(
                eq(server), eq("role-1"), eq(List.of("ticket.reply.all")), any()))
            .thenReturn(true);

        mockMvc.perform(patch(RESTMappingV1.MINECRAFT_ROLES + "/role-1/permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[\"ticket.reply.all\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.success").value(true));

        verify(roleService).updateRolePermissions(
            eq(server), eq("role-1"), eq(List.of("ticket.reply.all")), any());
    }

    @Test
    void updateRolePermissionsReturnsNotFoundForMissingRole() throws Exception {
        when(roleService.updateRolePermissions(
                eq(server), eq("missing"), eq(List.of("ticket.reply.all")), any()))
            .thenReturn(false);

        mockMvc.perform(patch(RESTMappingV1.MINECRAFT_ROLES + "/missing/permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[\"ticket.reply.all\"]}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }
}
