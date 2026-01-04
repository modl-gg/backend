package gg.modl.backend.domain.external;

import gg.modl.backend.domain.config.CloudflareConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CloudflareClient {
    private static final String CLOUDFLARE_API = "https://api.cloudflare.com/client/v4";

    private final CloudflareConfiguration config;
    private final RestTemplate restTemplate;

    public boolean addDnsRecord(String subdomain, String target) {
        if (!config.isConfigured()) {
            log.warn("Cloudflare is not configured");
            return false;
        }

        try {
            HttpHeaders headers = createHeaders();

            Map<String, Object> body = Map.of(
                    "type", "CNAME",
                    "name", subdomain,
                    "content", target,
                    "ttl", 1,
                    "proxied", true
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = CLOUDFLARE_API + "/zones/" + config.getZoneId() + "/dns_records";

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to add DNS record for {}", subdomain, e);
            return false;
        }
    }

    public boolean deleteDnsRecord(String subdomain) {
        if (!config.isConfigured()) {
            return false;
        }

        try {
            String recordId = findRecordId(subdomain);
            if (recordId == null) {
                return false;
            }

            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String url = CLOUDFLARE_API + "/zones/" + config.getZoneId() + "/dns_records/" + recordId;
            restTemplate.exchange(url, HttpMethod.DELETE, request, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete DNS record for {}", subdomain, e);
            return false;
        }
    }

    public boolean verifyDnsRecord(String subdomain) {
        if (!config.isConfigured()) {
            return false;
        }

        try {
            return findRecordId(subdomain) != null;
        } catch (Exception e) {
            log.error("Failed to verify DNS record for {}", subdomain, e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private String findRecordId(String subdomain) {
        HttpHeaders headers = createHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = CLOUDFLARE_API + "/zones/" + config.getZoneId() + "/dns_records?type=CNAME&name=" + subdomain;
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

        if (response.getBody() != null && Boolean.TRUE.equals(response.getBody().get("success"))) {
            List<Map<String, Object>> result = (List<Map<String, Object>>) response.getBody().get("result");
            if (result != null && !result.isEmpty()) {
                return (String) result.get(0).get("id");
            }
        }
        return null;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiToken());
        return headers;
    }
}
