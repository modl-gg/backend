package gg.modl.backend.domain.external;

import gg.modl.backend.domain.config.CloudflareConfiguration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class CloudflareClient {
    private final CloudflareConfiguration config;
    private final RestTemplate restTemplate;
    private static final String CLOUDFLARE_API = "https://api.cloudflare.com/client/v4";

    public CustomHostnameResult createCustomHostname(String hostname) {
        if (!config.isConfigured()) {
            log.warn("Cloudflare is not configured");
            return null;
        }

        try {
            HttpHeaders headers = createHeaders();

            Map<String, Object> ssl = Map.of(
                "method", "http",
                "type", "dv"
            );

            Map<String, Object> body = Map.of(
                "hostname", hostname,
                "ssl", ssl
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = CLOUDFLARE_API + "/zones/" + config.getZoneId() + "/custom_hostnames";

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Boolean success = (Boolean) response.getBody().get("success");
                if (Boolean.TRUE.equals(success)) {
                    return parseCustomHostnameResult(response.getBody());
                } else {
                    List<Map<String, Object>> errors = (List<Map<String, Object>>) response.getBody().get("errors");
                    if (errors != null && !errors.isEmpty()) {
                        log.error("Cloudflare API error creating custom hostname: {}", errors);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to create custom hostname for {}", hostname, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private CustomHostnameResult parseCustomHostnameResult(Map<String, Object> responseBody) {
        Map<String, Object> result = (Map<String, Object>) responseBody.get("result");
        if (result == null) {
            return null;
        }

        String id = (String) result.get("id");
        String hostname = (String) result.get("hostname");
        String status = (String) result.get("status");

        CustomHostnameResult.SslStatus sslStatus = null;
        Map<String, Object> ssl = (Map<String, Object>) result.get("ssl");
        if (ssl != null) {
            sslStatus = new CustomHostnameResult.SslStatus(
                (String) ssl.get("status"),
                (String) ssl.get("method"),
                (String) ssl.get("type")
            );
        }

        String ownershipHttpUrl = null;
        String ownershipHttpBody = null;
        Map<String, Object> ownershipVerification = (Map<String, Object>) result.get("ownership_verification");
        if (ownershipVerification != null) {
            ownershipHttpUrl = (String) ownershipVerification.get("http_url");
            ownershipHttpBody = (String) ownershipVerification.get("http_body");
        }

        return new CustomHostnameResult(
            id,
            hostname,
            status,
            sslStatus,
            ownershipHttpUrl,
            ownershipHttpBody
        );
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiToken());
        return headers;
    }

    public CustomHostnameResult getCustomHostname(String hostnameId) {
        if (!config.isConfigured()) {
            return null;
        }

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String url = CLOUDFLARE_API + "/zones/" + config.getZoneId() + "/custom_hostnames/" + hostnameId;
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Boolean success = (Boolean) response.getBody().get("success");
                if (Boolean.TRUE.equals(success)) {
                    return parseCustomHostnameResult(response.getBody());
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get custom hostname {}", hostnameId, e);
            return null;
        }
    }

    public CustomHostnameResult findCustomHostnameByName(String hostname) {
        if (!config.isConfigured()) {
            return null;
        }

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String url = CLOUDFLARE_API + "/zones/" + config.getZoneId() + "/custom_hostnames?hostname=" + hostname;
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Boolean success = (Boolean) response.getBody().get("success");
                if (Boolean.TRUE.equals(success)) {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("result");
                    if (results != null && !results.isEmpty()) {
                        Map<String, Object> modifiedBody = Map.of(
                            "success", true,
                            "result", results.get(0)
                        );
                        return parseCustomHostnameResult(modifiedBody);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to find custom hostname {}", hostname, e);
            return null;
        }
    }

    public boolean deleteCustomHostname(String hostnameId) {
        if (!config.isConfigured()) {
            return false;
        }

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String url = CLOUDFLARE_API + "/zones/" + config.getZoneId() + "/custom_hostnames/" + hostnameId;
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Boolean.TRUE.equals(response.getBody().get("success"));
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to delete custom hostname {}", hostnameId, e);
            return false;
        }
    }

    public record CustomHostnameResult(
        String id,
        String hostname,
        String status,
        SslStatus ssl,
        String ownershipVerificationHttpUrl,
        String ownershipVerificationHttpBody
    ) {
        public record SslStatus(String status, String method, String type) {}
    }
}
