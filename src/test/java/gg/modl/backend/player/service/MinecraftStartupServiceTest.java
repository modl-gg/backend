package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import gg.modl.backend.database.mongo.repository.ServerInstanceSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.player.controller.MinecraftStartupController.StartupRequest;
import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.proto.modl.v1.Topic;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MinecraftStartupServiceTest {

    @Test
    void startupKeepsPanelUrlAndOmitsRealtimeUrlWhenDisabled() {
        MinecraftStartupService service = service(properties(false, ""));
        Server server = server();

        Map<String, Object> response = service.handleStartup(server, request(), "127.0.0.1");

        assertEquals("https://demo.modl.gg", response.get("panelUrl"));
        assertNotNull(response.get("serverInstanceId"));
        assertTrue(((String) response.get("serverInstanceId")).length() >= 36);
        assertEquals(false, response.get("realtimeEnabled"));
        assertNull(response.get("realtimeUrl"));
        assertEquals(1, response.get("realtimeProtocolVersion"));
        assertIterableEquals(List.of(), (List<?>) response.get("realtimeTopics"));
    }

    @Test
    void startupExposesRealtimeMetadataWhenBackendEnabledAndUrlConfigured() {
        MinecraftStartupService service = service(properties(true, "wss://api.modl.gg/v1/realtime/ws"));
        Server server = server();

        Map<String, Object> response = service.handleStartup(server, request(), "127.0.0.1");

        assertEquals("https://demo.modl.gg", response.get("panelUrl"));
        assertEquals(true, response.get("realtimeEnabled"));
        assertEquals("wss://api.modl.gg/v1/realtime/ws", response.get("realtimeUrl"));
        assertEquals(1, response.get("realtimeProtocolVersion"));
        assertIterableEquals(
            List.of(
                Topic.TOPIC_MINECRAFT_PERMISSIONS.name(),
                Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES.name(),
                Topic.TOPIC_MINECRAFT_PUNISHMENTS.name(),
                Topic.TOPIC_MINECRAFT_PLAYER_NOTIFICATIONS.name(),
                Topic.TOPIC_MINECRAFT_STAFF_2FA.name(),
                Topic.TOPIC_MINECRAFT_MIGRATION_TASKS.name()
            ),
            (List<?>) response.get("realtimeTopics")
        );
    }

    @Test
    void startupDoesNotAdvertiseRealtimeWhenUrlIsBlank() {
        MinecraftStartupService service = service(properties(true, " "));
        Server server = server();

        Map<String, Object> response = service.handleStartup(server, request(), "127.0.0.1");

        assertEquals(false, response.get("realtimeEnabled"));
        assertNull(response.get("realtimeUrl"));
        assertFalse(((List<?>) response.get("realtimeTopics")).contains(Topic.TOPIC_MINECRAFT_PUNISHMENTS.name()));
    }

    private MinecraftStartupService service(RealtimeProperties realtimeProperties) {
        ModlProperties modlProperties = new ModlProperties();
        modlProperties.setDomain("modl.gg");
        ServerMongoRepository serverRepository = mock(ServerMongoRepository.class);
        ServerInstanceSnapshotMongoRepository snapshotRepository = mock(ServerInstanceSnapshotMongoRepository.class);
        return new MinecraftStartupService(modlProperties, realtimeProperties, serverRepository, snapshotRepository);
    }

    private RealtimeProperties properties(boolean enabled, String publicUrl) {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setEnabled(enabled);
        properties.setProtocolVersion(1);
        properties.setPublicUrl(publicUrl);
        return properties;
    }

    private Server server() {
        Server server = new Server("Demo", "demo", "demo_db", "admin@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }

    private StartupRequest request() {
        return new StartupRequest("1.21.4", "spigot", "2.2.2", 100, "Lobby");
    }
}
