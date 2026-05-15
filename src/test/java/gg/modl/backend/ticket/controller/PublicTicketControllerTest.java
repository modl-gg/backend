package gg.modl.backend.ticket.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.modl.backend.infrastructure.exception.GlobalExceptionHandler;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.service.TicketEmailVerificationService;
import gg.modl.backend.ticket.service.TicketReplyService;
import gg.modl.backend.ticket.service.TicketService;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicTicketControllerTest {
    private TicketService ticketService;
    private TicketEmailVerificationService verificationService;
    private MockMvc mockMvc;
    private Server server;

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketService.class);
        TicketReplyService ticketReplyService = mock(TicketReplyService.class);
        verificationService = mock(TicketEmailVerificationService.class);
        server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);

        mockMvc = MockMvcBuilders
            .standaloneSetup(new PublicTicketController(ticketService, ticketReplyService, verificationService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .defaultRequest(get("/").requestAttr(RequestAttribute.SERVER, server))
            .build();
    }

    @Test
    void getTicketStatusRequiresTokenForEmailAuthTicket() throws Exception {
        Ticket ticket = Ticket.builder()
            .id("TICKET-1")
            .emailAuthEnabled(true)
            .build();
        when(ticketService.getTicketRaw(server, "TICKET-1")).thenReturn(Optional.of(ticket));

        mockMvc.perform(get(RESTMappingV1.PUBLIC_TICKETS + "/TICKET-1/status"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(verificationService);
    }

    @Test
    void getTicketStatusAllowsValidTokenForEmailAuthTicket() throws Exception {
        Ticket ticket = Ticket.builder()
            .id("TICKET-1")
            .emailAuthEnabled(true)
            .build();
        Date created = new Date(1_700_000_000_000L);
        when(ticketService.getTicketRaw(server, "TICKET-1")).thenReturn(Optional.of(ticket));
        when(verificationService.validateToken(server, "TICKET-1", "valid-token")).thenReturn(true);
        when(ticketService.getTicketById(server, "TICKET-1")).thenReturn(new TicketResponse(
            "TICKET-1",
            "support",
            "support",
            "Need help",
            "open",
            null,
            "PlayerOne",
            null,
            null,
            null,
            null,
            created,
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

        mockMvc.perform(get(RESTMappingV1.PUBLIC_TICKETS + "/TICKET-1/status")
                .param("token", "valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("TICKET-1"))
            .andExpect(jsonPath("$.subject").value("Need help"));
    }
}
