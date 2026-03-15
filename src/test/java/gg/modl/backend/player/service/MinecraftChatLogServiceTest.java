package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

        ArgumentCaptor<ChatLogDocument> captor = ArgumentCaptor.forClass(ChatLogDocument.class);
        verify(chatLogRepository).saveEntity(any(Server.class), captor.capture());
        assertEquals("uuid-1", captor.getValue().getUuid());
        assertEquals("hello", captor.getValue().getMessage());
        assertEquals("lobby", captor.getValue().getServer());
    }

    @Test
    void getCommandLogsMapsTypedDocumentsToApiView() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(commandLogRepository.find(any(Server.class), any())).thenReturn(List.of(
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