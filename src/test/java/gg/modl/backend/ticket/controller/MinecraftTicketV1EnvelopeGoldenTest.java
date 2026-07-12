package gg.modl.backend.ticket.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.ai.service.AITicketAnalysisService;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.dto.request.AssignReportRequest;
import gg.modl.backend.ticket.dto.request.DismissReportRequest;
import gg.modl.backend.ticket.dto.request.MinecraftClaimTicketRequest;
import gg.modl.backend.ticket.dto.request.MinecraftCreateTicketRequest;
import gg.modl.backend.ticket.dto.request.MinecraftTicketsByIdsRequest;
import gg.modl.backend.ticket.dto.request.ResolveReportRequest;
import gg.modl.backend.ticket.dto.response.MinecraftTicketDetailView;
import gg.modl.backend.ticket.dto.response.MinecraftV1Response;
import gg.modl.backend.ticket.service.MinecraftTicketService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class MinecraftTicketV1EnvelopeGoldenTest {

    private static final ObjectMapper JSON = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private MinecraftTicketService ticketService;
    private AITicketAnalysisService aiService;
    private MinecraftTicketsController ticketsController;
    private MinecraftReportsController reportsController;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        ticketService = mock(MinecraftTicketService.class);
        aiService = mock(AITicketAnalysisService.class);
        ticketsController = new MinecraftTicketsController(ticketService, aiService);
        reportsController = new MinecraftReportsController(ticketService);
        request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestAttribute.SERVER)).thenReturn(mock(Server.class));
    }

    @Test
    void createTicketRendersFrozenSuccessEnvelope() throws Exception {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn("ticket-1");
        MinecraftCreateTicketRequest createRequest = mock(MinecraftCreateTicketRequest.class);
        when(createRequest.type()).thenReturn("bug");
        when(ticketService.createMinecraftTicket(any(), eq(createRequest))).thenReturn(ticket);

        ResponseEntity<MinecraftV1Response> response = ticketsController.createTicket(createRequest, request);

        assertEnvelope(response, 200,
            "{\"status\":200,\"success\":true,\"ticketId\":\"ticket-1\",\"message\":\"Ticket created successfully\"}");
    }

    @Test
    void createUnfinishedTicketRendersFrozenSuccessEnvelope() throws Exception {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn("ticket-1");
        MinecraftCreateTicketRequest createRequest = mock(MinecraftCreateTicketRequest.class);
        when(ticketService.createUnfinishedMinecraftTicket(any(), eq(createRequest))).thenReturn(ticket);

        ResponseEntity<MinecraftV1Response> response = ticketsController.createUnfinishedTicket(createRequest, request);

        assertEnvelope(response, 200,
            "{\"status\":200,\"success\":true,\"ticketId\":\"ticket-1\","
                + "\"message\":\"Ticket draft created - complete the form on the panel\"}");
    }

    @Test
    void getAllTicketsRendersEmptyTicketsEnvelope() throws Exception {
        when(ticketService.getMinecraftTickets(any(), any(), any(), anyInt())).thenReturn(List.of());

        ResponseEntity<MinecraftV1Response> response = ticketsController.getAllTickets(null, null, 50, request);

        assertEnvelope(response, 200, "{\"status\":200,\"tickets\":[]}");
    }

    @Test
    void getTicketFoundWrapsDetailInEnvelope() throws Exception {
        Ticket ticket = mock(Ticket.class);
        when(ticketService.getMinecraftTicket(any(), eq("ticket-1"))).thenReturn(Optional.of(ticket));
        when(ticketService.toTicketDetail(ticket)).thenReturn(new MinecraftTicketDetailView(
            "ticket-1", null, null, null, null, null, null, null, List.of(), null, null,
            false, List.of(), List.of(), null));

        ResponseEntity<MinecraftV1Response> response = ticketsController.getTicket("ticket-1", request);

        assertEquals(200, response.getStatusCode().value());
        JsonNode body = JSON.readTree(JSON.writeValueAsString(response.getBody()));
        assertEquals(200, body.get("status").asInt());
        assertEquals("ticket-1", body.get("ticket").get("id").asText());
    }

    @Test
    void getTicketNotFoundRendersFrozenErrorBody() throws Exception {
        when(ticketService.getMinecraftTicket(any(), eq("missing"))).thenReturn(Optional.empty());

        ResponseEntity<MinecraftV1Response> response = ticketsController.getTicket("missing", request);

        assertEnvelope(response, 404, "{\"status\":404,\"message\":\"Ticket not found\"}");
    }

    @Test
    void getPlayerTicketsRendersEmptyTicketsEnvelope() throws Exception {
        when(ticketService.getMinecraftTicketsByCreator(any(), any(), anyInt())).thenReturn(List.of());

        ResponseEntity<MinecraftV1Response> response = ticketsController.getPlayerTickets("uuid", request);

        assertEnvelope(response, 200, "{\"status\":200,\"tickets\":[]}");
    }

    @Test
    void claimTicketNotFoundRendersFrozenEnvelope() throws Exception {
        when(ticketService.claimMinecraftTicket(any(), eq("t-1"), any()))
            .thenReturn(new MinecraftTicketService.MinecraftTicketClaimResult(
                MinecraftTicketService.MinecraftTicketClaimStatus.NOT_FOUND, null));

        ResponseEntity<MinecraftV1Response> response = ticketsController.claimTicket(
            "t-1", mock(MinecraftClaimTicketRequest.class), request);

        assertEnvelope(response, 404, "{\"status\":404,\"success\":false,\"message\":\"Ticket not found\"}");
    }

    @Test
    void claimTicketAlreadyLinkedRendersFrozenConflictEnvelope() throws Exception {
        when(ticketService.claimMinecraftTicket(any(), eq("t-1"), any()))
            .thenReturn(new MinecraftTicketService.MinecraftTicketClaimResult(
                MinecraftTicketService.MinecraftTicketClaimStatus.ALREADY_LINKED, null));

        ResponseEntity<MinecraftV1Response> response = ticketsController.claimTicket(
            "t-1", mock(MinecraftClaimTicketRequest.class), request);

        assertEnvelope(response, 409,
            "{\"status\":409,\"success\":false,\"message\":\"Ticket is already linked to a Minecraft account\"}");
    }

    @Test
    void claimTicketSuccessIncludesSubjectWhenPresent() throws Exception {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getSubject()).thenReturn("Help me");
        when(ticketService.claimMinecraftTicket(any(), eq("t-1"), any()))
            .thenReturn(new MinecraftTicketService.MinecraftTicketClaimResult(
                MinecraftTicketService.MinecraftTicketClaimStatus.SUCCESS, ticket));

        ResponseEntity<MinecraftV1Response> response = ticketsController.claimTicket(
            "t-1", mock(MinecraftClaimTicketRequest.class), request);

        assertEnvelope(response, 200,
            "{\"status\":200,\"success\":true,\"message\":\"Ticket successfully linked to your account\","
                + "\"ticketId\":\"t-1\",\"subject\":\"Help me\"}");
    }

    @Test
    void claimTicketSuccessOmitsSubjectWhenAbsent() throws Exception {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getSubject()).thenReturn(null);
        when(ticketService.claimMinecraftTicket(any(), eq("t-1"), any()))
            .thenReturn(new MinecraftTicketService.MinecraftTicketClaimResult(
                MinecraftTicketService.MinecraftTicketClaimStatus.SUCCESS, ticket));

        ResponseEntity<MinecraftV1Response> response = ticketsController.claimTicket(
            "t-1", mock(MinecraftClaimTicketRequest.class), request);

        assertEnvelope(response, 200,
            "{\"status\":200,\"success\":true,\"message\":\"Ticket successfully linked to your account\","
                + "\"ticketId\":\"t-1\"}");
    }

    @Test
    void getTicketsByIdsEmptyRequestRendersEmptyEnvelope() throws Exception {
        MinecraftTicketsByIdsRequest byIds = mock(MinecraftTicketsByIdsRequest.class);
        when(byIds.ids()).thenReturn(List.of());

        ResponseEntity<MinecraftV1Response> response = ticketsController.getTicketsByIds(byIds, request);

        assertEnvelope(response, 200, "{\"status\":200,\"tickets\":[]}");
    }

    @Test
    void getAllReportsRendersEmptyReportsEnvelope() throws Exception {
        when(ticketService.getMinecraftReports(any(), any(), anyInt())).thenReturn(List.of());

        ResponseEntity<MinecraftV1Response> response = reportsController.getAllReports("open", 50, request);

        assertEnvelope(response, 200, "{\"status\":200,\"reports\":[]}");
    }

    @Test
    void dismissReportSuccessRendersFrozenEnvelope() throws Exception {
        when(ticketService.dismissMinecraftReport(any(), eq("r-1"), any()))
            .thenReturn(new MinecraftTicketService.ReportOperationResult(
                MinecraftTicketService.ReportOperationStatus.SUCCESS, null));

        ResponseEntity<MinecraftV1Response> response = reportsController.dismissReport(
            "r-1", mock(DismissReportRequest.class), request);

        assertEnvelope(response, 200, "{\"status\":200,\"success\":true,\"message\":\"Report dismissed\"}");
    }

    @Test
    void dismissReportNotFoundRendersFrozenErrorBody() throws Exception {
        when(ticketService.dismissMinecraftReport(any(), eq("r-1"), any()))
            .thenReturn(new MinecraftTicketService.ReportOperationResult(
                MinecraftTicketService.ReportOperationStatus.NOT_FOUND, null));

        ResponseEntity<MinecraftV1Response> response = reportsController.dismissReport(
            "r-1", mock(DismissReportRequest.class), request);

        assertEnvelope(response, 404, "{\"status\":404,\"message\":\"Report not found\"}");
    }

    @Test
    void resolveReportSuccessRendersFrozenEnvelope() throws Exception {
        when(ticketService.resolveMinecraftReport(any(), eq("r-1"), any()))
            .thenReturn(new MinecraftTicketService.ReportOperationResult(
                MinecraftTicketService.ReportOperationStatus.SUCCESS, null));

        ResponseEntity<MinecraftV1Response> response = reportsController.resolveReport(
            "r-1", mock(ResolveReportRequest.class), request);

        assertEnvelope(response, 200, "{\"status\":200,\"success\":true,\"message\":\"Report resolved\"}");
    }

    @Test
    void assignReportSuccessRendersFrozenEnvelope() throws Exception {
        when(ticketService.assignMinecraftReport(any(), eq("r-1"), any(AssignReportRequest.class)))
            .thenReturn(new MinecraftTicketService.ReportOperationResult(
                MinecraftTicketService.ReportOperationStatus.SUCCESS, null));

        ResponseEntity<MinecraftV1Response> response = reportsController.assignReport(
            "r-1", mock(AssignReportRequest.class), request);

        assertEnvelope(response, 200, "{\"status\":200,\"success\":true,\"message\":\"Report assigned\"}");
    }

    @Test
    void getPlayerReportsRendersEmptyReportsEnvelope() throws Exception {
        when(ticketService.getMinecraftReportsForPlayer(any(), any(), any(), anyInt())).thenReturn(List.of());

        ResponseEntity<MinecraftV1Response> response = reportsController.getPlayerReports("uuid", "all", 50, request);

        assertEnvelope(response, 200, "{\"status\":200,\"reports\":[]}");
    }

    private void assertEnvelope(ResponseEntity<MinecraftV1Response> response, int status, String expectedJson) throws Exception {
        assertEquals(status, response.getStatusCode().value());
        assertEquals(JSON.readTree(expectedJson), JSON.readTree(JSON.writeValueAsString(response.getBody())));
    }
}
