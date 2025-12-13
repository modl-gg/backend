package gg.modl.backend.ai;

import gg.modl.backend.ai.external.GeminiLLMProvider;
import gg.modl.backend.ai.external.LLMProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LLMService {
    private final LLMConfiguration config;
    private final LLMProvider llmProvider = new GeminiLLMProvider(config);


}
