package gg.modl.backend.ticket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.ticket.data.AppealWorkflowStatus;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppealWorkflowTransitionServiceTest {
    private TicketMongoRepository ticketRepository;
    private TicketNotificationService notificationService;
    private AppealWorkflowTransitionService service;
    private Server server;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketMongoRepository.class);
        notificationService = mock(TicketNotificationService.class);
        service = new AppealWorkflowTransitionService(ticketRepository, notificationService);
        server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);
    }

    private Ticket openAppealLinkedTo(String punishmentId) {
        return Ticket.builder()
            .id("APPEAL-1")
            .type(TicketCategory.APPEAL)
            .status(TicketStatus.OPEN)
            .appealWorkflowStatus(AppealWorkflowStatus.OPEN)
            .data(Map.of("punishmentId", punishmentId))
            .build();
    }

    @Test
    void approvalClosesLocksAndNotifiesLinkedOpenAppeal() {
        when(ticketRepository.findById(server, "APPEAL-1")).thenReturn(Optional.of(openAppealLinkedTo("PUN-1")));

        service.applyOutcomeForPunishment(server, "APPEAL-1", "PUN-1", AppealWorkflowStatus.APPROVED, "Admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TicketReply>> repliesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).updateAppealState(eq(server), eq("APPEAL-1"), eq(AppealWorkflowStatus.APPROVED),
            eq(TicketStatus.CLOSED), eq(true), isNull(), repliesCaptor.capture());
        assertEquals("Appeal status changed to Approved.", repliesCaptor.getValue().get(0).getContent());
        verify(notificationService).notifyTicketClosed(eq(server), any(Ticket.class));
    }

    @Test
    void ignoresAppealNotLinkedToPunishment() {
        when(ticketRepository.findById(server, "APPEAL-1")).thenReturn(Optional.of(openAppealLinkedTo("OTHER")));

        service.applyOutcomeForPunishment(server, "APPEAL-1", "PUN-1", AppealWorkflowStatus.APPROVED, "Admin");

        verify(ticketRepository, never()).updateAppealState(any(), any(), any(), any(), any(), any(), anyList());
        verifyNoInteractions(notificationService);
    }

    @Test
    void rejectsChangingOneTerminalOutcomeToAnother() {
        Ticket rejected = Ticket.builder()
            .id("APPEAL-1")
            .type(TicketCategory.APPEAL)
            .status(TicketStatus.CLOSED)
            .appealWorkflowStatus(AppealWorkflowStatus.REJECTED)
            .data(Map.of("punishmentId", "PUN-1"))
            .build();
        when(ticketRepository.findById(server, "APPEAL-1")).thenReturn(Optional.of(rejected));

        assertThrows(ConflictException.class, () ->
            service.applyOutcomeForPunishment(server, "APPEAL-1", "PUN-1", AppealWorkflowStatus.APPROVED, "Admin"));

        verify(ticketRepository, never()).updateAppealState(any(), any(), any(), any(), any(), any(), anyList());
    }

    @Test
    void noOpWhenOutcomeAlreadyApplied() {
        Ticket approved = Ticket.builder()
            .id("APPEAL-1")
            .type(TicketCategory.APPEAL)
            .status(TicketStatus.CLOSED)
            .appealWorkflowStatus(AppealWorkflowStatus.APPROVED)
            .data(Map.of("punishmentId", "PUN-1"))
            .build();
        when(ticketRepository.findById(server, "APPEAL-1")).thenReturn(Optional.of(approved));

        service.applyOutcomeForPunishment(server, "APPEAL-1", "PUN-1", AppealWorkflowStatus.APPROVED, "Admin");

        verify(ticketRepository, never()).updateAppealState(any(), any(), any(), any(), any(), any(), anyList());
        verifyNoInteractions(notificationService);
    }
}
