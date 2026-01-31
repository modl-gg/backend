package gg.modl.backend.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.ai.LLMService;
import gg.modl.backend.ai.data.AIAnalysisResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.player.service.PunishmentService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.settings.service.AIModerationSettingsService;
import gg.modl.backend.ticket.data.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AITicketAnalysisService {
    private static final String PROMPTS_COLLECTION = "system_prompts";

    private final LLMService llmService;
    private final AIModerationSettingsService aiModerationSettingsService;
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PunishmentService punishmentService;
    private final ObjectMapper objectMapper;

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

        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = Query.query(Criteria.where("_id").is(ticketId));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null) {
            log.warn("Ticket {} not found for AI analysis", ticketId);
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

        AIModerationSettings settings = aiModerationSettingsService.getAIModerationSettings(server);
        String systemPrompt = getSystemPrompt(settings.getStrictnessLevel());
        String formattedMessages = formatChatMessages(ticket.getChatMessages(), ticket.getReportedPlayer());
        String fullPrompt = buildPrompt(systemPrompt, formattedMessages, settings);

        log.info("Analyzing chat report ticket {} with AI (strictness: {})", ticketId, settings.getStrictnessLevel());

        String rawResponse;
        try {
            rawResponse = llmService.generate(fullPrompt);
        } catch (Exception e) {
            log.error("LLM generation failed for ticket {}", ticketId, e);
            return null;
        }

        AIAnalysisResult result = parseResponse(rawResponse);
        result.setAnalyzedAt(new Date());
        result.setRawResponse(rawResponse);

        if (settings.isEnableAutomatedActions() && result.isViolationDetected()) {
            executeAutomatedAction(server, ticket, result, settings);
        }

        Update update = new Update().set("aiAnalysis", result).set("updatedAt", new Date());
        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        log.info("AI analysis complete for ticket {}: violation={}, severity={}",
                ticketId, result.isViolationDetected(), result.getSeverity());

        return result;
    }

    private boolean shouldAnalyze(Server server) {
        if (!llmService.isAvailable()) {
            log.debug("LLM service not available");
            return false;
        }

        if (server.getPlan() != ServerPlan.premium) {
            log.debug("Server {} is not on premium plan", server.getServerName());
            return false;
        }

        AIModerationSettings settings = aiModerationSettingsService.getAIModerationSettings(server);
        if (!settings.isEnableAIReview()) {
            log.debug("AI review is disabled for server {}", server.getServerName());
            return false;
        }

        return true;
    }

    private boolean isChatReport(Ticket ticket) {
        return "chat".equalsIgnoreCase(ticket.getCategory());
    }

    private String getSystemPrompt(String strictnessLevel) {
        MongoTemplate template = mongoProvider.getGlobalDatabase();
        Query query = Query.query(Criteria.where("strictnessLevel").is(strictnessLevel).and("isActive").is(true));
        SystemPrompt prompt = template.findOne(query, SystemPrompt.class, PROMPTS_COLLECTION);

        if (prompt != null && prompt.getPrompt() != null && !prompt.getPrompt().isBlank()) {
            return prompt.getPrompt();
        }

        return getDefaultPrompt(strictnessLevel);
    }

    private String getDefaultPrompt(String level) {
        String common = """
            You are an AI moderator analyzing Minecraft server chat logs for rule violations.
            Analyze the provided chat transcript and determine if any moderation action is needed.
            """;

        return switch (level) {
            case "lenient" -> common + "\n\nLENIENT MODE: Give players significant benefit of the doubt. Only suggest action for clear, obvious rule violations.";
            case "strict" -> common + "\n\nSTRICT MODE: Enforce rules rigorously with minimal tolerance for violations. Prefer higher severity punishments.";
            default -> common + "\n\nSTANDARD MODE: Apply consistent moderation based on community standards. Balance individual player behavior with overall server atmosphere.";
        };
    }

    private String formatChatMessages(List<Map<String, Object>> chatMessages, String reportedPlayer) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CHAT LOG ===\n");
        if (reportedPlayer != null) {
            sb.append("Reported Player: ").append(reportedPlayer).append("\n\n");
        }

        for (Map<String, Object> msg : chatMessages) {
            String username = msg.getOrDefault("username", "Unknown").toString();
            String message = msg.getOrDefault("message", "").toString();
            String timestamp = msg.containsKey("timestamp") ? msg.get("timestamp").toString() : "";

            if (!timestamp.isEmpty()) {
                sb.append("[").append(timestamp).append("] ");
            }
            sb.append(username).append(": ").append(message).append("\n");
        }

        return sb.toString();
    }

    private String buildPrompt(String systemPrompt, String chatLog, AIModerationSettings settings) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt).append("\n\n");
        sb.append(chatLog).append("\n\n");

        sb.append("=== AVAILABLE ACTIONS ===\n");
        if (settings.getAiPunishmentConfigs() != null && !settings.getAiPunishmentConfigs().isEmpty()) {
            for (Map.Entry<String, AIModerationSettings.AIPunishmentConfig> entry : settings.getAiPunishmentConfigs().entrySet()) {
                AIModerationSettings.AIPunishmentConfig config = entry.getValue();
                if (config.isEnabled()) {
                    sb.append("- ").append(config.getName());
                    if (config.getAiDescription() != null && !config.getAiDescription().isBlank()) {
                        sb.append(": ").append(config.getAiDescription());
                    }
                    sb.append(" (id: ").append(config.getId()).append(")\n");
                }
            }
        } else {
            sb.append("- warn: Issue a warning\n");
            sb.append("- mute: Temporarily mute the player\n");
            sb.append("- kick: Kick the player from the server\n");
            sb.append("- ban: Ban the player from the server\n");
        }

        sb.append("\n=== RESPONSE FORMAT ===\n");
        sb.append("""
            Respond with a JSON object containing:
            {
              "violationDetected": true/false,
              "violationType": "type of violation if any (e.g., harassment, spam, hate speech, advertising)",
              "severity": "none/low/medium/high/critical",
              "recommendedAction": "action id or 'none'",
              "explanation": "brief explanation of your analysis",
              "confidence": 0.0-1.0
            }

            Only output the JSON object, no additional text.
            """);

        return sb.toString();
    }

    private AIAnalysisResult parseResponse(String rawResponse) {
        AIAnalysisResult result = AIAnalysisResult.builder()
                .violationDetected(false)
                .severity("none")
                .recommendedAction("none")
                .confidence(0.0)
                .actionTaken(false)
                .build();

        try {
            String jsonContent = extractJson(rawResponse);
            JsonNode json = objectMapper.readTree(jsonContent);

            if (json.has("violationDetected")) {
                result.setViolationDetected(json.get("violationDetected").asBoolean(false));
            }
            if (json.has("violationType")) {
                result.setViolationType(json.get("violationType").asText());
            }
            if (json.has("severity")) {
                result.setSeverity(json.get("severity").asText("none"));
            }
            if (json.has("recommendedAction")) {
                result.setRecommendedAction(json.get("recommendedAction").asText("none"));
            }
            if (json.has("explanation")) {
                result.setExplanation(json.get("explanation").asText());
            }
            if (json.has("confidence")) {
                result.setConfidence(json.get("confidence").asDouble(0.0));
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI response as JSON: {}", rawResponse, e);
            result.setExplanation("Failed to parse AI response");
        }

        return result;
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

    private void executeAutomatedAction(Server server, Ticket ticket, AIAnalysisResult result, AIModerationSettings settings) {
        String recommendedAction = result.getRecommendedAction();
        if (recommendedAction == null || "none".equalsIgnoreCase(recommendedAction)) {
            return;
        }

        if (ticket.getReportedPlayerUuid() == null || ticket.getReportedPlayerUuid().isBlank()) {
            log.warn("Cannot execute automated action: no reported player UUID for ticket {}", ticket.getId());
            return;
        }

        AIModerationSettings.AIPunishmentConfig punishmentConfig = null;
        Integer typeOrdinal = null;

        if (settings.getAiPunishmentConfigs() != null) {
            for (Map.Entry<String, AIModerationSettings.AIPunishmentConfig> entry : settings.getAiPunishmentConfigs().entrySet()) {
                AIModerationSettings.AIPunishmentConfig config = entry.getValue();
                if (config.isEnabled() && config.getId() != null && config.getId().equals(recommendedAction)) {
                    punishmentConfig = config;
                    try {
                        typeOrdinal = Integer.parseInt(entry.getKey());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid punishment type ordinal: {}", entry.getKey());
                    }
                    break;
                }
            }
        }

        if (typeOrdinal == null) {
            log.warn("Could not find punishment config for action: {}", recommendedAction);
            return;
        }

        try {
            UUID playerUuid = UUID.fromString(ticket.getReportedPlayerUuid());
            CreatePunishmentRequest request = new CreatePunishmentRequest(
                    "AI Moderator",
                    typeOrdinal,
                    null,
                    null,
                    List.of(ticket.getId()),
                    result.getSeverity(),
                    "active",
                    Map.of(
                            "reason", result.getViolationType() != null ? result.getViolationType() : "AI-detected violation",
                            "aiGenerated", true,
                            "confidence", result.getConfidence()
                    )
            );

            punishmentService.createPunishment(server, playerUuid, request);

            result.setActionTaken(true);
            result.setActionDetails("Applied punishment: " + punishmentConfig.getName());
            result.setPunishmentId(ticket.getId());

            log.info("Automated punishment applied for ticket {}: {} on player {}",
                    ticket.getId(), punishmentConfig.getName(), ticket.getReportedPlayer());

        } catch (Exception e) {
            log.error("Failed to apply automated punishment for ticket {}", ticket.getId(), e);
            result.setActionTaken(false);
            result.setActionDetails("Failed to apply punishment: " + e.getMessage());
        }
    }
}
