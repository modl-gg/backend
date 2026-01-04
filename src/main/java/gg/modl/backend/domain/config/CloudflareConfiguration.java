package gg.modl.backend.domain.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudflareConfiguration {

    @Value("${modl.cloudflare.api-token:}")
    private String apiToken;

    @Value("${modl.cloudflare.zone-id:}")
    private String zoneId;

    public String getApiToken() {
        return apiToken;
    }

    public String getZoneId() {
        return zoneId;
    }

    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank() && zoneId != null && !zoneId.isBlank();
    }
}
