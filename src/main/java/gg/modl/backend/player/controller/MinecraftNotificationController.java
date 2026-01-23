package gg.modl.backend.player.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_NOTIFICATIONS)
@RequiredArgsConstructor
public class MinecraftNotificationController {
    private final DynamicMongoTemplateProvider mongoProvider;

    @PostMapping("/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeNotifications(
            @RequestBody AcknowledgeRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        if (request.playerUuid() == null || request.notificationIds() == null || request.notificationIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", "playerUuid and notificationIds are required"
            ));
        }

        Query query = Query.query(Criteria.where("minecraftUuid").is(request.playerUuid()));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.ok(Map.of(
                    "status", 200,
                    "success", true,
                    "message", "No player found, nothing to acknowledge"
            ));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pending = (List<Map<String, Object>>)
                player.getData().getOrDefault("pendingNotifications", List.of());

        List<Map<String, Object>> remaining = pending.stream()
                .filter(n -> {
                    Object id = n.get("id");
                    return id == null || !request.notificationIds().contains(id.toString());
                })
                .toList();

        Update update = new Update().set("data.pendingNotifications", remaining);
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Acknowledged " + (pending.size() - remaining.size()) + " notification(s)"
        ));
    }

    public record AcknowledgeRequest(
            String playerUuid,
            List<String> notificationIds,
            String acknowledgedAt
    ) {}
}
