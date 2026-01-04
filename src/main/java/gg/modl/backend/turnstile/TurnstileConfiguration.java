package gg.modl.backend.turnstile;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "modl.turnstile")
public class TurnstileConfiguration {
    private String secretKey;
    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
}
