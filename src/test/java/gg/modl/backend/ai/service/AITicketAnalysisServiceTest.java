package gg.modl.backend.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.ai.LLMService;
import gg.modl.backend.ai.data.AIAnalysisResult;
import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.SystemPromptMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.limits.DefaultServerLimitPolicy;
import gg.modl.backend.player.service.PunishmentLifecycleService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.settings.service.AIModerationSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketStatus;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private PunishmentLifecycleService punishmentLifecycleService;

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
            punishmentLifecycleService,
            punishmentTypeService,
            usageTrackingService,
            new DefaultServerLimitPolicy(),
            new ObjectMapper(),
            systemPromptRepository
        );
    }

    private AIModerationSettings settings(boolean enableAutomatedActions, boolean typeEnabled) {
        return AIModerationSettings.builder()
            .enableAIReview(true)
            .enableAutomatedActions(enableAutomatedActions)
            .aiPunishmentConfigs(Map.of(
                "4",
                AIModerationSettings.AIPunishmentConfig.builder()
                    .id("4")
                    .name("Ban")
                    .enabled(typeEnabled)
                    .build()
            ))
            .build();
    }

    private Ticket chatTicket(String id, String playerUuid) {
        return Ticket.builder()
            .id(id)
            .type(TicketCategory.CHAT)
            .reportedPlayer("Player")
            .reportedPlayerUuid(playerUuid)
            .chatMessages(List.of(new Ticket.ChatMessage("bad chat", new Date())))
            .replies(new ArrayList<>())
            .notes(new ArrayList<>())
            .build();
    }

    @Test
    void dismissSuggestionMarksAnalysisDismissedThroughRepositorySave() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        Ticket ticket = Ticket.builder()
            .id("REPORT-1")
            .aiAnalysis(new AIAnalysisResult(
                "Toxic chat",
                new AIAnalysisResult.SuggestedAction(2, "regular"),
                new Date(),
                "{}"
            ))
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
            .aiAnalysis(new AIAnalysisResult(
                "Severe toxicity",
                new AIAnalysisResult.SuggestedAction(4, "severe"),
                new Date(),
                "{}"
            ))
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
        assertEquals(TicketStatus.CLOSED, updatedTicket.getStatus());
        assertTrue(updatedTicket.isLocked());
        // Manual apply by a human moderator must NOT be recorded as an automatic AI application.
        assertFalse(updatedTicket.getAiAnalysis().isWasAppliedAutomatically());
        assertEquals(1, updatedTicket.getReplies().size());
        assertEquals(1, updatedTicket.getNotes().size());
        verify(punishmentLifecycleService).createPunishment(any(Server.class), any(UUID.class), any());
    }

    @Test
    void applySuggestionRejectedWhenTicketAlreadyClosed() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        String playerUuid = UUID.randomUUID().toString();
        Ticket ticket = Ticket.builder()
            .id("REPORT-CLOSED")
            .reportedPlayerUuid(playerUuid)
            .status(TicketStatus.CLOSED)
            .locked(true)
            .replies(new ArrayList<>())
            .notes(new ArrayList<>())
            .aiAnalysis(new AIAnalysisResult(
                "Severe toxicity",
                new AIAnalysisResult.SuggestedAction(4, "severe"),
                new Date(),
                "{}"
            ))
            .build();

        when(ticketRepository.findById(server, "REPORT-CLOSED")).thenReturn(Optional.of(ticket));

        var result = aiTicketAnalysisService.applyAISuggestion(server, "REPORT-CLOSED", "Moderator");

        assertFalse(result.success());
        verify(punishmentLifecycleService, never()).createPunishment(any(Server.class), any(UUID.class), any());
        verify(ticketRepository, never()).saveEntity(any(Server.class), any(Ticket.class));
    }

    @Test
    void applyAISuggestionReturnsErrorOnMalformedUuid() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        Ticket ticket = Ticket.builder()
            .id("REPORT-BADUUID")
            .reportedPlayerUuid("not-a-uuid")
            .replies(new ArrayList<>())
            .notes(new ArrayList<>())
            .aiAnalysis(new AIAnalysisResult(
                "Severe toxicity",
                new AIAnalysisResult.SuggestedAction(4, "severe"),
                new Date(),
                "{}"
            ))
            .build();

        when(ticketRepository.findById(server, "REPORT-BADUUID")).thenReturn(Optional.of(ticket));

        var result = aiTicketAnalysisService.applyAISuggestion(server, "REPORT-BADUUID", "Moderator");

        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().toUpperCase().contains("UUID"));
        verify(punishmentLifecycleService, never()).createPunishment(any(Server.class), any(UUID.class), any());
    }

    @Test
    void analyzeTicketExecutesAutomatedActionWhenMasterAndTypeEnabled() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-id");
        String playerUuid = UUID.randomUUID().toString();
        Ticket ticket = chatTicket("REPORT-3", playerUuid);
        AIModerationSettings settings = settings(true, true);

        when(llmService.isAvailable()).thenReturn(true);
        when(aiModerationSettingsService.getAIModerationSettings(server)).thenReturn(settings);
        when(serverRepository.findAIUsageSnapshotById("server-id")).thenReturn(Optional.empty());
        when(ticketRepository.findById(server, "REPORT-3")).thenReturn(Optional.of(ticket));
        when(systemPromptRepository.findActive()).thenReturn(Optional.empty());
        when(punishmentTypeService.getPunishmentTypeName(server, 4)).thenReturn("Ban");
        when(llmService.generate(any())).thenReturn("""
            {"analysis":"violation","suggestedAction":{"punishmentTypeId":4,"severity":"severe"}}
            """);

        aiTicketAnalysisService.analyzeTicketAsync(server, "REPORT-3");

        verify(punishmentLifecycleService, times(1)).createPunishment(any(Server.class), any(UUID.class), any());

        ArgumentCaptor<Ticket> updatedCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveEntity(any(Server.class), updatedCaptor.capture());
        Ticket saved = updatedCaptor.getValue();
        assertEquals(TicketStatus.CLOSED, saved.getStatus());
        assertTrue(saved.isLocked());
        assertNotNull(saved.getAiAnalysis());
        assertTrue(saved.getAiAnalysis().isWasAppliedAutomatically());

        verify(usageTrackingService, times(1)).incrementAiRequests("server-id", 1);
    }

    @Test
    void analyzeTicketDoesNotExecuteWhenMasterToggleDisabled() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-id");
        String playerUuid = UUID.randomUUID().toString();
        Ticket ticket = chatTicket("REPORT-4", playerUuid);
        AIModerationSettings settings = settings(false, true);

        when(llmService.isAvailable()).thenReturn(true);
        when(aiModerationSettingsService.getAIModerationSettings(server)).thenReturn(settings);
        when(serverRepository.findAIUsageSnapshotById("server-id")).thenReturn(Optional.empty());
        when(ticketRepository.findById(server, "REPORT-4")).thenReturn(Optional.of(ticket));
        when(systemPromptRepository.findActive()).thenReturn(Optional.empty());
        when(llmService.generate(any())).thenReturn("""
            {"analysis":"violation","suggestedAction":{"punishmentTypeId":4,"severity":"severe"}}
            """);

        aiTicketAnalysisService.analyzeTicketAsync(server, "REPORT-4");

        verify(punishmentLifecycleService, never()).createPunishment(any(Server.class), any(UUID.class), any());

        ArgumentCaptor<Ticket> updatedCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveEntity(any(Server.class), updatedCaptor.capture());
        Ticket saved = updatedCaptor.getValue();
        assertNotNull(saved.getAiAnalysis());
        assertFalse(saved.getAiAnalysis().isWasAppliedAutomatically());
    }

    @Test
    void analyzeTicketDoesNotExecuteWhenTypeDisabled() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-id");
        String playerUuid = UUID.randomUUID().toString();
        Ticket ticket = chatTicket("REPORT-5", playerUuid);
        AIModerationSettings settings = settings(true, false);

        when(llmService.isAvailable()).thenReturn(true);
        when(aiModerationSettingsService.getAIModerationSettings(server)).thenReturn(settings);
        when(serverRepository.findAIUsageSnapshotById("server-id")).thenReturn(Optional.empty());
        when(ticketRepository.findById(server, "REPORT-5")).thenReturn(Optional.of(ticket));
        when(systemPromptRepository.findActive()).thenReturn(Optional.empty());
        when(llmService.generate(any())).thenReturn("""
            {"analysis":"violation","suggestedAction":{"punishmentTypeId":4,"severity":"severe"}}
            """);

        aiTicketAnalysisService.analyzeTicketAsync(server, "REPORT-5");

        verify(punishmentLifecycleService, never()).createPunishment(any(Server.class), any(UUID.class), any());

        ArgumentCaptor<Ticket> updatedCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveEntity(any(Server.class), updatedCaptor.capture());
        Ticket saved = updatedCaptor.getValue();
        assertNotNull(saved.getAiAnalysis());
        assertFalse(saved.getAiAnalysis().isWasAppliedAutomatically());
    }

    @Test
    void parseResponseReturnsAnalysisWithoutActionWhenSeverityMissing() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-id");
        String playerUuid = UUID.randomUUID().toString();
        Ticket ticket = chatTicket("REPORT-6", playerUuid);
        AIModerationSettings settings = settings(false, true);

        when(llmService.isAvailable()).thenReturn(true);
        when(aiModerationSettingsService.getAIModerationSettings(server)).thenReturn(settings);
        when(serverRepository.findAIUsageSnapshotById("server-id")).thenReturn(Optional.empty());
        when(ticketRepository.findById(server, "REPORT-6")).thenReturn(Optional.of(ticket));
        when(systemPromptRepository.findActive()).thenReturn(Optional.empty());
        when(llmService.generate(any())).thenReturn("""
            {"analysis":"x","suggestedAction":{"punishmentTypeId":4}}
            """);

        aiTicketAnalysisService.analyzeTicketAsync(server, "REPORT-6");

        ArgumentCaptor<Ticket> updatedCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveEntity(any(Server.class), updatedCaptor.capture());
        Ticket saved = updatedCaptor.getValue();
        assertNotNull(saved.getAiAnalysis());
        assertNull(saved.getAiAnalysis().getSuggestedAction());
        verify(punishmentLifecycleService, never()).createPunishment(any(Server.class), any(UUID.class), any());
        // a usable analysis was produced and saved, so the request IS metered
        verify(usageTrackingService, times(1)).incrementAiRequests("server-id", 1);
    }

    @Test
    void analyzeTicketDoesNotMeterWhenParseFails() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-id");
        String playerUuid = UUID.randomUUID().toString();
        Ticket ticket = chatTicket("REPORT-7", playerUuid);
        AIModerationSettings settings = settings(false, true);

        when(llmService.isAvailable()).thenReturn(true);
        when(aiModerationSettingsService.getAIModerationSettings(server)).thenReturn(settings);
        when(serverRepository.findAIUsageSnapshotById("server-id")).thenReturn(Optional.empty());
        when(ticketRepository.findById(server, "REPORT-7")).thenReturn(Optional.of(ticket));
        when(systemPromptRepository.findActive()).thenReturn(Optional.empty());
        // No "analysis" field -> parseResponse returns null
        when(llmService.generate(any())).thenReturn("not json at all");

        aiTicketAnalysisService.analyzeTicketAsync(server, "REPORT-7");

        verify(ticketRepository, never()).saveEntity(any(Server.class), any(Ticket.class));
        verify(usageTrackingService, never()).incrementAiRequests(eq("server-id"), anyLong());
    }
}
