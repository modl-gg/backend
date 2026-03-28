package gg.modl.backend.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "modl")
public class ModlProperties {
    private boolean developmentMode;

    private String domain = "modl.gg";

    private String appDomain;
}
