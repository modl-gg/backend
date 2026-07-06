package gg.modl.backend.ai.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import gg.modl.backend.ai.LLMConfiguration;
import gg.modl.backend.ai.data.DefaultPrompts;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class GeminiLLMProvider implements LLMProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Client client;
    private final String geminiModelId;
    private final Schema responseSchema;
    private final float temperature;
    private final float topP;
    private final int maxOutputTokens;

    public GeminiLLMProvider(LLMConfiguration config) {
        this.client = Client.builder()
            .apiKey(config.getGeminiApiKey())
            .build();
        this.responseSchema = parseSchema(DefaultPrompts.JSON_FORMAT);
        this.temperature = config.getGeminiTemperature();
        this.topP = config.getGeminiTopP();
        this.maxOutputTokens = config.getGeminiMaxOutputTokens();
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
    public @NotNull String generate(@NotNull String systemInstruction, @NotNull String userContent) {
        final GenerateContentConfig requestConfig = GenerateContentConfig.builder()
            .temperature(temperature)
            .topP(topP)
            .maxOutputTokens(maxOutputTokens)
            .responseMimeType("application/json")
            .responseSchema(responseSchema)
            .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
            .build();

        final GenerateContentResponse result = client.models.generateContent(geminiModelId, userContent, requestConfig);

        return Objects.requireNonNull(result.text(), "Failed to get response from Gemini API.");
    }

    @Override
    public boolean isConnected() {
        return client != null;
    }
}
