package gg.modl.backend.cloudflare.external;

import gg.modl.backend.cloudflare.config.CloudflareConfiguration;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class CloudflareClient {
    private static final String CLOUDFLARE_API = "https://api.cloudflare.com/client/v4";
    private static final int HOSTNAME_PAGE_SIZE = 50;
    private static final int MAX_HOSTNAME_PAGES = 200;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE =
        new ParameterizedTypeReference<>() {};
    private final CloudflareConfiguration config;
    private final RestTemplate restTemplate;

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

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request, MAP_RESPONSE);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Boolean success = (Boolean) response.getBody().get("success");
                if (Boolean.TRUE.equals(success)) {
                    return parseCustomHostnameResult(response.getBody());
                } else {
                    Object errors = response.getBody().get("errors");
                    if (errors instanceof List<?> errorList && !errorList.isEmpty()) {
                        log.error("Cloudflare API error creating custom hostname: {}", errorList);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to create custom hostname for {}", hostname, e);
            return null;
        }
    }

    private CustomHostnameResult parseCustomHostnameResult(Map<String, Object> responseBody) {
        Map<?, ?> result = (Map<?, ?>) responseBody.get("result");
        if (result == null) {
            return null;
        }

        String id = (String) result.get("id");
        String hostname = (String) result.get("hostname");
        String status = (String) result.get("status");

        CustomHostnameResult.SslStatus sslStatus = null;
        Map<?, ?> ssl = (Map<?, ?>) result.get("ssl");
        if (ssl != null) {
            sslStatus = new CustomHostnameResult.SslStatus(
                (String) ssl.get("status"),
                (String) ssl.get("method"),
                (String) ssl.get("type")
            );
        }

        String ownershipHttpUrl = null;
        String ownershipHttpBody = null;
        Map<?, ?> ownershipVerification = (Map<?, ?>) result.get("ownership_verification");
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
        requireConfigured();

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String url = CLOUDFLARE_API + "/zones/" + config.getZoneId() + "/custom_hostnames/" + hostnameId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, request, MAP_RESPONSE);

            return parseCustomHostnameResult(requireSuccessBody(response, "get custom hostname " + hostnameId));
        } catch (HttpClientErrorException.NotFound notFound) {
            return null;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get custom hostname {}", hostnameId, e);
            throw new ExternalServiceException("Cloudflare API request failed.", e);
        }
    }

    public CustomHostnameResult findCustomHostnameByName(String hostname) {
        requireConfigured();

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String url = CLOUDFLARE_API + "/zones/" + config.getZoneId() + "/custom_hostnames?hostname=" + hostname;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, request, MAP_RESPONSE);

            Map<String, Object> body = requireSuccessBody(response, "find custom hostname " + hostname);
            Object resultsValue = body.get("result");
            if (resultsValue instanceof List<?> results && !results.isEmpty()) {
                return parseCustomHostnameResult(Map.of("success", true, "result", results.get(0)));
            }
            return null;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find custom hostname {}", hostname, e);
            throw new ExternalServiceException("Cloudflare API request failed.", e);
        }
    }

    private void requireConfigured() {
        if (!config.isConfigured()) {
            throw new ExternalServiceException("Cloudflare is not configured.");
        }
    }

    private Map<String, Object> requireSuccessBody(ResponseEntity<Map<String, Object>> response, String operation) {
        Map<String, Object> body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null || !Boolean.TRUE.equals(body.get("success"))) {
            log.error("Cloudflare API returned non-success for {}: {}", operation, body == null ? null : body.get("errors"));
            throw new ExternalServiceException("Cloudflare API request failed.");
        }
        return body;
    }

    public List<CustomHostnameResult> listAllCustomHostnames() {
        if (!config.isConfigured()) {
            return List.of();
        }

        List<CustomHostnameResult> hostnames = new ArrayList<>();
        HttpHeaders headers = createHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        int page = 1;

        while (page <= MAX_HOSTNAME_PAGES) {
            String url = CLOUDFLARE_API + "/zones/" + config.getZoneId()
                + "/custom_hostnames?page=" + page + "&per_page=" + HOSTNAME_PAGE_SIZE;
            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, request, MAP_RESPONSE);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                    || !Boolean.TRUE.equals(response.getBody().get("success"))) {
                    log.error("Cloudflare API returned non-success listing custom hostnames on page {}", page);
                    return List.of();
                }

                Object resultsValue = response.getBody().get("result");
                if (resultsValue instanceof List<?> results) {
                    for (Object entry : results) {
                        if (entry instanceof Map<?, ?>) {
                            CustomHostnameResult parsed = parseCustomHostnameResult(Map.of("success", true, "result", entry));
                            if (parsed != null) {
                                hostnames.add(parsed);
                            }
                        }
                    }
                }

                int totalPages = extractTotalPages(response.getBody());
                if (page >= totalPages) {
                    break;
                }
                page++;
            } catch (Exception e) {
                log.error("Failed to list custom hostnames on page {}", page, e);
                return List.of();
            }
        }
        return hostnames;
    }

    private int extractTotalPages(Map<String, Object> body) {
        if (body.get("result_info") instanceof Map<?, ?> resultInfo
            && resultInfo.get("total_pages") instanceof Number totalPages) {
            return totalPages.intValue();
        }
        return 1;
    }

    public boolean deleteCustomHostname(String hostnameId) {
        if (!config.isConfigured()) {
            return false;
        }

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String url = CLOUDFLARE_API + "/zones/" + config.getZoneId() + "/custom_hostnames/" + hostnameId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.DELETE, request, MAP_RESPONSE);

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
