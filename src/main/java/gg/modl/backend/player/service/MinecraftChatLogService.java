package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.fields.ChatLogFields;
import gg.modl.backend.database.mongo.fields.CommandLogFields;
import gg.modl.backend.database.mongo.repository.ChatLogMongoRepository;
import gg.modl.backend.database.mongo.repository.CommandLogMongoRepository;
import gg.modl.backend.player.data.log.ChatLogDocument;
import gg.modl.backend.player.data.log.CommandLogDocument;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MinecraftChatLogService {
    private static final int MAX_FETCH_LIMIT = 500;

    private final ChatLogMongoRepository chatLogRepository;
    private final CommandLogMongoRepository commandLogRepository;

    public void submitChatLogs(Server server, List<ChatLogCommand> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        for (ChatLogCommand entry : entries) {
            chatLogRepository.saveEntity(server, ChatLogDocument.builder()
                    .uuid(entry.uuid())
                    .username(entry.username())
                    .message(entry.message())
                    .timestamp(entry.timestamp())
                    .server(entry.server() != null ? entry.server() : "")
                    .build());
        }
    }

    public void submitCommandLogs(Server server, List<CommandLogCommand> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        for (CommandLogCommand entry : entries) {
            commandLogRepository.saveEntity(server, CommandLogDocument.builder()
                    .uuid(entry.uuid())
                    .username(entry.username())
                    .command(entry.command())
                    .timestamp(entry.timestamp())
                    .server(entry.server() != null ? entry.server() : "")
                    .build());
        }
    }

    public List<ChatLogEntryView> getChatLogs(Server server, String uuid, int limit) {
        Query query = Query.query(MongoQueries.where(ChatLogFields.UUID).is(uuid));
        query.with(MongoQueries.sort(Sort.Direction.DESC, ChatLogFields.TIMESTAMP));
        query.limit(Math.min(limit, MAX_FETCH_LIMIT));

        return chatLogRepository.find(server, query).stream()
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
        Query query = Query.query(MongoQueries.where(CommandLogFields.UUID).is(uuid));
        query.with(MongoQueries.sort(Sort.Direction.DESC, CommandLogFields.TIMESTAMP));
        query.limit(Math.min(limit, MAX_FETCH_LIMIT));

        return commandLogRepository.find(server, query).stream()
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
}