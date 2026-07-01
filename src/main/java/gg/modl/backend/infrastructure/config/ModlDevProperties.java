package gg.modl.backend.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "modl.dev")
public class ModlDevProperties {
    private String serverDomain = "";
    private String serverName = "Local Test";
    private String seedAdminEmail = "admin@localtest.dev";
    private String seedApiKey = "";
}
