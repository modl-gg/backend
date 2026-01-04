package gg.modl.backend.player.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class IpGeolocationService {

    private static final String IP_API_URL = "https://pro.ip-api.com/json/%s?fields=status,message,countryCode,regionName,city,as,proxy,hosting";

    private final RestTemplate restTemplate;

    public IpGeolocationResult getIpInfo(String ipAddress) {
        try {
            String url = String.format(IP_API_URL, ipAddress);
            IpApiResponse response = restTemplate.getForObject(url, IpApiResponse.class);

            if (response == null || !"success".equals(response.status())) {
                log.warn("Failed to get IP info for {}: {}", ipAddress, response != null ? response.message() : "null response");
                return IpGeolocationResult.empty();
            }

            return new IpGeolocationResult(
                    response.countryCode(),
                    response.regionName(),
                    response.as(),
                    response.proxy(),
                    response.hosting()
            );
        } catch (Exception e) {
            log.error("Error fetching IP geolocation for {}", ipAddress, e);
            return IpGeolocationResult.empty();
        }
    }

    public record IpGeolocationResult(
            String country,
            String region,
            String asn,
            boolean proxy,
            boolean hosting
    ) {
        public static IpGeolocationResult empty() {
            return new IpGeolocationResult(null, null, null, false, false);
        }
    }

    private record IpApiResponse(
            String status,
            String message,
            String countryCode,
            String regionName,
            String city,
            String as,
            boolean proxy,
            boolean hosting
    ) {
    }
}
