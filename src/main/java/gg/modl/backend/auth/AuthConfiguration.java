package gg.modl.backend.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "modl.auth")
@Getter
@Setter
public class AuthConfiguration {
    private int emailCodeExpiry = 600;
    private int emailCodeLength = 6;
    private long sessionDurationSeconds = 14 * 24 * 60 * 60;
    private String sessionCookieName = "MODL_SESSION";
    private boolean cookieSecure = true;
    private boolean developmentMode = false;
}
