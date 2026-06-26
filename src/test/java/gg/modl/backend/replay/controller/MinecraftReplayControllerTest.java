package gg.modl.backend.replay.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.infrastructure.exception.ErrorResponseDTO;
import gg.modl.backend.infrastructure.exception.GlobalExceptionHandler;
import gg.modl.backend.infrastructure.proto.ProtobufMediaTypes;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.replay.dto.InitReplayUploadResponse;
import gg.modl.backend.replay.service.ReplayService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MinecraftReplayControllerTest {
    private ReplayService replayService;
    private MockMvc mockMvc;
    private Server server;

    @BeforeEach
    void setUp() {
        replayService = mock(ReplayService.class);
        server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);

        mockMvc = MockMvcBuilders.standaloneSetup(new MinecraftReplayController(replayService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .defaultRequest(post("/").requestAttr(RequestAttribute.SERVER, server))
            .build();
    }

    @Test
    void v1UploadReturnsJsonShapePluginParses() throws Exception {
        when(replayService.initUpload(same(server), eq("1.21.4"), eq(2048L), isNull(), isNull()))
            .thenReturn(new InitReplayUploadResponse(
                "replay-1",
                "https://storage.example/upload",
                "PUT",
                Map.of("Content-Type", "application/octet-stream")
            ));

        mockMvc.perform(post(RESTMappingV1.MINECRAFT_REPLAYS + "/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mcVersion": "1.21.4",
                      "fileSize": 2048
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replayId").exists())
            .andExpect(jsonPath("$.uploadUrl").exists())
            .andExpect(jsonPath("$.method").exists())
            .andExpect(jsonPath("$.requiredHeaders").exists());
    }

    @Test
    void v1UploadInvalidRequestStillReturnsJsonError() throws Exception {
        MvcResult result = mockMvc.perform(post(RESTMappingV1.MINECRAFT_REPLAYS + "/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mcVersion": "",
                      "fileSize": 0
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        ErrorResponseDTO error = new ObjectMapper()
            .readValue(result.getResponse().getContentAsByteArray(), ErrorResponseDTO.class);
        assertEquals(400, error.status());
        assertEquals("Invalid data provided.", error.error());
        assertFalse(result.getResponse().getContentType().contains(ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE));
    }

    @Test
    void v1ConfirmReturnsJsonContract() throws Exception {
        when(replayService.confirmUpload(same(server), eq("replay-1"))).thenReturn(true);

        mockMvc.perform(post(RESTMappingV1.MINECRAFT_REPLAYS + "/confirm/replay-1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Upload confirmed"));

        when(replayService.confirmUpload(same(server), eq("replay-1"))).thenReturn(false);

        mockMvc.perform(post(RESTMappingV1.MINECRAFT_REPLAYS + "/confirm/replay-1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Upload verification failed"));
    }
}
