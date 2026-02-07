package gg.modl.backend.ai.external;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import gg.modl.backend.ai.LLMConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class GeminiLLMProvider implements LLMProvider {
    private static final String GEMINI_MODEL_ID = "gemini-2.5-flash-lite";

    private final Client client;
    private final LLMConfiguration configuration;

    public GeminiLLMProvider(LLMConfiguration config) {
        this.configuration = config;
        this.client = Client.builder()
                .apiKey(config.getGeminiApiKey())
                .build();
    }

    @Override
    public @NotNull String generate(@NotNull String prompt) {
        GenerateContentResponse result = client.models.generateContent(GEMINI_MODEL_ID, prompt, GenerateContentConfig.builder()
                .temperature(configuration.getGeminiTemperature())
                .topP(configuration.getGeminiTopP())
                .maxOutputTokens(configuration.getGeminiMaxOutputTokens())
                .thinkingConfig(ThinkingConfig.builder()
                    .thinkingLevel(ThinkingLevel.Known.LOW)
                    .includeThoughts(false)
                    .build())
                .build());

        return Objects.requireNonNull(result.text(), "Failed to get response from Gemini API.");
    }

    @Override
    public boolean isConnected() {
        return true; // TODO: test connection
    }
}
