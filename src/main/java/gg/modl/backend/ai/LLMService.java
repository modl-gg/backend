package gg.modl.backend.ai;

import gg.modl.backend.ai.external.GeminiLLMProvider;
import gg.modl.backend.ai.external.LLMProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LLMService {
    private final LLMConfiguration config;
    private LLMProvider llmProvider;

    @PostConstruct
    public void init() {
        if (config.getGeminiApiKey() != null && !config.getGeminiApiKey().isBlank()) {
            this.llmProvider = new GeminiLLMProvider(config);
            log.info("LLM service initialized with Gemini provider");
        } else {
            log.warn("LLM service not initialized: Gemini API key not configured");
        }
    }

    @NotNull
    public String generate(@NotNull String systemInstruction, @NotNull String userContent) {
        if (!isAvailable()) {
            throw new IllegalStateException("LLM provider not initialized. Check API key configuration.");
        }

        return llmProvider.generate(systemInstruction, userContent);
    }

    public boolean isAvailable() {
        return llmProvider != null && llmProvider.isConnected();
    }
}
