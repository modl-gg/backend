package gg.modl.backend.log.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.log.data.SystemLog;
import gg.modl.backend.log.dto.response.SystemLogResponse;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogService {
    private final DynamicMongoTemplateProvider mongoProvider;

    public List<SystemLogResponse> getLogs(Server server, int limit) {
        MongoTemplate template = getTemplate(server);

        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "created"))
                .limit(limit);

        List<SystemLog> logs = template.find(query, SystemLog.class, CollectionName.LOGS);

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

    public SystemLog createLog(Server server, String description, String level, String source) {
        MongoTemplate template = getTemplate(server);

        SystemLog logEntry = SystemLog.builder()
                .description(description)
                .level(level != null ? level : "info")
                .source(source != null ? source : "system")
                .created(new Date())
                .build();

        return template.save(logEntry, CollectionName.LOGS);
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
