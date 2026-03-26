package gg.modl.backend.player.service;

import gg.modl.backend.config.ModlProperties;
import gg.modl.backend.database.mongo.repository.ServerInstanceSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.player.controller.MinecraftStartupController.StartupRequest;
import gg.modl.backend.server.data.Server;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final ModlProperties modlProperties;
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
        return result;
    }
}
