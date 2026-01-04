package gg.modl.backend.turnstile;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TurnstileService {
    private final TurnstileConfiguration config;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean validateToken(String token, String remoteIp) {
        if (config.getSecretKey() == null || config.getSecretKey().isBlank()) {
            log.warn("Turnstile secret key not configured, skipping validation");
            return true;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", config.getSecretKey());
            body.add("response", token);
            if (remoteIp != null && !remoteIp.isEmpty()) {
                body.add("remoteip", remoteIp);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            TurnstileResponse response = restTemplate.postForObject(
                    config.getVerifyUrl(),
                    request,
                    TurnstileResponse.class
            );

            if (response == null) {
                log.error("Turnstile validation returned null response");
                return false;
            }

            if (!response.success()) {
                log.warn("Turnstile validation failed: {}", response.errorCodes());
            }

            return response.success();
        } catch (Exception e) {
            log.error("Error validating Turnstile token", e);
            return false;
        }
    }

    public record TurnstileResponse(
            boolean success,
            @JsonProperty("challenge_ts") String challengeTs,
            String hostname,
            @JsonProperty("error-codes") List<String> errorCodes
    ) {}
}
