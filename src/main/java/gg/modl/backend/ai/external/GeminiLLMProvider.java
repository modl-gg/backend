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
            .build();
        this.geminiModelId = config.getGeminiModelId();
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
