package gg.modl.backend.player.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MojangApiService {

    private final HttpClient httpClient;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROFILE_BY_NAME_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_BY_UUID_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    public MojangApiService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public Optional<MojangProfile> lookupByUsername(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROFILE_BY_NAME_URL + username))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }

            return parseProfile(response.body());
        } catch (Exception e) {
            log.warn("Mojang API lookup by username '{}' failed", username, e);
            return Optional.empty();
        }
    }

    static Optional<MojangProfile> parseProfile(String json) {
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }

        JsonNode nameNode = root.get("name");
        JsonNode idNode = root.get("id");
        if (nameNode == null || !nameNode.isTextual() || idNode == null || !idNode.isTextual()) {
            return Optional.empty();
        }

        return Optional.of(new MojangProfile(nameNode.asText(), fromDashlessUuid(idNode.asText())));
    }

    private static UUID fromDashlessUuid(String id) {
        if (id.contains("-")) {
            return UUID.fromString(id);
        }
        String withDashes = id.replaceFirst(
            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
            "$1-$2-$3-$4-$5"
        );
        return UUID.fromString(withDashes);
    }

    public Optional<MojangProfile> lookupByUuid(String uuid) {
        try {
            String dashlessUuid = uuid.replace("-", "");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROFILE_BY_UUID_URL + dashlessUuid))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }

            return parseProfile(response.body());
        } catch (Exception e) {
            log.warn("Mojang API lookup by UUID '{}' failed", uuid, e);
            return Optional.empty();
        }
    }

    public record MojangProfile(String name, UUID uuid) {}
}
