package gg.modl.backend.player.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.player.dto.request.AcknowledgeNotificationsRequest;
import gg.modl.backend.player.dto.request.CreateNoteRequest;
import gg.modl.backend.player.dto.response.AcknowledgeResult;
import gg.modl.backend.player.dto.response.CreateNoteResult;
import gg.modl.backend.player.dto.response.LinkedAccountsResult;
import gg.modl.backend.player.dto.response.OnlinePlayersResult;
import gg.modl.backend.player.dto.response.PaginatedNotesResult;
import gg.modl.backend.player.dto.response.PaginatedPunishmentsResult;
import gg.modl.backend.player.dto.response.PardonResult;
import gg.modl.backend.player.dto.response.PlayerFetchResult;
import gg.modl.backend.player.dto.response.PlayerLookupResult;
import gg.modl.backend.player.dto.response.PlayerLoginResult;
import gg.modl.backend.player.dto.response.PlayerProfileResult;
import gg.modl.backend.player.dto.response.PlayerReportsResult;
import gg.modl.backend.player.dto.response.PunishmentView;
import gg.modl.backend.player.dto.response.SimpleActionResult;
import gg.modl.backend.player.service.MinecraftPlayerService;
import gg.modl.backend.player.service.PlayerLookupService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class MinecraftPlayerV1EnvelopeGoldenTest {

    private static final ObjectMapper JSON = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    private static final Map<String, Object> PROFILE = Map.of("id", "player-id");
    private static final Map<String, Object> DATA = Map.of("minecraftUuid", UUID);
    private static final Map<String, Object> NOTE = Map.of("id", "note-1", "text", "hi");

    private MinecraftPlayerService playerService;
    private PlayerLookupService lookupService;
    private MinecraftPlayerController controller;
    private MinecraftNotificationController notificationController;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        playerService = mock(MinecraftPlayerService.class);
        lookupService = mock(PlayerLookupService.class);
        controller = new MinecraftPlayerController(playerService, lookupService);
        notificationController = new MinecraftNotificationController(playerService);
        request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestAttribute.SERVER)).thenReturn(mock(Server.class));
    }

    @Test
    void loginExistingPlayerOmitsEmptyOptionalSections() throws Exception {
        when(playerService.login(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PlayerLoginResult(200, List.of(), List.of(), List.of(), List.of()));

        ResponseEntity<Map<String, Object>> response = controller.login(
            new MinecraftPlayerController.LoginRequest(UUID, "Byteful", "1.2.3.4", null, null, null, null), request);

        assertEnvelope(response, 200, "{\"status\":200,\"activePunishments\":[],\"pendingNotifications\":[]}");
    }

    @Test
    void loginNewPlayerIncludesOptionalSectionsAndReturns201() throws Exception {
        when(playerService.login(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PlayerLoginResult(201, List.of(), List.of(), List.of("1.2.3.4"),
                List.of(Map.of("minecraftUuid", UUID))));

        ResponseEntity<Map<String, Object>> response = controller.login(
            new MinecraftPlayerController.LoginRequest(UUID, "Byteful", "1.2.3.4", null, null, null, null), request);

        assertEnvelope(response, 201,
            "{\"status\":201,\"activePunishments\":[],\"pendingNotifications\":[],"
                + "\"pendingIpLookups\":[\"1.2.3.4\"],\"pendingStatWipes\":[{\"minecraftUuid\":\"" + UUID + "\"}]}");
    }

    @Test
    void disconnectRendersSimpleSuccess() throws Exception {
        when(playerService.disconnect(any(), any(), anyLong()))
            .thenReturn(new SimpleActionResult(true));

        ResponseEntity<Map<String, Object>> response = controller.disconnect(
            new MinecraftPlayerController.DisconnectRequest(UUID, 5L, null), request);

        assertEnvelope(response, 200, "{\"status\":200,\"success\":true}");
    }

    @Test
    void getOnlinePlayersRendersPlayersEnvelope() throws Exception {
        when(playerService.getOnlinePlayers(any())).thenReturn(new OnlinePlayersResult(List.of()));

        ResponseEntity<Map<String, Object>> response = controller.getOnlinePlayers(request);

        assertEnvelope(response, 200, "{\"status\":200,\"players\":[]}");
    }

    @Test
    void getPlayerByUuidFoundRendersProfileEnvelope() throws Exception {
        when(lookupService.getPlayerByUuid(any(), any(), any(), any()))
            .thenReturn(new PlayerProfileResult.Found(PROFILE));

        ResponseEntity<Map<String, Object>> response = controller.getPlayerByUuid(UUID, null, null, request);

        assertEnvelope(response, 200, "{\"status\":200,\"profile\":{\"id\":\"player-id\"}}");
    }

    @Test
    void getPlayerByUuidNotFoundRendersFrozenErrorBody() throws Exception {
        when(lookupService.getPlayerByUuid(any(), any(), any(), any()))
            .thenReturn(new PlayerProfileResult.NotFound("Player not found"));

        ResponseEntity<Map<String, Object>> response = controller.getPlayerByUuid(UUID, null, null, request);

        assertEnvelope(response, 404, "{\"status\":404,\"message\":\"Player not found\"}");
    }

    @Test
    void getPlayerByQueryFoundRendersPlayerEnvelope() throws Exception {
        when(lookupService.getPlayerByMinecraftUuid(any(), any(), anyBoolean()))
            .thenReturn(new PlayerFetchResult.Found("Player found", PROFILE));

        ResponseEntity<Map<String, Object>> response = controller.getPlayerByQuery(UUID, true, request);

        assertEnvelope(response, 200, "{\"status\":200,\"message\":\"Player found\",\"player\":{\"id\":\"player-id\"}}");
    }

    @Test
    void getPlayerByQueryInvalidRequestRendersFrozenBadRequestBody() throws Exception {
        when(lookupService.getPlayerByMinecraftUuid(any(), any(), anyBoolean()))
            .thenReturn(new PlayerFetchResult.InvalidRequest("minecraftUuid parameter required"));

        ResponseEntity<Map<String, Object>> response = controller.getPlayerByQuery(UUID, true, request);

        assertEnvelope(response, 400, "{\"status\":400,\"message\":\"minecraftUuid parameter required\"}");
    }

    @Test
    void getPlayerByQueryNotFoundRendersFrozenErrorBody() throws Exception {
        when(lookupService.getPlayerByMinecraftUuid(any(), any(), anyBoolean()))
            .thenReturn(new PlayerFetchResult.NotFound("Player not found"));

        ResponseEntity<Map<String, Object>> response = controller.getPlayerByQuery(UUID, true, request);

        assertEnvelope(response, 404, "{\"status\":404,\"message\":\"Player not found\"}");
    }

    @Test
    void lookupPlayerFoundRendersDataEnvelope() throws Exception {
        when(lookupService.lookupPlayer(any(), any(), anyBoolean()))
            .thenReturn(new PlayerLookupResult.Found("Player found", DATA));

        ResponseEntity<Map<String, Object>> response = controller.lookupPlayer(
            new MinecraftPlayerController.LookupRequest("Byteful", true), request);

        assertEnvelope(response, 200, "{\"status\":200,\"message\":\"Player found\",\"data\":{\"minecraftUuid\":\"" + UUID + "\"}}");
    }

    @Test
    void lookupPlayerNotFoundRendersFrozenErrorBody() throws Exception {
        when(lookupService.lookupPlayer(any(), any(), anyBoolean()))
            .thenReturn(new PlayerLookupResult.NotFound("Player not found"));

        ResponseEntity<Map<String, Object>> response = controller.lookupPlayer(
            new MinecraftPlayerController.LookupRequest("Byteful", true), request);

        assertEnvelope(response, 404, "{\"status\":404,\"message\":\"Player not found\"}");
    }

    @Test
    void createNoteCreatedRendersSuccessEnvelope() throws Exception {
        when(playerService.createNote(any(), any(), any(), any(), any()))
            .thenReturn(new CreateNoteResult.Created("Note added"));

        ResponseEntity<Map<String, Object>> response = controller.createPlayerNote(
            UUID, new CreateNoteRequest("text", "Mod", null, null), request);

        assertEnvelope(response, 200, "{\"status\":200,\"success\":true,\"message\":\"Note added\"}");
    }

    @Test
    void createNoteNotFoundRendersFrozenErrorBody() throws Exception {
        when(playerService.createNote(any(), any(), any(), any(), any()))
            .thenReturn(new CreateNoteResult.NotFound("Player not found"));

        ResponseEntity<Map<String, Object>> response = controller.createPlayerNote(
            UUID, new CreateNoteRequest("text", "Mod", null, null), request);

        assertEnvelope(response, 404, "{\"status\":404,\"message\":\"Player not found\"}");
    }

    @Test
    void linkedAccountsPaginatedRendersFullEnvelope() throws Exception {
        when(lookupService.getLinkedAccounts(any(), any(), any(), any()))
            .thenReturn(new LinkedAccountsResult.Found(List.of(), 4, 2, true));

        ResponseEntity<Map<String, Object>> response = controller.getLinkedAccounts(UUID, 2, 3, request);

        assertEnvelope(response, 200,
            "{\"status\":200,\"linkedAccounts\":[],\"totalCount\":4,\"page\":2,\"hasMore\":true}");
    }

    @Test
    void linkedAccountsUnpaginatedOmitsPaginationKeys() throws Exception {
        when(lookupService.getLinkedAccounts(any(), any(), any(), any()))
            .thenReturn(new LinkedAccountsResult.Found(List.of(), null, null, null));

        ResponseEntity<Map<String, Object>> response = controller.getLinkedAccounts(UUID, null, null, request);

        assertEnvelope(response, 200, "{\"status\":200,\"linkedAccounts\":[]}");
    }

    @Test
    void linkedAccountsNotFoundRendersFrozenErrorBody() throws Exception {
        when(lookupService.getLinkedAccounts(any(), any(), any(), any()))
            .thenReturn(new LinkedAccountsResult.NotFound("Player not found"));

        ResponseEntity<Map<String, Object>> response = controller.getLinkedAccounts(UUID, null, null, request);

        assertEnvelope(response, 404, "{\"status\":404,\"message\":\"Player not found\"}");
    }

    @Test
    void getPlayerPunishmentsFoundRendersPaginatedEnvelope() throws Exception {
        PunishmentView view = new PunishmentView("p-1", "Mod", new Date(1_600_000_000_000L), null,
            1, "Ban", List.of(), List.of(), List.of(), List.of(), Map.of(), null, null);
        when(playerService.getPlayerPunishments(any(), any(), anyInt(), anyInt()))
            .thenReturn(new PaginatedPunishmentsResult.Found(List.of(view), 1, 1, false));

        ResponseEntity<Map<String, Object>> response = controller.getPlayerPunishments(UUID, 1, 7, request);

        assertEquals(200, response.getStatusCode().value());
        JsonNode body = JSON.readTree(JSON.writeValueAsString(response.getBody()));
        assertEquals(200, body.get("status").asInt());
        assertEquals(1, body.get("totalCount").asInt());
        assertEquals(1, body.get("page").asInt());
        assertEquals(false, body.get("hasMore").asBoolean());
        assertEquals("p-1", body.get("punishments").get(0).get("id").asText());
    }

    @Test
    void getPlayerPunishmentsNotFoundRendersFrozenErrorBody() throws Exception {
        when(playerService.getPlayerPunishments(any(), any(), anyInt(), anyInt()))
            .thenReturn(new PaginatedPunishmentsResult.NotFound("Player not found"));

        ResponseEntity<Map<String, Object>> response = controller.getPlayerPunishments(UUID, 1, 7, request);

        assertEnvelope(response, 404, "{\"status\":404,\"message\":\"Player not found\"}");
    }

    @Test
    void getPlayerNotesFoundRendersPaginatedEnvelope() throws Exception {
        when(playerService.getPlayerNotes(any(), any(), anyInt(), anyInt()))
            .thenReturn(new PaginatedNotesResult.Found(List.of(NOTE), 1, 1, false));

        ResponseEntity<Map<String, Object>> response = controller.getPlayerNotes(UUID, 1, 7, request);

        assertEnvelope(response, 200,
            "{\"status\":200,\"notes\":[{\"id\":\"note-1\",\"text\":\"hi\"}],\"totalCount\":1,\"page\":1,\"hasMore\":false}");
    }

    @Test
    void getPlayerNotesNotFoundRendersFrozenErrorBody() throws Exception {
        when(playerService.getPlayerNotes(any(), any(), anyInt(), anyInt()))
            .thenReturn(new PaginatedNotesResult.NotFound("Player not found"));

        ResponseEntity<Map<String, Object>> response = controller.getPlayerNotes(UUID, 1, 7, request);

        assertEnvelope(response, 404, "{\"status\":404,\"message\":\"Player not found\"}");
    }

    @Test
    void getPlayerReportsRendersReportsEnvelope() throws Exception {
        when(playerService.getPlayerReports(any(), any())).thenReturn(new PlayerReportsResult(List.of()));

        ResponseEntity<Map<String, Object>> response = controller.getPlayerReports(UUID, request);

        assertEnvelope(response, 200, "{\"status\":200,\"reports\":[]}");
    }

    @Test
    void pardonPardonedRendersSuccessEnvelope() throws Exception {
        when(playerService.pardonPlayer(any(), any(), any(), any(), any(), any()))
            .thenReturn(new PardonResult.Pardoned(true, 1, "Pardoned 1 punishment(s)"));

        ResponseEntity<Map<String, Object>> response = controller.pardonPlayer(
            new MinecraftPlayerController.PardonPlayerRequest("Byteful", null, null, null, null), request);

        assertEnvelope(response, 200,
            "{\"status\":200,\"success\":true,\"pardonedCount\":1,\"message\":\"Pardoned 1 punishment(s)\"}");
    }

    @Test
    void pardonPlayerNotFoundRendersFrozenErrorBody() throws Exception {
        when(playerService.pardonPlayer(any(), any(), any(), any(), any(), any()))
            .thenReturn(new PardonResult.PlayerNotFound("Player not found"));

        ResponseEntity<Map<String, Object>> response = controller.pardonPlayer(
            new MinecraftPlayerController.PardonPlayerRequest("Byteful", null, null, null, null), request);

        assertEnvelope(response, 404, "{\"status\":404,\"message\":\"Player not found\"}");
    }

    @Test
    void acknowledgeRendersSuccessEnvelope() throws Exception {
        when(playerService.acknowledgeNotifications(any(), any(AcknowledgeNotificationsRequest.class)))
            .thenReturn(new AcknowledgeResult("Acknowledged 1 notification(s)"));

        ResponseEntity<Map<String, Object>> response = notificationController.acknowledgeNotifications(
            new AcknowledgeNotificationsRequest(UUID, List.of("n-1"), null), request);

        assertEnvelope(response, 200,
            "{\"status\":200,\"success\":true,\"message\":\"Acknowledged 1 notification(s)\"}");
    }

    private void assertEnvelope(ResponseEntity<Map<String, Object>> response, int status, String expectedJson) throws Exception {
        assertEquals(status, response.getStatusCode().value());
        assertEquals(JSON.readTree(expectedJson), JSON.readTree(JSON.writeValueAsString(response.getBody())));
    }
}
