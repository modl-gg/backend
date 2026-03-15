package gg.modl.backend.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "modl.llm")
@Validated
@Getter
@Setter
public class LLMConfiguration {
    private String geminiModelId;
    private String geminiApiKey;
    private float geminiTemperature;
    private int geminiMaxOutputTokens;
    private float geminiTopP;
}
