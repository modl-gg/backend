package gg.modl.backend.ai.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import gg.modl.backend.ai.LLMConfiguration;
import gg.modl.backend.ai.data.DefaultPrompts;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class GeminiLLMProvider implements LLMProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Client client;
    private final GenerateContentConfig gemini;
    private final String geminiModelId;

    public GeminiLLMProvider(LLMConfiguration config) {
        this.client = Client.builder()
            .apiKey(config.getGeminiApiKey())
            .build();
        this.gemini = GenerateContentConfig.builder()
            .temperature(config.getGeminiTemperature())
            .topP(config.getGeminiTopP())
            .maxOutputTokens(config.getGeminiMaxOutputTokens())
            .responseMimeType("application/json")
            .responseSchema(parseSchema(DefaultPrompts.JSON_FORMAT))
            .build();
        this.geminiModelId = config.getGeminiModelId();
    }

    private static Schema parseSchema(String jsonSchema) {
        try {
            return MAPPER.readValue(jsonSchema, Schema.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON schema: " + e.getMessage(), e);
        }
    }

    @Override
    public @NotNull String generate(@NotNull String prompt) {
        final GenerateContentResponse result = client.models.generateContent(geminiModelId, prompt, gemini);

        return Objects.requireNonNull(result.text(), "Failed to get response from Gemini API.");
    }

    @Override
    public boolean isConnected() {
        return client != null;
    }
}
