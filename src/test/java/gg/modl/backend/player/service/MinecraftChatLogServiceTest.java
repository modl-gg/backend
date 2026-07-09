package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ChatLogMongoRepository;
import gg.modl.backend.database.mongo.repository.CommandLogMongoRepository;
import gg.modl.backend.player.data.log.ChatLogDocument;
import gg.modl.backend.player.data.log.CommandLogDocument;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinecraftChatLogServiceTest {

    @Mock
    private ChatLogMongoRepository chatLogRepository;

    @Mock
    private CommandLogMongoRepository commandLogRepository;

    private MinecraftChatLogService minecraftChatLogService;

    @BeforeEach
    void setUp() {
        minecraftChatLogService = new MinecraftChatLogService(chatLogRepository, commandLogRepository);
    }

    @Test
    void submitChatLogsPersistsTypedDocuments() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        minecraftChatLogService.submitChatLogs(server, List.of(
            new MinecraftChatLogService.ChatLogCommand("uuid-1", "PlayerOne", "hello", 10L, "lobby")
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatLogDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatLogRepository).insertAll(any(Server.class), captor.capture());
        assertEquals("uuid-1", captor.getValue().get(0).getUuid());
        assertEquals("hello", captor.getValue().get(0).getMessage());
        assertEquals("lobby", captor.getValue().get(0).getServer());
    }

    @Test
    void submitChatLogsLowercasesUuidBeforePersisting() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        minecraftChatLogService.submitChatLogs(server, List.of(
            new MinecraftChatLogService.ChatLogCommand("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", "PlayerOne", "hi", 1L, "lobby")
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatLogDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatLogRepository).insertAll(any(Server.class), captor.capture());
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", captor.getValue().get(0).getUuid());
    }

    @Test
    void submitCommandLogsLowercasesUuidBeforePersisting() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        minecraftChatLogService.submitCommandLogs(server, List.of(
            new MinecraftChatLogService.CommandLogCommand("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", "PlayerOne", "/help", 1L, "lobby")
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommandLogDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandLogRepository).insertAll(any(Server.class), captor.capture());
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", captor.getValue().get(0).getUuid());
    }

    @Test
    void getChatLogsLowercasesUuidBeforeQueryingRepository() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(chatLogRepository.findByUuidRecent(any(Server.class), any(), anyInt())).thenReturn(List.of());

        minecraftChatLogService.getChatLogs(server, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", 100);

        verify(chatLogRepository).findByUuidRecent(eq(server), eq("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), eq(100));
    }

    @Test
    void getCommandLogsLowercasesUuidBeforeQueryingRepository() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(commandLogRepository.findByUuidRecent(any(Server.class), any(), anyInt())).thenReturn(List.of());

        minecraftChatLogService.getCommandLogs(server, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", 100);

        verify(commandLogRepository).findByUuidRecent(eq(server), eq("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), eq(100));
    }

    @Test
    void getCommandLogsMapsTypedDocumentsToApiView() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(commandLogRepository.findByUuidRecent(any(Server.class), any(), anyInt())).thenReturn(List.of(
            CommandLogDocument.builder()
                .uuid("uuid-2")
                .username("PlayerTwo")
                .command("/msg hi")
                .timestamp(42L)
                .server("survival")
                .build()
        ));

        List<MinecraftChatLogService.CommandLogEntryView> response = minecraftChatLogService.getCommandLogs(server, "uuid-2", 200);

        assertEquals(1, response.size());
        assertEquals("/msg hi", response.get(0).command());
        assertEquals("survival", response.get(0).server());
    }
}