package gg.modl.backend.player.controller;

import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.bson.Document;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_PLAYERS)
@RequiredArgsConstructor
@Slf4j
public class MinecraftChatLogController {

    private final DynamicMongoTemplateProvider mongoProvider;

    private static final String CHAT_LOGS_COLLECTION = "chat_logs";
    private static final String COMMAND_LOGS_COLLECTION = "command_logs";

    /**
     * Batch submit chat messages from the plugin.
     */
    @PostMapping("/chat-log")
    public ResponseEntity<Void> submitChatLogs(
            @RequestBody @Valid ChatLogBatchRequest request,
            HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        for (ChatLogEntry entry : request.entries()) {
            Document doc = new Document();
            doc.put("uuid", entry.uuid());
            doc.put("username", entry.username());
            doc.put("message", entry.message());
            doc.put("timestamp", entry.timestamp());
            doc.put("server", entry.server() != null ? entry.server() : "");
            template.save(doc, CHAT_LOGS_COLLECTION);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Batch submit commands from the plugin.
     */
    @PostMapping("/command-log")
    public ResponseEntity<Void> submitCommandLogs(
            @RequestBody @Valid CommandLogBatchRequest request,
            HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        for (CommandLogEntry entry : request.entries()) {
            Document doc = new Document();
            doc.put("uuid", entry.uuid());
            doc.put("username", entry.username());
            doc.put("command", entry.command());
            doc.put("timestamp", entry.timestamp());
            doc.put("server", entry.server() != null ? entry.server() : "");
            template.save(doc, COMMAND_LOGS_COLLECTION);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Get chat logs for a specific player.
     */
    @GetMapping("/{uuid}/chat-logs")
    public ResponseEntity<Map<String, Object>> getChatLogs(
            @PathVariable String uuid,
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("uuid").is(uuid))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(Math.min(limit, 500));

        List<Map> entries = template.find(query, Map.class, CHAT_LOGS_COLLECTION);

        return ResponseEntity.ok(Map.of("entries", entries));
    }

    /**
     * Get command logs for a specific player.
     */
    @GetMapping("/{uuid}/command-logs")
    public ResponseEntity<Map<String, Object>> getCommandLogs(
            @PathVariable String uuid,
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("uuid").is(uuid))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(Math.min(limit, 500));

        List<Map> entries = template.find(query, Map.class, COMMAND_LOGS_COLLECTION);

        return ResponseEntity.ok(Map.of("entries", entries));
    }

    // DTOs
    public record ChatLogBatchRequest(@NotNull List<ChatLogEntry> entries) {}
    public record ChatLogEntry(@NotBlank String uuid, @NotBlank String username, @NotBlank String message, long timestamp, String server) {}
    public record CommandLogBatchRequest(@NotNull List<CommandLogEntry> entries) {}
    public record CommandLogEntry(@NotBlank String uuid, @NotBlank String username, @NotBlank String command, long timestamp, String server) {}
}
