package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.ChatLogMongoRepository;
import gg.modl.backend.database.mongo.repository.CommandLogMongoRepository;
import gg.modl.backend.player.data.log.ChatLogDocument;
import gg.modl.backend.player.data.log.CommandLogDocument;
import gg.modl.backend.server.data.Server;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MinecraftChatLogService {
    private final ChatLogMongoRepository chatLogRepository;
    private final CommandLogMongoRepository commandLogRepository;
    private static final int MAX_FETCH_LIMIT = 500;

    public void submitChatLogs(Server server, List<ChatLogCommand> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        chatLogRepository.insertAll(server, entries.stream()
            .map(entry -> ChatLogDocument.builder()
                .uuid(normalizeUuid(entry.uuid()))
                .username(entry.username())
                .message(entry.message())
                .timestamp(entry.timestamp())
                .server(entry.server() != null ? entry.server() : "")
                .build())
            .toList());
    }

    public void submitCommandLogs(Server server, List<CommandLogCommand> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        commandLogRepository.insertAll(server, entries.stream()
            .map(entry -> CommandLogDocument.builder()
                .uuid(normalizeUuid(entry.uuid()))
                .username(entry.username())
                .command(entry.command())
                .timestamp(entry.timestamp())
                .server(entry.server() != null ? entry.server() : "")
                .build())
            .toList());
    }

    public List<ChatLogEntryView> getChatLogs(Server server, String uuid, int limit) {
        return chatLogRepository.findByUuidRecent(server, normalizeUuid(uuid), Math.min(limit, MAX_FETCH_LIMIT))
            .stream()
            .map(entry -> new ChatLogEntryView(
                entry.getUuid(),
                entry.getUsername(),
                entry.getMessage(),
                entry.getTimestamp(),
                entry.getServer()
            ))
            .toList();
    }

    public List<CommandLogEntryView> getCommandLogs(Server server, String uuid, int limit) {
        return commandLogRepository.findByUuidRecent(server, normalizeUuid(uuid), Math.min(limit, MAX_FETCH_LIMIT))
            .stream()
            .map(entry -> new CommandLogEntryView(
                entry.getUuid(),
                entry.getUsername(),
                entry.getCommand(),
                entry.getTimestamp(),
                entry.getServer()
            ))
            .toList();
    }

    public record ChatLogCommand(String uuid, String username, String message, long timestamp, String server) {
    }

    public record CommandLogCommand(String uuid, String username, String command, long timestamp, String server) {
    }

    public record ChatLogEntryView(String uuid, String username, String message, long timestamp, String server) {
    }

    public record CommandLogEntryView(String uuid, String username, String command, long timestamp, String server) {
    }

    private static String normalizeUuid(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}