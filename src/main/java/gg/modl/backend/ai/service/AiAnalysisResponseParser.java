package gg.modl.backend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.ai.data.AIAnalysisResult;
import java.util.Date;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisResponseParser {
    private final ObjectMapper objectMapper;

    @Nullable
    public AIAnalysisResult parseResponse(@NotNull String rawResponse) {
        try {
            final String jsonContent = extractJson(rawResponse);
            final JsonNode json = objectMapper.readTree(jsonContent);
            final String analysis = json.has("analysis") ? json.get("analysis").asText() : null;

            if (analysis == null) {
                return null;
            }

            AIAnalysisResult.SuggestedAction suggestedAction = null;
            if (json.has("suggestedAction") && !json.get("suggestedAction").isNull()) {
                JsonNode actionNode = json.get("suggestedAction");
                final Integer punishmentTypeId = parseNumber(actionNode, "punishmentTypeId", JsonNode::asInt, Integer::parseInt);
                final JsonNode sevNode = actionNode.path("severity");
                final String severity = (sevNode.isMissingNode() || sevNode.isNull()) ? null : sevNode.asText();

                if (punishmentTypeId != null && severity != null) {
                    suggestedAction = new AIAnalysisResult.SuggestedAction(punishmentTypeId, severity);
                }
            }

            final AIAnalysisResult result = new AIAnalysisResult(analysis, suggestedAction, new Date(), rawResponse);
            result.setConfidence(parseNumber(json, "confidence", JsonNode::asDouble, Double::parseDouble));
            return result;
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", rawResponse, e);
            return null;
        }
    }

    @Nullable
    private <T> T parseNumber(@Nullable JsonNode node, @Nullable String field,
                              @NotNull Function<JsonNode, T> fromNumber,
                              @NotNull Function<String, T> fromText) {
        if (node == null || field == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }

        final JsonNode value = node.get(field);
        if (value.isNumber()) {
            return fromNumber.apply(value);
        }

        if (value.isTextual()) {
            try {
                return fromText.apply(value.asText());
            } catch (NumberFormatException e) {
                log.warn("Non-numeric value for {}: {}", field, value.asText());
                return null;
            }
        }

        return null;
    }

    @NotNull
    private String extractJson(@NotNull String response) {
        final String trimmed = response.trim();
        final int start = trimmed.indexOf('{');
        final int end = trimmed.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }
}
