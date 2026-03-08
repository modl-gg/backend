package gg.modl.backend.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.ai.LLMService;
import gg.modl.backend.ai.data.AIAnalysisResult;
import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.SystemPromptMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.service.PunishmentService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.AIModerationSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.ticket.data.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AITicketAnalysisServiceTest {

    @Mock
    private LLMService llmService;

    @Mock
    private AIModerationSettingsService aiModerationSettingsService;

    @Mock
    private TicketMongoRepository ticketRepository;

    @Mock
    private ServerMongoRepository serverRepository;

    @Mock
    private PunishmentService punishmentService;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private UsageTrackingService usageTrackingService;

    @Mock
    private SystemPromptMongoRepository systemPromptRepository;

    private AITicketAnalysisService aiTicketAnalysisService;

    @BeforeEach
    void setUp() {
        aiTicketAnalysisService = new AITicketAnalysisService(
                llmService,
                aiModerationSettingsService,
                ticketRepository,
                serverRepository,
                punishmentService,
                punishmentTypeService,
                usageTrackingService,
                new ObjectMapper(),
                systemPromptRepository
        );
    }

    @Test
    void dismissSuggestionMarksAnalysisDismissedThroughRepositorySave() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        Ticket ticket = Ticket.builder()
                .id("REPORT-1")
                .aiAnalysis(AIAnalysisResult.builder()
                        .analysis("Toxic chat")
                        .suggestedAction(AIAnalysisResult.SuggestedAction.builder().punishmentTypeId(2).severity("regular").build())
                        .build())
                .build();

        when(ticketRepository.findById(server, "REPORT-1")).thenReturn(Optional.of(ticket));

        var result = aiTicketAnalysisService.dismissAISuggestion(server, "REPORT-1");

        assertTrue(result.success());

        ArgumentCaptor<Ticket> updatedCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveEntity(any(Server.class), updatedCaptor.capture());
        assertTrue(updatedCaptor.getValue().getAiAnalysis().isDismissed());
    }

    @Test
    void applySuggestionClosesTicketAndMarksAnalysisApplied() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        String playerUuid = UUID.randomUUID().toString();
        Ticket ticket = Ticket.builder()
                .id("REPORT-2")
                .reportedPlayerUuid(playerUuid)
                .replies(new ArrayList<>())
                .notes(new ArrayList<>())
                .aiAnalysis(AIAnalysisResult.builder()
                        .analysis("Severe toxicity")
                        .suggestedAction(AIAnalysisResult.SuggestedAction.builder().punishmentTypeId(4).severity("severe").build())
                        .build())
                .build();

        when(ticketRepository.findById(server, "REPORT-2")).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveEntity(any(Server.class), any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(punishmentTypeService.getPunishmentTypeName(server, 4)).thenReturn("Ban");

        var result = aiTicketAnalysisService.applyAISuggestion(server, "REPORT-2", "Moderator");

        assertTrue(result.success());

        ArgumentCaptor<Ticket> updatedCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveEntity(any(Server.class), updatedCaptor.capture());
        Ticket updatedTicket = updatedCaptor.getValue();
        assertEquals("Closed", updatedTicket.getStatus());
        assertTrue(updatedTicket.isLocked());
        assertTrue(updatedTicket.getAiAnalysis().isWasAppliedAutomatically());
        assertEquals(1, updatedTicket.getReplies().size());
        assertEquals(1, updatedTicket.getNotes().size());
        verify(punishmentService).createPunishment(any(Server.class), any(UUID.class), any());
    }
}
