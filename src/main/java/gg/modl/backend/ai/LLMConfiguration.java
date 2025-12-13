package gg.modl.backend.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "modl.llm")
@Getter
@Setter
public class LLMConfiguration {
    private String geminiApiKey;
    private float geminiTemperature;
    private int geminiMaxOutputTokens;
    private float geminiTopP;
}
