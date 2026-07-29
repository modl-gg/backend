package gg.modl.backend.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.UnknownFieldSet;
import gg.modl.backend.infrastructure.exception.ErrorResponseDTO;
import gg.modl.backend.infrastructure.exception.GlobalExceptionHandler;
import gg.modl.backend.infrastructure.proto.ProtoBinaryHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtoJsonHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtoValidationAdvice;
import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.infrastructure.proto.ProtobufMediaTypes;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RESTMappingV3;
import gg.modl.backend.player.controller.MinecraftNotificationController;
import gg.modl.backend.player.controller.MinecraftNotificationV3Controller;
import gg.modl.backend.player.service.MinecraftPlayerService;
import gg.modl.backend.player.dto.response.AcknowledgeResult;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.proto.modl.v1.AcknowledgeNotificationsRequest;
import gg.modl.proto.modl.v1.ApiError;
import gg.modl.proto.modl.v1.SimpleResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MinecraftNotificationV3ControllerTest {
    private MinecraftPlayerService minecraftPlayerService;
    private MockMvc v3MockMvc;
    private MockMvc v1MockMvc;
    private Server server;

    @BeforeEach
    void setUp() {
        minecraftPlayerService = mock(MinecraftPlayerService.class);
        server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);

        v3MockMvc = MockMvcBuilders.standaloneSetup(new MinecraftNotificationV3Controller(minecraftPlayerService))
            .setControllerAdvice(new GlobalExceptionHandler(new ProtobufErrorResponseWriter()), new ProtoValidationAdvice())
            .setMessageConverters(new ProtoBinaryHttpMessageConverter(), new ProtoJsonHttpMessageConverter())
            .defaultRequest(post("/").requestAttr(RequestAttribute.SERVER, server))
            .build();

        v1MockMvc = MockMvcBuilders.standaloneSetup(new MinecraftNotificationController(minecraftPlayerService))
            .setControllerAdvice(new GlobalExceptionHandler(new ProtobufErrorResponseWriter()))
            .defaultRequest(post("/").requestAttr(RequestAttribute.SERVER, server))
            .build();
    }

    @Test
    void v3AcknowledgeAcceptsBinaryRequestAndReturnsBinarySimpleResponse() throws Exception {
        when(minecraftPlayerService.acknowledgeNotifications(same(server), any(), any(), any()))
            .thenReturn(new AcknowledgeResult("Acknowledged 2 notification(s)"));

        AcknowledgeNotificationsRequest request =
            AcknowledgeNotificationsRequest.newBuilder()
                .setPlayerUuid("11111111-2222-3333-4444-555555555555")
                .addAllNotificationIds(List.of("notification-1", "notification-2"))
                .setAcknowledgedAt("2026-05-02T20:00:00Z")
                .build();

        MvcResult result = v3MockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/notifications/acknowledge")
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content(request.toByteArray()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        SimpleResponse response = SimpleResponse.parseFrom(result.getResponse().getContentAsByteArray());
        assertTrue(response.getSuccess());

        ArgumentCaptor<List<String>> notificationIdsCaptor = listCaptor();
        ArgumentCaptor<String> acknowledgedAtCaptor = ArgumentCaptor.forClass(String.class);
        verify(minecraftPlayerService).acknowledgeNotifications(
            same(server),
            eq("11111111-2222-3333-4444-555555555555"),
            notificationIdsCaptor.capture(),
            acknowledgedAtCaptor.capture()
        );
        assertEquals(List.of("notification-1", "notification-2"), notificationIdsCaptor.getValue());
        assertEquals("2026-05-02T20:00:00Z", acknowledgedAtCaptor.getValue());
    }

    @Test
    void v3AcknowledgeOmittedAcknowledgedAtMapsToLegacyNull() throws Exception {
        when(minecraftPlayerService.acknowledgeNotifications(same(server), any(), any(), any()))
            .thenReturn(new AcknowledgeResult("Acknowledged 1 notification(s)"));

        AcknowledgeNotificationsRequest request =
            AcknowledgeNotificationsRequest.newBuilder()
                .setPlayerUuid("11111111-2222-3333-4444-555555555555")
                .addNotificationIds("notification-1")
                .build();

        MvcResult result = v3MockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/notifications/acknowledge")
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content(request.toByteArray()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        SimpleResponse response = SimpleResponse.parseFrom(result.getResponse().getContentAsByteArray());
        assertTrue(response.getSuccess());

        ArgumentCaptor<String> acknowledgedAtCaptor = ArgumentCaptor.forClass(String.class);
        verify(minecraftPlayerService).acknowledgeNotifications(
            same(server),
            eq("11111111-2222-3333-4444-555555555555"),
            eq(List.of("notification-1")),
            acknowledgedAtCaptor.capture()
        );
        assertNull(acknowledgedAtCaptor.getValue());
    }

    @Test
    void v3AcknowledgeValidationFailureReturnsBinaryApiErrorWithFieldViolations() throws Exception {
        AcknowledgeNotificationsRequest request =
            AcknowledgeNotificationsRequest.newBuilder()
                .setPlayerUuid("not-a-uuid")
                .addNotificationIds("")
                .addNotificationIds("x".repeat(65))
                .setAcknowledgedAt("x".repeat(65))
                .build();

        MvcResult result = v3MockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/notifications/acknowledge")
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content(request.toByteArray()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(400, error.getStatusCode());
        assertEquals("INVALID_ARGUMENT", error.getCode());
        assertTrue(error.getFieldViolationsList().stream()
            .anyMatch(violation -> violation.getField().contains("player_uuid")));
        assertTrue(error.getFieldViolationsList().stream()
            .anyMatch(violation -> violation.getField().contains("notification_ids")));
        assertTrue(error.getFieldViolationsList().stream()
            .anyMatch(violation -> violation.getField().contains("acknowledged_at")));
    }

    @Test
    void v3AcknowledgeEmptyNotificationIdsReturnsBinaryApiErrorWithFieldViolation() throws Exception {
        AcknowledgeNotificationsRequest request =
            AcknowledgeNotificationsRequest.newBuilder()
                .setPlayerUuid("11111111-2222-3333-4444-555555555555")
                .setUnknownFields(UnknownFieldSet.newBuilder()
                    .addField(999, UnknownFieldSet.Field.newBuilder().addVarint(1).build())
                    .build())
                .build();

        MvcResult result = v3MockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/notifications/acknowledge")
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content(request.toByteArray()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(400, error.getStatusCode());
        assertEquals("INVALID_ARGUMENT", error.getCode());
        assertTrue(error.getFieldViolationsList().stream()
            .anyMatch(violation -> violation.getField().contains("notification_ids")));
    }

    @Test
    void v3AcknowledgeWhitespaceNotificationIdReturnsBinaryApiErrorWithFieldViolation() throws Exception {
        AcknowledgeNotificationsRequest request =
            AcknowledgeNotificationsRequest.newBuilder()
                .setPlayerUuid("11111111-2222-3333-4444-555555555555")
                .addNotificationIds("   ")
                .build();

        MvcResult result = v3MockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/notifications/acknowledge")
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content(request.toByteArray()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(400, error.getStatusCode());
        assertEquals("INVALID_ARGUMENT", error.getCode());
        assertTrue(error.getFieldViolationsList().stream()
            .anyMatch(violation -> violation.getField().contains("notification_ids")));
    }

    @Test
    void v3AcknowledgeRejectsJsonContentTypeWithBinaryApiError() throws Exception {
        MvcResult result = v3MockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/notifications/acknowledge")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content("{}"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(415, error.getStatusCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", error.getCode());
    }

    @Test
    void v1AcknowledgeInvalidUuidStillReturnsJsonError() throws Exception {
        MvcResult result = v1MockMvc.perform(post(RESTMappingV1.MINECRAFT_NOTIFICATIONS + "/acknowledge")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "playerUuid": "not-a-uuid",
                      "notificationIds": ["notification-1"]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        ErrorResponseDTO error = new ObjectMapper()
            .readValue(result.getResponse().getContentAsByteArray(), ErrorResponseDTO.class);
        assertEquals(400, error.status());
        assertTrue(error.error().startsWith("Invalid data provided"));
        assertFalse(result.getResponse().getContentType().contains(ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<String>> listCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
