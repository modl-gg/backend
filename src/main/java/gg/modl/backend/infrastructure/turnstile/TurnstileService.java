package gg.modl.backend.infrastructure.turnstile;

import com.fasterxml.jackson.annotation.JsonProperty;
import gg.modl.backend.infrastructure.config.ModlProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class TurnstileService {
    private final RestTemplate restTemplate;
    private final TurnstileConfiguration config;
    private final ModlProperties modlProperties;
    private volatile boolean warnedAboutMissingHostnames = false;

    public boolean validateToken(String token, String remoteIp) {
        if (config.getSecretKey() == null || config.getSecretKey().isBlank()) {
            if (modlProperties.isDevelopmentMode()) {
                log.warn("Turnstile secret key not configured in development mode, skipping validation");
                return true;
            }

            log.error("Turnstile secret key not configured, rejecting request");
            return false;
        }

        try {
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", config.getSecretKey());
            body.add("response", token);
            if (remoteIp != null && !remoteIp.isEmpty()) {
                body.add("remoteip", remoteIp);
            }

            final HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            final TurnstileResponse response = restTemplate.postForObject(
                config.getVerifyUrl(),
                request,
                TurnstileResponse.class
            );

            if (response == null) {
                log.error("Turnstile validation returned null response");
                return false;
            }

            if (!response.success()) {
                log.warn("Turnstile validation rejected token, error-codes={}", response.errorCodes());
                return false;
            }

            return isHostnameAllowed(response.hostname());
        } catch (Exception e) {
            log.error("Error validating Turnstile token", e);
            return false;
        }
    }

    private boolean isHostnameAllowed(String host) {
        final List<String> allowed = resolveAllowedHostnames();
        if (allowed.isEmpty()) {
            if (modlProperties.isDevelopmentMode()) {
                return true;
            }
            warnMissingHostnamePinningOnce();
            return false;
        }
        if (modlProperties.isDevelopmentMode()) {
            return true;
        }
        final boolean hostAllowed = host != null
            && allowed.stream().anyMatch(h -> h.equalsIgnoreCase(host.trim()));
        if (!hostAllowed) {
            log.warn("Turnstile token solved on unexpected hostname '{}', expected one of {}", host, allowed);
            return false;
        }
        return true;
    }

    private List<String> resolveAllowedHostnames() {
        final List<String> explicit = normalizeHostnames(config.getExpectedHostnames());
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return defaultHostnamesFromAppDomain();
    }

    private List<String> normalizeHostnames(List<String> hostnames) {
        if (hostnames == null) {
            return List.of();
        }
        return hostnames.stream()
            .filter(h -> h != null && !h.isBlank())
            .map(String::trim)
            .toList();
    }

    private List<String> defaultHostnamesFromAppDomain() {
        final String appDomain = modlProperties.getAppDomain();
        if (appDomain != null && !appDomain.isBlank()) {
            return List.of(appDomain.trim());
        }
        final String domain = modlProperties.getDomain();
        if (domain != null && !domain.isBlank()) {
            return List.of(domain.trim());
        }
        return List.of();
    }

    private void warnMissingHostnamePinningOnce() {
        if (warnedAboutMissingHostnames) {
            return;
        }
        warnedAboutMissingHostnames = true;
        log.error("Turnstile hostname pinning is not configured and no app domain is available; rejecting token. "
            + "Set CLOUDFLARE_TURNSTILE_HOSTNAMES (modl.turnstile.expected-hostnames) to the hostname(s) serving the widget.");
    }

    public record TurnstileResponse(
        boolean success,
        @JsonProperty("challenge_ts") String challengeTs,
        String hostname,
        @JsonProperty("error-codes") List<String> errorCodes
    ) {}
}
