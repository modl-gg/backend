package gg.modl.backend.infrastructure.turnstile;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "modl.turnstile")
public class TurnstileConfiguration {
    private String secretKey;
    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private List<String> expectedHostnames = new ArrayList<>();
}
