package gg.modl.backend.dashboard.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_DASHBOARD)
@RequiredArgsConstructor
public class MinecraftDashboardController {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats(HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        // Get unresolved reports count (tickets where type is report-related and status is open)
        Query reportQuery = Query.query(
                Criteria.where("type").in("PLAYER", "CHAT", "CHEATING", "BEHAVIOR")
                        .and("status").in("open", "pending", "in_progress")
        );
        long unresolvedReports = template.count(reportQuery, Ticket.class, CollectionName.TICKETS);

        // Get unresolved tickets count (support tickets that are open)
        Query ticketQuery = Query.query(
                Criteria.where("type").in("SUPPORT", "BUG", "APPEAL", "STAFF", "OTHER")
                        .and("status").in("open", "pending", "in_progress")
        );
        long unresolvedTickets = template.count(ticketQuery, Ticket.class, CollectionName.TICKETS);

        // Get online staff count (staff with assigned minecraft UUID who are online)
        Query onlineStaffQuery = Query.query(
                Criteria.where("assignedMinecraftUuid").exists(true).ne(null).ne("")
        );
        List<Staff> staffWithMinecraft = template.find(onlineStaffQuery, Staff.class, CollectionName.STAFF);

        // Check which staff are online by looking at their player records
        long onlineStaff = 0;
        for (Staff staff : staffWithMinecraft) {
            Query playerQuery = Query.query(
                    Criteria.where("minecraftUuid").is(staff.getAssignedMinecraftUuid())
                            .and("data.isOnline").is(true)
            );
            if (template.exists(playerQuery, Player.class, CollectionName.PLAYERS)) {
                onlineStaff++;
            }
        }

        // Get online players count
        Query onlinePlayersQuery = Query.query(Criteria.where("data.isOnline").is(true));
        long onlinePlayers = template.count(onlinePlayersQuery, Player.class, CollectionName.PLAYERS);

        // Get active punishments counts
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        Query playersWithPunishmentsQuery = Query.query(Criteria.where("punishments").exists(true).not().size(0));
        List<Player> playersWithPunishments = template.find(playersWithPunishmentsQuery, Player.class, CollectionName.PLAYERS);

        long activeBans = 0;
        long activeMutes = 0;
        long totalPunishments = 0;

        for (Player player : playersWithPunishments) {
            for (Punishment punishment : player.getPunishments()) {
                if (statusCalculator.isPunishmentActive(punishment)) {
                    totalPunishments++;
                    int ordinal = punishment.getType_ordinal();

                    boolean isBan = types.stream()
                            .filter(t -> t.getOrdinal() == ordinal)
                            .findFirst()
                            .map(PunishmentType::isBan)
                            .orElse(false);

                    boolean isMute = types.stream()
                            .filter(t -> t.getOrdinal() == ordinal)
                            .findFirst()
                            .map(PunishmentType::isMute)
                            .orElse(false);

                    if (isBan) activeBans++;
                    if (isMute) activeMutes++;
                }
            }
        }

        // Get total players count
        long totalPlayers = template.count(new Query(), Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "stats", Map.of(
                        "unresolvedReports", unresolvedReports,
                        "unresolvedTickets", unresolvedTickets,
                        "onlineStaff", onlineStaff,
                        "onlinePlayers", onlinePlayers,
                        "activeBans", activeBans,
                        "activeMutes", activeMutes,
                        "totalActivePunishments", totalPunishments,
                        "totalPlayers", totalPlayers
                )
        ));
    }
}
