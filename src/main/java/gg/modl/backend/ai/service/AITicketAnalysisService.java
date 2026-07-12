package gg.modl.backend.ai.service;

import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.ai.LLMService;
import gg.modl.backend.ai.data.AIAnalysisResult;
import gg.modl.backend.ai.data.DefaultPrompts;
import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.database.mongo.repository.ServerUsageRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.SystemPromptMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.limits.ServerLimits;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.player.service.PunishmentLifecycleService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.settings.data.AIModerationSettings.AIPunishmentConfig;
import gg.modl.backend.settings.service.AIModerationSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketNote;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AITicketAnalysisService {
    private final LLMService llmService;
    private final AIModerationSettingsService aiModerationSettingsService;
    private final TicketMongoRepository ticketRepository;
    private final ServerUsageRepository serverUsageRepository;
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final PunishmentTypeService punishmentTypeService;
    private final UsageTrackingService usageTrackingService;
    private final ServerLimitPolicy serverLimitPolicy;
    private final ChatModerationPromptBuilder promptBuilder;
    private final AiAnalysisResponseParser responseParser;
    private final SystemPromptMongoRepository systemPromptRepository;
    private final StaffMongoRepository staffRepository;
    private static final String AI_MODERATOR = "AI Moderator";
    private static final String DEFAULT_ISSUER_NAME = "Staff";
    private static final double AUTOMATED_ACTION_CONFIDENCE_THRESHOLD = 0.85;

    @Async
    public void analyzeTicketAsync(@NotNull Server server, @NotNull String ticketId) {
        final AIModerationSettings settings = resolveActiveModerationSettings(server);
        if (settings == null) {
            log.debug("Skipping AI analysis for ticket {}: preconditions not met", ticketId);
            return;
        }

        final Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);

        if (ticket == null) {
            log.debug("Ticket {} not found for AI analysis", ticketId);
            return;
        }

        if (!isChatReport(ticket)) {
            log.debug("Skipping AI analysis for ticket {}: not a chat report", ticketId);
            return;
        }

        if (ticket.getChatMessages() == null || ticket.getChatMessages().isEmpty()) {
            log.debug("Skipping AI analysis for ticket {}: no chat messages", ticketId);
            return;
        }

        final ChatModerationPromptBuilder.ModerationPrompt prompt = promptBuilder.buildModerationPrompt(ticket, settings, this::getSystemPrompt);
        if (prompt == null) {
            return;
        }

        final String rawResponse;
        try {
            rawResponse = llmService.generate(prompt.systemInstruction(), prompt.userContent());
        } catch (Exception e) {
            log.error("LLM generation failed for ticket {}", ticketId, e);
            return;
        }
        final AIAnalysisResult result = responseParser.parseResponse(rawResponse);
        if (result == null) {
            return;
        }

        usageTrackingService.incrementAiRequests(server.getId(), 1);

        ticket.setAiAnalysis(result);
        if (result.getSuggestedAction() != null && settings.isEnableAutomatedActions()) {
            executeAutomatedAction(server, ticket, result, settings);
        }
        ticket.setUpdatedAt(new Date());
        ticketRepository.saveEntity(server, ticket);
    }

    @Nullable
    private AIModerationSettings resolveActiveModerationSettings(@NotNull Server server) {
        if (!llmService.isAvailable()) {
            log.debug("LLM service not available");
            return null;
        }

        final ServerLimits limits = serverLimitPolicy.resolve(server);
        if (!limits.isAiModerationEnabled()) {
            return null;
        }

        final AIModerationSettings settings = aiModerationSettingsService.getAIModerationSettings(server);
        if (!settings.isEnableAIReview()
            || settings.getAiPunishmentConfigs() == null
            || settings.getAiPunishmentConfigs().isEmpty()) {
            return null;
        }

        final ServerUsageRepository.AIUsageSnapshot usageSnapshot = serverUsageRepository.findAIUsageSnapshotById(server.getId()).orElse(null);
        if (usageSnapshot != null) {
            long currentUsage = usageSnapshot.aiRequestsCurrentPeriod();
            long limit = limits.getAiRequestLimit();
            if (currentUsage >= limit) {
                log.debug("Server {} has reached AI request limit ({}/{})", server.getServerName(), currentUsage, limit);
                return null;
            }
        }

        return settings;
    }

    private boolean isChatReport(Ticket ticket) {
        return ticket.getType() == TicketCategory.CHAT;
    }

    private void executeAutomatedAction(Server server, Ticket ticket, AIAnalysisResult result, AIModerationSettings settings) {
        final AIAnalysisResult.SuggestedAction suggestion = result.getSuggestedAction();
        if (suggestion == null) {
            return;
        }

        if (!settings.isEnableAutomatedActions()) {
            return;
        }

        final AIAnalysisResult existing = ticket.getAiAnalysis();
        if (existing != null && (existing.isWasAppliedAutomatically() || existing.isDismissed())) {
            return;
        }

        if (ticket.getStatus() == TicketStatus.CLOSED || ticket.isLocked()) {
            return;
        }

        final Double confidence = result.getConfidence();
        if (confidence == null || confidence < AUTOMATED_ACTION_CONFIDENCE_THRESHOLD) {
            log.info("Skipping automated action for ticket {}: confidence {} below threshold {}. Leaving suggestion for human review.",
                ticket.getId(), confidence, AUTOMATED_ACTION_CONFIDENCE_THRESHOLD);
            return;
        }

        if (settings.getAiPunishmentConfigs() == null) {
            log.warn("No punishment configs available for automated action on ticket {}", ticket.getId());
            return;
        }

        final String typeKey = String.valueOf(suggestion.getPunishmentTypeId());
        final AIPunishmentConfig punishmentConfig = settings.getAiPunishmentConfigs().get(typeKey);

        if (punishmentConfig == null || !punishmentConfig.isEnabled()) {
            log.warn("Punishment config not found or disabled for type ordinal {}", typeKey);
            return;
        }

        final UUID playerUuid = parsePlayerUuid(ticket.getReportedPlayerUuid());
        if (playerUuid == null) {
            log.warn("Cannot execute automated action: no valid reported player UUID for ticket {}", ticket.getId());
            return;
        }

        try {
            applyPunishmentAndCloseTicket(server, ticket, result, playerUuid, AI_MODERATOR);
            result.setWasAppliedAutomatically(true);
        } catch (Exception e) {
            log.error("Failed to apply automated punishment for ticket {}", ticket.getId(), e);
        }
    }

    @Nullable
    private UUID parsePlayerUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void applyPunishmentAndCloseTicket(Server server, Ticket ticket, AIAnalysisResult aiAnalysis, UUID playerUuid, String staffName) {
        AIAnalysisResult.SuggestedAction suggestion = aiAnalysis.getSuggestedAction();
        String reason = aiAnalysis.getAnalysis();

        CreatePunishmentRequest request = new CreatePunishmentRequest(
            staffName,
            null,
            suggestion.getPunishmentTypeId(),
            null, null,
            List.of(ticket.getId()),
            suggestion.getSeverity(),
            "active",
            Map.of("aiGenerated", true),
            reason, null
        );

        punishmentLifecycleService.createPunishment(server, playerUuid, request);

        Date now = new Date();
        String typeName = punishmentTypeService.getPunishmentTypeName(server, suggestion.getPunishmentTypeId());

        TicketReply systemReply = TicketReply.builder()
            .id(UUID.randomUUID().toString())
            .name(staffName)
            .content("This report has been reviewed and appropriate action has been taken. Thank you for your report.")
            .type("system")
            .created(now)
            .staff(true)
            .action("Close")
            .attachments(new ArrayList<>())
            .build();

        TicketNote staffNote = TicketNote.builder()
            .text("AI Analysis by " + staffName + ": " + typeName + " (" + suggestion.getSeverity() + "). Reason: " + reason)
            .issuerName(staffName)
            .date(now)
            .build();

        if (ticket.getReplies() == null) {
            ticket.setReplies(new ArrayList<>());
        }
        if (ticket.getNotes() == null) {
            ticket.setNotes(new ArrayList<>());
        }
        ticket.getReplies().add(systemReply);
        ticket.getNotes().add(staffNote);
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setLocked(true);
        ticket.setUpdatedAt(now);
    }

    private String getSystemPrompt() {
        SystemPrompt prompt = systemPromptRepository.findActive().orElse(null);

        if (prompt != null && prompt.getPrompt() != null && !prompt.getPrompt().isBlank()) {
            return prompt.getPrompt();
        }

        return getDefaultPrompt();
    }

    @NotNull
    public static String getDefaultPrompt() {
        return DefaultPrompts.MINECRAFT;
    }

    @NotNull
    public AISuggestionResult applyAISuggestion(@NotNull Server server, @NotNull String ticketId, @Nullable String actingEmail) {
        final Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);

        if (ticket == null) {
            return new AISuggestionResult(false, "Ticket not found");
        }

        final AIAnalysisResult aiAnalysis = ticket.getAiAnalysis();
        if (aiAnalysis == null || aiAnalysis.getSuggestedAction() == null) {
            return new AISuggestionResult(false, "No AI suggestion to apply");
        }

        if (aiAnalysis.isWasAppliedAutomatically() || aiAnalysis.isDismissed()) {
            return new AISuggestionResult(false, "AI suggestion already handled");
        }

        if (ticket.getStatus() == TicketStatus.CLOSED || ticket.isLocked()) {
            return new AISuggestionResult(false, "Ticket already closed");
        }

        final UUID playerUuid = parsePlayerUuid(ticket.getReportedPlayerUuid());
        if (playerUuid == null) {
            return new AISuggestionResult(false, "No valid reported player UUID");
        }

        punishmentLifecycleService.validatePunishmentPermission(server, actingEmail, aiAnalysis.getSuggestedAction().getPunishmentTypeId());

        applyPunishmentAndCloseTicket(server, ticket, aiAnalysis, playerUuid, resolveIssuerName(server, actingEmail));
        ticketRepository.saveEntity(server, ticket);

        return new AISuggestionResult(true, null);
    }

    @NotNull
    private String resolveIssuerName(@NotNull Server server, @Nullable String email) {
        if (email == null) {
            return DEFAULT_ISSUER_NAME;
        }
        return staffRepository.findByEmailIgnoreCase(server, email)
            .map(Staff::getUsername)
            .filter(name -> name != null && !name.isBlank())
            .orElse(DEFAULT_ISSUER_NAME);
    }

    @NotNull
    public AISuggestionResult dismissAISuggestion(@NotNull Server server, @NotNull String ticketId) {
        final Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);

        if (ticket == null) {
            return new AISuggestionResult(false, "Ticket not found");
        }

        if (ticket.getAiAnalysis() == null) {
            return new AISuggestionResult(false, "No AI analysis to dismiss");
        }

        ticket.getAiAnalysis().setDismissed(true);
        ticket.setUpdatedAt(new Date());
        ticketRepository.saveEntity(server, ticket);

        return new AISuggestionResult(true, null);
    }

    public record AISuggestionResult(boolean success, String error) {}
}
