package gg.modl.backend.cloudflare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "modl.cloudflare")
@Validated
@Getter
@Setter
public class CloudflareConfiguration {
    private String apiToken = "";
    private String zoneId = "";

    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank() && zoneId != null && !zoneId.isBlank();
    }
}
