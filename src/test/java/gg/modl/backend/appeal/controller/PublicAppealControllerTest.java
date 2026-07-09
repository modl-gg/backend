package gg.modl.backend.appeal.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.modl.backend.appeal.service.AppealService;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.GlobalExceptionHandler;
import gg.modl.backend.infrastructure.proto.ProtoBinaryHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtoJsonHttpMessageConverter;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.service.PublicAccessProperties;
import gg.modl.backend.ticket.service.PublicRecordAccessService;
import gg.modl.backend.ticket.service.PublicRecordVerificationService;
import gg.modl.backend.ticket.service.TicketEmailVerificationService;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicAppealControllerTest {
    private AppealService appealService;
    private TicketEmailVerificationService verificationService;
    private MockMvc mockMvc;
    private Server server;

    @BeforeEach
    void setUp() {
        appealService = mock(AppealService.class);
        verificationService = mock(TicketEmailVerificationService.class);
        RealtimeEventPublisher realtimeEventPublisher = mock(RealtimeEventPublisher.class);
        PublicRecordAccessService recordAccessService =
            new PublicRecordAccessService(verificationService, new PublicAccessProperties());
        PublicRecordVerificationService recordVerificationService =
            new PublicRecordVerificationService(verificationService);
        server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);

        mockMvc = MockMvcBuilders
            .standaloneSetup(new PublicAppealController(appealService, recordAccessService, recordVerificationService, realtimeEventPublisher))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new ProtoJsonHttpMessageConverter(), new ProtoBinaryHttpMessageConverter(), new JacksonJsonHttpMessageConverter())
            .defaultRequest(get("/").requestAttr(RequestAttribute.SERVER, server))
            .build();
    }

    private Ticket appealWithContactEmail() {
        return Ticket.builder()
            .id("APPEAL-1")
            .type(TicketCategory.APPEAL)
            .data(Map.of("contactEmail", "player@example.com"))
            .build();
    }

    @Test
    void getAppealWithoutTokenRequiresVerificationWhenContactEmailPresent() throws Exception {
        when(appealService.getAppealRaw(server, "APPEAL-1")).thenReturn(Optional.of(appealWithContactEmail()));

        mockMvc.perform(get(RESTMappingV1.PUBLIC_APPEALS + "/APPEAL-1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.requiresVerification").value(true))
            .andExpect(jsonPath("$.ticketId").value("APPEAL-1"));
    }

    @Test
    void getAppealWithValidTokenReturnsAppeal() throws Exception {
        Ticket appeal = appealWithContactEmail();
        when(appealService.getAppealRaw(server, "APPEAL-1")).thenReturn(Optional.of(appeal));
        when(verificationService.validateToken(server, "APPEAL-1", "valid-token")).thenReturn(true);
        when(appealService.toResponse(appeal)).thenReturn(new TicketResponse(
            "APPEAL-1",
            "appeal",
            "appeal",
            "Appeal for Punishment: ban-1",
            "open",
            "OPEN",
            "PlayerOne",
            null,
            null,
            null,
            null,
            new Date(1_700_000_000_000L),
            false,
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            null,
            true,
            false,
            null,
            List.of()
        ));

        mockMvc.perform(get(RESTMappingV1.PUBLIC_APPEALS + "/APPEAL-1")
                .param("token", "valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("APPEAL-1"))
            .andExpect(jsonPath("$.subject").value("Appeal for Punishment: ban-1"));
    }

    @Test
    void addReplyToLockedAppealReturnsConflictWithMessage() throws Exception {
        Ticket appeal = appealWithContactEmail();
        when(appealService.getAppealRaw(server, "APPEAL-1")).thenReturn(Optional.of(appeal));
        when(verificationService.validateToken(server, "APPEAL-1", "valid-token")).thenReturn(true);
        when(appealService.addPublicReply(eq(server), eq("APPEAL-1"), eq("hello"), isNull()))
            .thenThrow(new ConflictException("Appeal is locked and cannot accept new replies"));

        mockMvc.perform(post(RESTMappingV1.PUBLIC_APPEALS + "/APPEAL-1/replies")
                .param("token", "valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Appeal is locked and cannot accept new replies"));
    }

    @Test
    void requestVerificationSendsCodeAndReturnsEmailHint() throws Exception {
        Ticket appeal = appealWithContactEmail();
        when(appealService.getAppealRaw(server, "APPEAL-1")).thenReturn(Optional.of(appeal));
        when(verificationService.sendVerificationCode(server, appeal)).thenReturn("p***@example.com");

        mockMvc.perform(post(RESTMappingV1.PUBLIC_APPEALS + "/APPEAL-1/request-verification"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.emailHint").value("p***@example.com"));
    }

    @Test
    void requestVerificationReturnsNotFoundForMissingAppeal() throws Exception {
        when(appealService.getAppealRaw(server, "APPEAL-404")).thenReturn(Optional.empty());

        mockMvc.perform(post(RESTMappingV1.PUBLIC_APPEALS + "/APPEAL-404/request-verification"))
            .andExpect(status().isNotFound());
    }

    @Test
    void requestVerificationRejectsAppealWithoutContactEmail() throws Exception {
        Ticket appeal = Ticket.builder()
            .id("APPEAL-1")
            .type(TicketCategory.APPEAL)
            .data(Map.of())
            .build();
        when(appealService.getAppealRaw(server, "APPEAL-1")).thenReturn(Optional.of(appeal));

        mockMvc.perform(post(RESTMappingV1.PUBLIC_APPEALS + "/APPEAL-1/request-verification"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void verifyCodeReturnsTokenForValidCode() throws Exception {
        when(appealService.getAppealRaw(server, "APPEAL-1")).thenReturn(Optional.of(appealWithContactEmail()));
        when(verificationService.verifyCode(server, "APPEAL-1", "123456")).thenReturn("issued-token");

        mockMvc.perform(post(RESTMappingV1.PUBLIC_APPEALS + "/APPEAL-1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.token").value("issued-token"));
    }

    @Test
    void verifyCodeRejectsInvalidCode() throws Exception {
        when(appealService.getAppealRaw(server, "APPEAL-1")).thenReturn(Optional.of(appealWithContactEmail()));
        when(verificationService.verifyCode(server, "APPEAL-1", "000000")).thenReturn(null);

        mockMvc.perform(post(RESTMappingV1.PUBLIC_APPEALS + "/APPEAL-1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"000000\"}"))
            .andExpect(status().isForbidden());
    }
}
