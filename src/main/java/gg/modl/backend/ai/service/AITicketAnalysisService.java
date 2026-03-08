package gg.modl.backend.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.ai.LLMService;
import gg.modl.backend.ai.data.AIAnalysisResult;
import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.SystemPromptMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.player.service.PunishmentService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.settings.data.AIModerationSettings.AIPunishmentConfig;
import gg.modl.backend.settings.service.AIModerationSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketNote;
import gg.modl.backend.ticket.data.TicketReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AITicketAnalysisService {
    private static final String JSON_FORMAT = """
            {
              "analysis": "Brief explanation of what rule violations (if any) were found in the chat",
              "suggestedAction": {
                "punishmentTypeId": "<punishment_type_id>",
                "severity": "low|regular|severe"
              } OR null if no action needed
            }""";

    private final LLMService llmService;
    private final AIModerationSettingsService aiModerationSettingsService;
    private final TicketMongoRepository ticketRepository;
    private final ServerMongoRepository serverRepository;
    private final PunishmentService punishmentService;
    private final PunishmentTypeService punishmentTypeService;
    private final UsageTrackingService usageTrackingService;
    private final ObjectMapper objectMapper;
    private final SystemPromptMongoRepository systemPromptRepository;

    public record AISuggestionResult(boolean success, String error) {}

    @Async
    public void analyzeTicketAsync(Server server, String ticketId) {
        try {
            analyzeTicket(server, ticketId);
        } catch (Exception e) {
            log.error("Failed to analyze ticket {} for server {}", ticketId, server.getServerName(), e);
        }
    }

    public AIAnalysisResult analyzeTicket(Server server, String ticketId) {
        if (!shouldAnalyze(server)) {
            log.debug("Skipping AI analysis for ticket {}: preconditions not met", ticketId);
            return null;
        }

        final Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);

        if (ticket == null) {
            log.debug("Ticket {} not found for AI analysis", ticketId);
            return null;
        }

        if (!isChatReport(ticket)) {
            log.debug("Skipping AI analysis for ticket {}: not a chat report", ticketId);
            return null;
        }

        if (ticket.getChatMessages() == null || ticket.getChatMessages().isEmpty()) {
            log.debug("Skipping AI analysis for ticket {}: no chat messages", ticketId);
            return null;
        }

        final AIModerationSettings settings = aiModerationSettingsService.getAIModerationSettings(server);
        final String systemPrompt = getSystemPrompt(settings.getStrictnessLevel());
        final String chatLog = ticket.getChatMessages().stream().map(Ticket.ChatMessage::getContent).collect(Collectors.joining("\n"));
        final String fullPrompt = buildPrompt(systemPrompt, chatLog, ticket.getReportedPlayer(), settings);

        final String rawResponse;
        try {
            rawResponse = llmService.generate(fullPrompt);
            usageTrackingService.incrementAiRequests(server.getId(), 1);
        } catch (Exception e) {
            log.error("LLM generation failed for ticket {}", ticketId, e);
            return null;
        }

        final AIAnalysisResult result = parseResponse(rawResponse);
        result.setCreatedAt(new Date());
        result.setRawResponse(rawResponse);

        if (settings.isEnableAutomatedActions() && result.hasViolation()) {
            executeAutomatedAction(server, ticket, result, settings);
        }

        ticket.setAiAnalysis(result);
        ticket.setUpdatedAt(new Date());
        ticketRepository.saveEntity(server, ticket);

        return result;
    }

    public AISuggestionResult applyAISuggestion(Server server, String ticketId, String staffName) {
        final Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);

        if (ticket == null) {
            return new AISuggestionResult(false, "Ticket not found");
        }

        AIAnalysisResult aiAnalysis = ticket.getAiAnalysis();
        if (aiAnalysis == null || aiAnalysis.getSuggestedAction() == null) {
            return new AISuggestionResult(false, "No AI suggestion to apply");
        }

        if (aiAnalysis.isWasAppliedAutomatically() || aiAnalysis.isDismissed()) {
            return new AISuggestionResult(false, "AI suggestion already handled");
        }

        if (ticket.getReportedPlayerUuid() == null || ticket.getReportedPlayerUuid().isBlank()) {
            return new AISuggestionResult(false, "No reported player UUID");
        }
        applyPunishmentAndCloseTicket(server, ticket, aiAnalysis, staffName);
        ticketRepository.saveEntity(server, ticket);
        return new AISuggestionResult(true, null);
    }

    public AISuggestionResult dismissAISuggestion(Server server, String ticketId) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);

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

    private boolean shouldAnalyze(Server server) {
        if (!llmService.isAvailable()) {
            log.debug("LLM service not available");
            return false;
        }

        if (server.getPlan() != ServerPlan.PREMIUM) {
            log.debug("Server {} is not on premium plan", server.getServerName());
            return false;
        }

        AIModerationSettings settings = aiModerationSettingsService.getAIModerationSettings(server);
        if (!settings.isEnableAIReview()) {
            log.debug("AI review is disabled for server {}", server.getServerName());
            return false;
        }

        // Check AI usage cap, fetch fresh server data for current period counts
        Server freshServer = serverRepository.findById(server.getId()).orElse(null);
        if (freshServer != null) {
            long currentUsage = freshServer.getAiRequestsCurrentPeriod() != null ? freshServer.getAiRequestsCurrentPeriod() : 0L;
            long limit = usageTrackingService.getAiRequestLimit(freshServer);
            if (currentUsage >= limit) {
                log.debug("Server {} has reached AI request limit ({}/{})", server.getServerName(), currentUsage, limit);
                return false;
            }
        }

        return true;
    }

    private boolean isChatReport(Ticket ticket) {
        return "chat".equalsIgnoreCase(ticket.getCategory());
    }

    private void applyPunishmentAndCloseTicket(Server server, Ticket ticket, AIAnalysisResult aiAnalysis, String staffName) {
        UUID playerUuid = UUID.fromString(ticket.getReportedPlayerUuid());
        AIAnalysisResult.SuggestedAction suggestion = aiAnalysis.getSuggestedAction();
        String reason = aiAnalysis.getAnalysis() != null ? aiAnalysis.getAnalysis() : "AI-detected violation";

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

        punishmentService.createPunishment(server, playerUuid, request);

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

        aiAnalysis.setWasAppliedAutomatically(true);
        ensureTicketReplies(ticket).add(systemReply);
        ensureTicketNotes(ticket).add(staffNote);
        ticket.setStatus("Closed");
        ticket.setLocked(true);
        ticket.setUpdatedAt(now);
    }

    private void executeAutomatedAction(Server server, Ticket ticket, AIAnalysisResult result, AIModerationSettings settings) {
        AIAnalysisResult.SuggestedAction suggestion = result.getSuggestedAction();
        if (suggestion == null || suggestion.getPunishmentTypeId() == null) {
            return;
        }

        if (ticket.getReportedPlayerUuid() == null || ticket.getReportedPlayerUuid().isBlank()) {
            log.warn("Cannot execute automated action: no reported player UUID for ticket {}", ticket.getId());
            return;
        }

        if (settings.getAiPunishmentConfigs() == null) {
            log.warn("No punishment configs available for automated action on ticket {}", ticket.getId());
            return;
        }

        String typeKey = String.valueOf(suggestion.getPunishmentTypeId());
        AIPunishmentConfig punishmentConfig = settings.getAiPunishmentConfigs().get(typeKey);

        if (punishmentConfig == null || !punishmentConfig.isEnabled()) {
            log.warn("Punishment config not found or disabled for type ordinal {}", typeKey);
            return;
        }

        try {
            applyPunishmentAndCloseTicket(server, ticket, result, "AI Moderator");
            result.setWasAppliedAutomatically(true);
        } catch (Exception e) {
            log.error("Failed to apply automated punishment for ticket {}", ticket.getId(), e);
        }
    }

    private List<TicketReply> ensureTicketReplies(Ticket ticket) {
        if (ticket.getReplies() == null) {
            ticket.setReplies(new ArrayList<>());
        }
        return ticket.getReplies();
    }

    private List<TicketNote> ensureTicketNotes(Ticket ticket) {
        if (ticket.getNotes() == null) {
            ticket.setNotes(new ArrayList<>());
        }
        return ticket.getNotes();
    }

    private String getSystemPrompt(String strictnessLevel) {
        String normalizedStrictnessLevel = strictnessLevel == null
                ? "STANDARD"
                : strictnessLevel.trim().toUpperCase(Locale.ROOT);
        SystemPrompt prompt = systemPromptRepository.findActiveByStrictnessLevel(normalizedStrictnessLevel).orElse(null);

        if (prompt != null && prompt.getPrompt() != null && !prompt.getPrompt().isBlank()) {
            return prompt.getPrompt();
        }

        return getDefaultPrompt(normalizedStrictnessLevel);
    }

    public String getDefaultPrompt(String level) {
        String normalizedLevel = level == null ? "STANDARD" : level.trim().toUpperCase(Locale.ROOT);
        String modeInstruction = switch (normalizedLevel) {
            case "LENIENT" ->
                """
                LENIENT MODE - Additional Guidelines:
                - Give players the benefit of the doubt when context is unclear
                - Only suggest action for clear, obvious rule violations
                - Prefer warnings and lighter punishments for first-time offenses
                - Consider context and intent - friendly banter may not require action
                - Be more forgiving of minor language issues
                - Focus on patterns of behavior rather than isolated incidents

                If there's any ambiguity about whether something violates rules, err on the side of no action.
                """;
            case "STRICT" ->
                """
                STRICT MODE - Additional Guidelines:
                - Enforce rules rigorously with zero tolerance for violations
                - Take action on borderline cases that could negatively impact the community
                - Prefer higher severity punishments to maintain server standards
                - Consider even minor infractions as worthy of moderation action
                - Prioritize community safety and positive environment over individual leniency
                - Be proactive in preventing escalation of problematic behavior

                When in doubt, err on the side of taking moderation action to maintain high community standards.
                """;
            default ->
                """
                STANDARD MODE - Additional Guidelines:
                - Apply consistent moderation based on clear rule violations
                - Consider the severity and impact of violations on the community
                - Balance player behavior with server standards
                - Escalate punishment severity for repeat offenses when evident
                - Take context into account but enforce rules fairly
                - Focus on maintaining a positive gaming environment

                Apply appropriate action when rules are clearly violated, using good judgment for edge cases.
                """;
        };

        return """
               You are an AI moderator analyzing Minecraft server chat logs for rule violations. Analyze the provided chat transcript and determine if any moderation action is needed.

               RESPONSE FORMAT:
               You must respond with a valid JSON object in this exact format:
               {{JSON_FORMAT}}

               PUNISHMENT SEVERITY GUIDELINES:
               - "low": Minor infractions, first-time offenses, borderline cases
               - "regular": Clear rule violations, repeat minor offenses
               - "severe": Serious violations, multiple rule breaks, toxic behavior

               AVAILABLE PUNISHMENT TYPES:
               {{PUNISHMENT_TYPES}}

               Choose the most appropriate punishment type from the provided list based on the violation category and severity. Use the descriptions provided to understand when each punishment type is appropriate.

               %s
               """
            .formatted(modeInstruction);
    }

    private String buildPrompt(String systemPrompt, String chatLog, String reportedPlayer, AIModerationSettings settings) {
        String punishmentTypes = formatPunishmentTypes(settings);

        if (systemPrompt.contains("{{")) {
            systemPrompt = systemPrompt
                    .replace("{{PUNISHMENT_TYPES}}", punishmentTypes)
                    .replace("{{JSON_FORMAT}}", JSON_FORMAT);
        }

        return """
                %s

                CHAT TRANSCRIPT TO ANALYZE:
                ```
                %s
                ```

                REPORTED PLAYER: %s

                Please analyze the chat transcript and respond with a JSON object following the exact format specified in the system prompt.
                """.formatted(systemPrompt, chatLog, reportedPlayer);
    }

    private String formatPunishmentTypes(AIModerationSettings settings) {
        if (settings.getAiPunishmentConfigs() == null || settings.getAiPunishmentConfigs().isEmpty()) {
            return "No punishment types configured";
        }

        return settings.getAiPunishmentConfigs().values().stream()
                .filter(AIPunishmentConfig::isEnabled)
                .map(config -> {
                    String description = config.getAiDescription();
                    return "%s: %s".formatted(
                            config.getId(),
                            description != null && !description.isBlank() ? description : config.getName()
                    );
                })
                .collect(Collectors.joining("\n"));
    }

    private AIAnalysisResult parseResponse(String rawResponse) {
        try {
            String jsonContent = extractJson(rawResponse);
            JsonNode json = objectMapper.readTree(jsonContent);

            String analysis = json.has("analysis") ? json.get("analysis").asText() : null;

            AIAnalysisResult.SuggestedAction suggestedAction = null;
            if (json.has("suggestedAction") && !json.get("suggestedAction").isNull()) {
                JsonNode actionNode = json.get("suggestedAction");
                suggestedAction = AIAnalysisResult.SuggestedAction.builder()
                        .punishmentTypeId(parseIntField(actionNode, "punishmentTypeId"))
                        .severity(actionNode.has("severity") ? actionNode.get("severity").asText() : null)
                        .build();
            }

            return AIAnalysisResult.builder()
                    .analysis(analysis)
                    .suggestedAction(suggestedAction)
                    .wasAppliedAutomatically(false)
                    .build();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI response: {}", rawResponse, e);
            return AIAnalysisResult.builder()
                    .analysis("Failed to parse AI response")
                    .wasAppliedAutomatically(false)
                    .build();
        }
    }

    private Integer parseIntField(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText());
            } catch (NumberFormatException e) {
                log.warn("Non-numeric value for {}: {}", field, value.asText());
                return null;
            }
        }
        return null;
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}