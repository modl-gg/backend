package gg.modl.backend.log.service;

import gg.modl.backend.database.mongo.repository.ServerLogMongoRepository;
import gg.modl.backend.log.data.ServerLog;
import gg.modl.backend.log.dto.response.SystemLogResponse;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogService {
    private final ServerLogMongoRepository serverLogRepository;

    private static final int MAX_LIMIT = 500;
    private static final String LEVEL_MODERATION = "moderation";
    private static final String LEVEL_INFO = "info";
    private static final String SOURCE_SYSTEM = "system";

    public List<SystemLogResponse> getLogs(Server server, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<ServerLog> logs = serverLogRepository.findRecent(server, safeLimit);

        return logs.stream()
            .map(l -> new SystemLogResponse(
                l.getId(),
                l.getDescription(),
                l.getLevel(),
                l.getSource(),
                l.getCreated()
            ))
            .toList();
    }

    public void recordModerationAction(Server server, String source, String description) {
        write(server, description, LEVEL_MODERATION, source);
    }

    public void recordStaffAction(Server server, String source, String description) {
        write(server, description, LEVEL_INFO, source);
    }

    private void write(Server server, String description, String level, String source) {
        ServerLog entry = ServerLog.builder()
            .description(description)
            .level(level)
            .source(source != null && !source.isBlank() ? source : SOURCE_SYSTEM)
            .created(new Date())
            .build();
        try {
            serverLogRepository.saveEntity(server, entry);
        } catch (RuntimeException e) {
            log.error("Failed to record audit log entry [{}] for server {}", description, server.getDatabaseName(), e);
        }
    }
}
