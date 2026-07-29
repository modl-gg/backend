package gg.modl.backend.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.ai.data.DefaultPrompts;
import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.settings.data.AIModerationSettings.AIPunishmentConfig;
import gg.modl.backend.ticket.data.Ticket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatModerationPromptBuilder {
    private static final String REPORTED_PLAYER_REFERENCE = "the reported player identified in the untrusted chat data";
    private static final SecureRandom NONCE_RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper;

    @Nullable
    public ModerationPrompt buildModerationPrompt(@NotNull Ticket ticket, @NotNull AIModerationSettings settings, @NotNull Supplier<String> systemPrompt) {
        final String nonce = generateNonce();
        final String beginMarker = "===BEGIN_UNTRUSTED_CHAT_DATA:" + nonce + "===";
        final String endMarker = "===END_UNTRUSTED_CHAT_DATA:" + nonce + "===";

        final String chatJson;
        try {
            chatJson = objectMapper.writeValueAsString(buildChatPayload(ticket));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize chat data for ticket {}", ticket.getId(), e);
            return null;
        }

        final String userContent = beginMarker + "\n" + chatJson + "\n" + endMarker;
        final String systemInstruction = systemPrompt.get()
            .replace("{{REPORTED_PLAYER}}", REPORTED_PLAYER_REFERENCE)
            .replace("{{PUNISHMENT_TYPES}}", formatPunishmentTypes(settings))
            .replace("{{CHAT_LOG}}", "")
            + "\n\n"
            + DefaultPrompts.UNTRUSTED_DATA_DIRECTIVE.formatted(beginMarker, endMarker);

        return new ModerationPrompt(systemInstruction, userContent);
    }

    @NotNull
    private Map<String, Object> buildChatPayload(@NotNull Ticket ticket) {
        final List<Map<String, Object>> messages = new ArrayList<>();
        for (Ticket.ChatMessage message : ticket.getChatMessages()) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sender", message.getSender());
            entry.put("content", message.getContent());
            messages.add(entry);
        }

        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportedPlayer", ticket.getReportedPlayer());
        payload.put("messages", messages);
        return payload;
    }

    @NotNull
    private static String generateNonce() {
        final byte[] bytes = new byte[16];
        NONCE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @NotNull
    private String formatPunishmentTypes(@NotNull AIModerationSettings settings) {
        if (settings.getAiPunishmentConfigs() == null || settings.getAiPunishmentConfigs().isEmpty()) {
            return "No punishment types configured";
        }

        return settings.getAiPunishmentConfigs().values()
            .stream()
            .filter(AIPunishmentConfig::isEnabled)
            .map(config -> {
                String description = config.getAiDescription();
                return "%s: (%s) %s".formatted(
                    config.getId(),
                    config.getName(),
                    description != null && !description.isBlank() ? description : config.getName()
                );
            })
            .collect(Collectors.joining("\n"));
    }

    public record ModerationPrompt(String systemInstruction, String userContent) {}
}
