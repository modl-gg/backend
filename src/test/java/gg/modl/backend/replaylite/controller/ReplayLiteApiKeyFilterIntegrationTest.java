package gg.modl.backend.replaylite.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.modl.backend.infrastructure.config.StagingEnvironment;
import gg.modl.backend.infrastructure.filter.ApiKeyFilter;
import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestHeader;
import gg.modl.backend.replaylite.dto.ReplayLiteUploadInitResponse;
import gg.modl.backend.replaylite.service.ReplayLiteService;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReplayLiteApiKeyFilterIntegrationTest {
    private static final String VALID_API_KEY = "valid-api-key";
    private static final String INVALID_API_KEY = "invalid-api-key";

    private ServerService serverService;
    private ReplayLiteService replayLiteService;
    private MockMvc mockMvc;
    private Server server;

    @BeforeEach
    void setUp() {
        serverService = mock(ServerService.class);
        replayLiteService = mock(ReplayLiteService.class);
        server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);
        server.setId("507f1f77bcf86cd799439011");

        ApiKeyFilter apiKeyFilter = new ApiKeyFilter(serverService, new ProtobufErrorResponseWriter(), mock(StagingEnvironment.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new ReplayLiteController(replayLiteService))
            .addFilters(apiKeyFilter)
            .build();
    }

    @Test
    void replayLiteUploadWithoutApiKeyReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(RESTMappingV1.REPLAY_LITE_REPLAYS + "/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadBody()))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(serverService, replayLiteService);
    }

    @Test
    void replayLiteUploadWithInvalidApiKeyReturnsUnauthorized() throws Exception {
        when(serverService.getServerByApiKey(INVALID_API_KEY)).thenReturn(null);

        mockMvc.perform(post(RESTMappingV1.REPLAY_LITE_REPLAYS + "/upload")
                .header(RequestHeader.API_KEY, INVALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadBody()))
            .andExpect(status().isUnauthorized());

        verify(serverService).getServerByApiKey(INVALID_API_KEY);
        verifyNoInteractions(replayLiteService);
    }

    @Test
    void replayLiteUploadWithValidApiKeyUsesAuthenticatedServer() throws Exception {
        when(serverService.getServerByApiKey(VALID_API_KEY)).thenReturn(server);
        when(replayLiteService.initUpload(same(server), any(), any()))
            .thenReturn(new ReplayLiteUploadInitResponse(
                "75f4b741-67df-414c-957b-a8a08222fc30",
                "https://uploads.example/replay",
                "PUT",
                Map.of("Content-Type", "application/octet-stream"),
                Instant.parse("2026-05-15T12:15:00Z")
            ));

        mockMvc.perform(post(RESTMappingV1.REPLAY_LITE_REPLAYS + "/upload")
                .header(RequestHeader.API_KEY, VALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replayId").value("75f4b741-67df-414c-957b-a8a08222fc30"));

        verify(serverService).getServerByApiKey(VALID_API_KEY);
        verify(replayLiteService).initUpload(same(server), any(), any());
    }

    private String uploadBody() {
        return """
            {
              "pluginServerUuid": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
              "requestedSize": 2048,
              "mcVersion": "1.21.4"
            }
            """;
    }
}
