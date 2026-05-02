package gg.modl.backend.player.service;

import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.database.mongo.repository.ServerInstanceSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.player.controller.MinecraftStartupController.StartupRequest;
import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.Topic;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinecraftStartupService {
    private static final List<Topic> MINECRAFT_STARTUP_TOPICS = Arrays.asList(
        Topic.TOPIC_MINECRAFT_PERMISSIONS,
        Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES
    );

    private final ModlProperties modlProperties;
    private final RealtimeProperties realtimeProperties;
    private final ServerMongoRepository serverRepository;
    private final ServerInstanceSnapshotMongoRepository serverInstanceSnapshotRepository;

    public Map<String, Object> handleStartup(Server server, StartupRequest request, String clientIp) {
        Instant now = Instant.now();

        // Build panel URL
        String domain = server.getCustomDomainOverride();
        if (domain == null || domain.isBlank()) {
            domain = server.getCustomDomain() + "." + modlProperties.getDomain();
        }
        String panelUrl = "https://" + domain;

        // Update lastActivityAt on the server
        serverRepository.updateFirst(
            Query.query(Criteria.where("_id").is(server.getId())),
            new Update().set("lastActivityAt", Date.from(now))
        );

        // Store metrics via ServerInstanceSnapshotMongoRepository
        try {
            long epochSeconds = now.getEpochSecond();
            Date fiveMinBoundary = Date.from(Instant.ofEpochSecond((epochSeconds / 300) * 300));
            serverInstanceSnapshotRepository.upsertServerEntry(
                fiveMinBoundary,
                server.getId(),
                request.serverName(),
                0,
                request.platformType(),
                request.serverVersion(),
                clientIp,
                request.pluginVersion(),
                Date.from(now)
            );
        } catch (Exception e) {
            log.warn("Failed to upsert server instance snapshot during startup", e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("panelUrl", panelUrl);
        result.put("timestamp", now.toString());
        result.put("serverInstanceId", UUID.randomUUID().toString());
        addRealtimeBootstrap(result);
        return result;
    }

    private void addRealtimeBootstrap(Map<String, Object> result) {
        String realtimeUrl = normalizedRealtimeUrl();
        boolean enabled = realtimeProperties.isEnabled() && realtimeUrl != null;

        result.put("realtimeEnabled", enabled);
        result.put("realtimeUrl", enabled ? realtimeUrl : null);
        result.put("realtimeProtocolVersion", realtimeProperties.getProtocolVersion());
        result.put("realtimeTopics", enabled ? topicNames(MINECRAFT_STARTUP_TOPICS) : List.of());
    }

    private String normalizedRealtimeUrl() {
        String publicUrl = realtimeProperties.getPublicUrl();
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        return publicUrl.trim();
    }

    private List<String> topicNames(List<Topic> topics) {
        return topics.stream()
            .map(Topic::name)
            .collect(Collectors.toList());
    }
}
