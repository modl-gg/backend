package gg.modl.backend.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.modl.backend.infrastructure.exception.GlobalExceptionHandler;
import gg.modl.backend.infrastructure.proto.ProtoBinaryHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtoJsonHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtoValidationAdvice;
import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.infrastructure.proto.ProtobufMediaTypes;
import gg.modl.backend.infrastructure.rest.RESTMappingV3;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.player.controller.MinecraftSyncV3Controller;
import gg.modl.backend.player.service.MinecraftSyncService;
import gg.modl.backend.player.dto.response.SimplePunishmentView;
import gg.modl.backend.player.dto.response.SyncDataView;
import gg.modl.backend.player.service.SyncProtoFactory;
import gg.modl.backend.player.dto.response.SyncPunishmentEntry;
import gg.modl.backend.player.dto.response.SyncResult;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.proto.modl.v1.SyncOnlinePlayer;
import gg.modl.proto.modl.v1.SyncRequest;
import gg.modl.proto.modl.v1.SyncResponse;
import gg.modl.proto.modl.v1.SyncServerStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MinecraftSyncV3ControllerTest {
    private MinecraftSyncService minecraftSyncService;
    private MockMvc mockMvc;
    private Server server;

    @BeforeEach
    void setUp() {
        minecraftSyncService = mock(MinecraftSyncService.class);
        server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);

        mockMvc = MockMvcBuilders.standaloneSetup(new MinecraftSyncV3Controller(minecraftSyncService, new SyncProtoFactory()))
            .setControllerAdvice(new GlobalExceptionHandler(new ProtobufErrorResponseWriter()), new ProtoValidationAdvice())
            .setMessageConverters(new ProtoBinaryHttpMessageConverter(), new ProtoJsonHttpMessageConverter())
            .defaultRequest(post("/")
                .requestAttr(RequestAttribute.SERVER, server)
                .header("X-Forwarded-For", "203.0.113.20"))
            .build();
    }

    @Test
    void v3SyncAcceptsBinaryRequestAndReturnsBinaryResponse() throws Exception {
        when(minecraftSyncService.sync(
            same(server),
            eq("2026-05-12T00:00:00Z"),
            any(),
            eq("hub"),
            any(),
            any(),
            any(),
            eq("127.0.0.1")
        )).thenReturn(new SyncResult(
            "2026-05-12T00:00:01Z",
            new SyncDataView(
                List.of(new SyncPunishmentEntry(
                    "11111111-2222-3333-4444-555555555555",
                    "Byteful",
                    new SimplePunishmentView(
                        "punishment-1",
                        "Ban",
                        null,
                        0,
                        2,
                        true,
                        null,
                        "Rule violation",
                        null,
                        0L,
                        null,
                        List.of()
                    ))),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                1_700_000_000_000L,
                1_700_000_001_000L,
                List.of(),
                null
            )
        ));

        SyncRequest request = SyncRequest.newBuilder()
            .setLastSyncTimestamp("2026-05-12T00:00:00Z")
            .setServerName("hub")
            .addOnlinePlayers(SyncOnlinePlayer.newBuilder()
                .setUuid("11111111-2222-3333-4444-555555555555")
                .setUsername("Byteful")
                .setIpAddress("198.51.100.15")
                .build())
            .setServerStatus(SyncServerStatus.newBuilder()
                .setOnlinePlayerCount(1)
                .setMaxPlayers(200)
                .setServerVersion("1.21.8")
                .setPlatformType("paper")
                .setPluginVersion("2.0.0")
                .build())
            .build();

        MvcResult result = mockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/players/sync")
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content(request.toByteArray()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        SyncResponse response = SyncResponse.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals("2026-05-12T00:00:01Z", response.getTimestamp());
        assertEquals("11111111-2222-3333-4444-555555555555", response.getData().getPendingPunishments(0).getMinecraftUuid());
        assertEquals("Ban", response.getData().getPendingPunishments(0).getPunishment().getType());
        assertEquals(1_700_000_000_000L, response.getData().getStaffPermissionsUpdatedAt());
        assertEquals(1_700_000_001_000L, response.getData().getPunishmentTypesUpdatedAt());

        verify(minecraftSyncService).sync(
            same(server),
            eq("2026-05-12T00:00:00Z"),
            any(),
            eq("hub"),
            any(),
            any(),
            any(),
            eq("127.0.0.1")
        );
    }
}
