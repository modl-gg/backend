package gg.modl.backend.ai;

import gg.modl.backend.ai.external.GeminiLLMProvider;
import gg.modl.backend.ai.external.LLMProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LLMService {
    private final LLMConfiguration config;
    private LLMProvider llmProvider;

    @PostConstruct
    public void init() {
        this.llmProvider = new GeminiLLMProvider(config);
    }
}
