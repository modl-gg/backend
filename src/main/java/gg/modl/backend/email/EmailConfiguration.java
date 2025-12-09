package gg.modl.backend.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "modl.email")
@Getter
@Setter
public class EmailConfiguration {
    private String fromName;
    private String fromEmailAddress;
}
