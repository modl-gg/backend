package gg.modl.backend.player.external;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrafatarProxyService {

    private final RestTemplate restTemplate;
    private static final int MIN_AVATAR_SIZE = 8;
    private static final int MAX_AVATAR_SIZE = 512;
    private static final int MAX_AVATAR_BYTES = 1024 * 1024; // 1 MiB

    private static final long MAX_AVATAR_CACHE_BYTES = 64L * 1024 * 1024;

    private final Cache<String, byte[]> avatarCache = Caffeine.newBuilder()
        .maximumWeight(MAX_AVATAR_CACHE_BYTES)
        .weigher((String key, byte[] body) -> body.length)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build();

    public byte[] getAvatar(String uuid, int size, boolean overlay) {
        int clampedSize = Math.max(MIN_AVATAR_SIZE, Math.min(size, MAX_AVATAR_SIZE));
        String cacheKey = clampedSize + ":" + overlay + ":" + uuid;
        return avatarCache.get(cacheKey, key -> fetchAvatar(uuid, clampedSize, overlay));
    }

    private byte[] fetchAvatar(String uuid, int clampedSize, boolean overlay) {
        // Try Crafatar first. Values are passed as URI variables so a stray ?/&/# in uuid is
        // percent-encoded by DefaultUriBuilderFactory and never re-parsed as URL structure.
        try {
            URI url = UriComponentsBuilder.fromUriString("https://crafatar.com")
                .pathSegment("avatars", uuid)
                .queryParam("size", clampedSize)
                .queryParam("overlay", overlay)
                .build()
                .encode()
                .toUri();
            byte[] body = fetch(url);
            if (body != null) {
                return body;
            }
        } catch (Exception e) {
            log.warn("Crafatar failed for UUID {}, trying Minotar fallback", uuid);
        }

        // Try Minotar as fallback
        try {
            URI fallbackUrl = UriComponentsBuilder.fromUriString("https://minotar.net")
                .pathSegment("avatar", uuid, String.valueOf(clampedSize))
                .build()
                .encode()
                .toUri();
            return fetch(fallbackUrl);
        } catch (Exception e) {
            log.error("Both Crafatar and Minotar failed for UUID {}", uuid, e);
            return null;
        }
    }

    /**
     * Fetches an avatar with a content-type and size guard. Returns null (treated as a miss) when the
     * upstream response is non-image, oversized, or empty, so the unauthenticated proxy never buffers
     * unbounded bytes into heap and never serves a non-image body mislabeled as a PNG.
     */
    private byte[] fetch(URI url) {
        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
        HttpHeaders headers = response.getHeaders();
        if (headers.getContentLength() > MAX_AVATAR_BYTES) {
            log.warn("Avatar upstream {} reported content-length {} over cap {}", url, headers.getContentLength(), MAX_AVATAR_BYTES);
            return null;
        }
        MediaType contentType = headers.getContentType();
        if (contentType != null && !"image".equalsIgnoreCase(contentType.getType())) {
            log.warn("Avatar upstream {} returned non-image content-type {}", url, contentType);
            return null;
        }
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return null;
        }
        if (body.length > MAX_AVATAR_BYTES) {
            log.warn("Avatar upstream {} body length {} over cap {}", url, body.length, MAX_AVATAR_BYTES);
            return null;
        }
        return body;
    }
}
