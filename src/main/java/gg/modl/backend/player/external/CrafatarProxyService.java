package gg.modl.backend.player.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrafatarProxyService {

    private static final String CRAFATAR_URL = "https://crafatar.com/avatars/%s?size=%d&overlay=%s";
    private static final String MINOTAR_FALLBACK_URL = "https://minotar.net/avatar/%s/%d";

    private final RestTemplate restTemplate;

    private static final int MIN_AVATAR_SIZE = 8;
    private static final int MAX_AVATAR_SIZE = 512;

    public byte[] getAvatar(String uuid, int size, boolean overlay) {
        int clampedSize = Math.max(MIN_AVATAR_SIZE, Math.min(size, MAX_AVATAR_SIZE));

        // Try Crafatar first
        try {
            String url = String.format(CRAFATAR_URL, uuid, clampedSize, overlay);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    byte[].class
            );

            if (response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Crafatar failed for UUID {}, trying Minotar fallback", uuid);
        }

        // Try Minotar as fallback
        try {
            String fallbackUrl = String.format(MINOTAR_FALLBACK_URL, uuid, clampedSize);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    fallbackUrl,
                    HttpMethod.GET,
                    null,
                    byte[].class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Both Crafatar and Minotar failed for UUID {}", uuid, e);
            return null;
        }
    }
}
