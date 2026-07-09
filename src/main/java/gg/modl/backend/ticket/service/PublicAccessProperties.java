package gg.modl.backend.ticket.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "modl.public-access")
@Getter
@Setter
public class PublicAccessProperties {
    private Enforcement enforcement = Enforcement.COMPAT;
    private boolean appealTokenRequired = false;

    public enum Enforcement {
        COMPAT,
        STRICT
    }
}
