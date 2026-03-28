package gg.modl.backend.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "modl.cors")
public class ModlCorsProperties {
    @NotNull
    private String systemOrigins = "https://modl.gg,https://admin.modl.gg,https://modl.top,https://admin.modl.top";

    @NotNull
    private String appDomains = "modl.gg,modl.top";
}
