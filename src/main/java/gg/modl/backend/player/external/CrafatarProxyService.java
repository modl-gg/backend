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

    private final RestTemplate restTemplate;

    private static final int MIN_AVATAR_SIZE = 8;
    private static final int MAX_AVATAR_SIZE = 512;

    public byte[] getAvatar(String uuid, int size, boolean overlay) {
        int clampedSize = Math.max(MIN_AVATAR_SIZE, Math.min(size, MAX_AVATAR_SIZE));

        try {
            String url = String.format(CRAFATAR_URL, uuid, clampedSize, overlay);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    byte[].class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Error fetching avatar for UUID {}", uuid, e);
            return null;
        }
    }
}
