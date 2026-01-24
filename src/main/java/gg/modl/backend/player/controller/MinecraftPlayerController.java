package gg.modl.backend.player.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.PlayerResponseMessage;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.validation.RegExpConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_PLAYERS)
@RequiredArgsConstructor
public class MinecraftPlayerController {
    private final PlayerService playerService;
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody @Valid LoginRequest request,
            BindingResult bindingResult,
            HttpServletRequest httpRequest
    ) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "success", false,
                    "message", PlayerResponseMessage.LOGIN_INVALID_SCHEMA
            ));
        }

        Server server = RequestUtil.getRequestServer(httpRequest);
        Player player = playerService.loginPlayer(
                server,
                UUID.fromString(request.minecraftUUID()),
                request.username(),
                request.ip()
        );

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        List<Map<String, Object>> activePunishments = new ArrayList<>();

        for (Punishment punishment : player.getPunishments()) {
            if (statusCalculator.isPunishmentActive(punishment)) {
                activePunishments.add(toSimplePunishment(punishment, types));
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pendingNotifications = (List<Map<String, Object>>)
                player.getData().getOrDefault("pendingNotifications", List.of());

        boolean isNewPlayer = player.getUsernames().size() == 1;
        return ResponseEntity.status(isNewPlayer ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Map.of(
                        "status", isNewPlayer ? 201 : 200,
                        "activePunishments", activePunishments,
                        "pendingNotifications", pendingNotifications
                ));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(
            @RequestBody DisconnectRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("minecraftUuid").is(request.minecraftUuid()));
        Update update = new Update()
                .set("data.isOnline", false)
                .set("data.lastLogout", new Date());

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of("status", 200, "success", true));
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<Map<String, Object>> getPlayerByUuid(
            @PathVariable String uuid,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("minecraftUuid").is(uuid));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "profile", toPlayerProfile(player)
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPlayerByQuery(
            @RequestParam(required = false) String minecraftUuid,
            HttpServletRequest httpRequest
    ) {
        if (minecraftUuid == null || minecraftUuid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", "minecraftUuid parameter required"
            ));
        }

        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("minecraftUuid").is(minecraftUuid));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Player found",
                "player", toPlayerProfile(player)
        ));
    }

    @GetMapping("/by-name")
    public ResponseEntity<Map<String, Object>> getPlayerByUsername(
            @RequestParam String username,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("usernames.username").regex("^" + username + "$", "i"));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Player found",
                "player", toPlayerProfile(player)
        ));
    }

    @PostMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookupPlayer(
            @RequestBody LookupRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        String queryStr = request.query();
        Player player;

        if (queryStr.contains("-") && queryStr.length() == 36) {
            Query query = Query.query(Criteria.where("minecraftUuid").is(queryStr));
            player = template.findOne(query, Player.class, CollectionName.PLAYERS);
        } else {
            Query query = Query.query(Criteria.where("usernames.username").regex("^" + queryStr + "$", "i"));
            player = template.findOne(query, Player.class, CollectionName.PLAYERS);
        }

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        Map<String, Object> lookupData = buildLookupResponse(server, player, types);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Player found",
                "data", lookupData
        ));
    }

    @PostMapping("/{uuid}/notes")
    public ResponseEntity<Map<String, Object>> createPlayerNote(
            @PathVariable String uuid,
            @RequestBody @Valid CreateNoteRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("minecraftUuid").is(uuid));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }

        NoteEntry note = NoteEntry.builder()
                .text(request.text())
                .date(new Date())
                .issuerName(request.issuerName())
                .build();
        Update update = new Update().push("notes", note);
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Note added"
        ));
    }

    @GetMapping("/{uuid}/linked-accounts")
    public ResponseEntity<Map<String, Object>> getLinkedAccounts(
            @PathVariable String uuid,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("minecraftUuid").is(uuid));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }

        List<String> ips = player.getIpAddresses().stream()
                .map(ip -> ip.getIpAddress())
                .toList();

        List<Map<String, Object>> linkedAccounts = new ArrayList<>();
        if (!ips.isEmpty()) {
            Query ipQuery = Query.query(
                    Criteria.where("ipAddresses.ipAddress").in(ips)
                            .and("minecraftUuid").ne(uuid)
            );
            ipQuery.limit(20);

            List<Player> related = template.find(ipQuery, Player.class, CollectionName.PLAYERS);
            for (Player p : related) {
                linkedAccounts.add(toPlayerProfile(p));
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "linkedAccounts", linkedAccounts
        ));
    }

    @GetMapping("/{uuid}/reports")
    public ResponseEntity<Map<String, Object>> getPlayerReports(
            @PathVariable String uuid,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("reportedPlayerUuid").is(uuid));
        query.limit(50);

        List<Ticket> tickets = template.find(query, Ticket.class, CollectionName.TICKETS);

        List<Map<String, Object>> reports = tickets.stream().map(t -> {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("id", t.getId());
            report.put("type", t.getType());
            report.put("reporterName", t.getCreatorName());
            report.put("reporterUuid", t.getCreatorUuid());
            report.put("subject", t.getSubject());
            report.put("status", t.getStatus());
            report.put("priority", t.getPriority());
            report.put("createdAt", t.getCreated());
            return report;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "reports", reports
        ));
    }

    private Map<String, Object> toSimplePunishment(Punishment punishment, List<PunishmentType> types) {
        Map<String, Object> data = punishment.getData();
        Date expires = statusCalculator.getEffectiveExpiry(punishment);

        String typeName = types.stream()
                .filter(t -> t.getOrdinal() == punishment.getType_ordinal())
                .findFirst()
                .map(PunishmentType::getName)
                .orElse("Unknown");

        boolean isBan = types.stream()
                .filter(t -> t.getOrdinal() == punishment.getType_ordinal())
                .findFirst()
                .map(PunishmentType::isBan)
                .orElse(false);

        String category = isBan ? "BAN" : "OTHER";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", punishment.getId());
        result.put("type", typeName);
        result.put("category", category);
        result.put("ordinal", punishment.getType_ordinal());
        result.put("started", punishment.getStarted() != null);
        result.put("expiration", expires != null ? expires.getTime() : null);
        result.put("reason", data != null ? data.get("reason") : null);

        return result;
    }

    private Map<String, Object> toPlayerProfile(Player player) {
        // Convert usernames to the format expected by the plugin
        List<Map<String, Object>> usernames = player.getUsernames().stream()
                .map(u -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("username", u.username());
                    entry.put("date", u.date());
                    return entry;
                }).toList();

        // Convert notes to the format expected by the plugin
        List<Map<String, Object>> notes = player.getNotes().stream()
                .map(n -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", n.getId());
                    entry.put("text", n.getText());
                    entry.put("date", n.getDate());
                    entry.put("issuerName", n.getIssuerName());
                    entry.put("issuerId", n.getIssuerId());
                    return entry;
                }).toList();

        // Convert IP addresses (ipAddresses -> ipList for plugin compatibility)
        List<Map<String, Object>> ipList = player.getIpAddresses().stream()
                .map(ip -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("ipAddress", ip.getIpAddress());
                    entry.put("country", ip.getCountry());
                    entry.put("region", ip.getRegion());
                    entry.put("asn", ip.getAsn());
                    entry.put("proxy", ip.isProxy());
                    entry.put("hosting", ip.isHosting());
                    entry.put("firstLogin", ip.getFirstLogin());
                    entry.put("logins", ip.getLogins());
                    return entry;
                }).toList();

        // Convert punishments to the format expected by the plugin
        List<Map<String, Object>> punishments = player.getPunishments().stream()
                .map(this::toPunishmentMap).toList();

        // Get pending notifications from player data
        @SuppressWarnings("unchecked")
        List<String> pendingNotifications = (List<String>) player.getData()
                .getOrDefault("pendingNotifications", Collections.emptyList());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("_id", player.getId());
        profile.put("minecraftUuid", player.getMinecraftUuid().toString());
        profile.put("usernames", usernames);
        profile.put("notes", notes);
        profile.put("ipList", ipList);
        profile.put("punishments", punishments);
        profile.put("pendingNotifications", pendingNotifications);

        return profile;
    }

    private Map<String, Object> toPunishmentMap(Punishment punishment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", punishment.getId());
        map.put("issuerName", punishment.getIssuerName());
        map.put("issued", punishment.getIssued());
        map.put("started", punishment.getStarted());

        // Convert type_ordinal to Type enum name for plugin compatibility
        String typeName = switch (punishment.getType_ordinal()) {
            case 0 -> "KICK";
            case 1 -> "MUTE";
            case 2 -> "BAN";
            case 3 -> "SECURITY_BAN";
            case 4 -> "LINKED_BAN";
            case 5 -> "BLACKLIST";
            default -> "KICK";
        };
        map.put("type", typeName);

        // Convert modifications
        List<Map<String, Object>> modifications = punishment.getModifications().stream()
                .map(m -> {
                    Map<String, Object> mod = new LinkedHashMap<>();
                    mod.put("id", m.getId());
                    mod.put("type", m.getType());
                    mod.put("date", m.getDate());
                    mod.put("issuerName", m.getIssuerName());
                    mod.put("data", m.getData());
                    return mod;
                }).toList();
        map.put("modifications", modifications);

        // Convert notes
        List<Map<String, Object>> notes = punishment.getNotes().stream()
                .map(n -> {
                    Map<String, Object> note = new LinkedHashMap<>();
                    note.put("id", n.getId());
                    note.put("text", n.getText());
                    note.put("issuerName", n.getIssuerName());
                    note.put("date", n.getDate());
                    return note;
                }).toList();
        map.put("notes", notes);

        map.put("attachedTicketIds", punishment.getAttachedTicketIds());
        map.put("data", punishment.getData() != null ? punishment.getData() : Collections.emptyMap());

        return map;
    }

    private Map<String, Object> buildLookupResponse(Server server, Player player, List<PunishmentType> types) {
        String currentUsername = player.getUsernames().isEmpty() ? "Unknown"
                : player.getUsernames().get(player.getUsernames().size() - 1).username();

        List<String> previousUsernames = player.getUsernames().stream()
                .map(u -> u.username())
                .skip(1)
                .toList();

        Date firstSeen = player.getUsernames().isEmpty() ? null
                : player.getUsernames().get(0).date();
        Date lastSeen = player.getUsernames().isEmpty() ? null
                : player.getUsernames().get(player.getUsernames().size() - 1).date();

        int totalPunishments = player.getPunishments().size();
        int activePunishments = (int) player.getPunishments().stream()
                .filter(statusCalculator::isPunishmentActive).count();

        int bans = 0, mutes = 0, kicks = 0, warnings = 0;
        for (Punishment p : player.getPunishments()) {
            int ordinal = p.getType_ordinal();
            boolean isBan = types.stream().filter(t -> t.getOrdinal() == ordinal).findFirst().map(PunishmentType::isBan).orElse(false);
            boolean isMute = types.stream().filter(t -> t.getOrdinal() == ordinal).findFirst().map(PunishmentType::isMute).orElse(false);
            boolean isKick = types.stream().filter(t -> t.getOrdinal() == ordinal).findFirst().map(PunishmentType::isKick).orElse(false);

            if (isBan) bans++;
            else if (isMute) mutes++;
            else if (isKick) kicks++;
            else warnings++;
        }

        List<Map<String, Object>> recentPunishments = player.getPunishments().stream()
                .sorted((a, b) -> b.getIssued().compareTo(a.getIssued()))
                .limit(5)
                .map(p -> {
                    String typeName = types.stream()
                            .filter(t -> t.getOrdinal() == p.getType_ordinal())
                            .findFirst()
                            .map(PunishmentType::getName)
                            .orElse("Unknown");

                    Map<String, Object> punishment = new LinkedHashMap<>();
                    punishment.put("id", p.getId());
                    punishment.put("type", typeName);
                    punishment.put("issuer", p.getIssuerName());
                    punishment.put("issuedAt", p.getIssued());
                    punishment.put("expiresAt", statusCalculator.getEffectiveExpiry(p));
                    punishment.put("isActive", statusCalculator.isPunishmentActive(p));
                    return punishment;
                }).toList();

        String baseUrl = "https://" + server.getCustomDomain();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("minecraftUuid", player.getMinecraftUuid().toString());
        data.put("currentUsername", currentUsername);
        data.put("previousUsernames", previousUsernames);
        data.put("firstSeen", firstSeen);
        data.put("lastSeen", lastSeen);
        data.put("isOnline", player.getData().getOrDefault("isOnline", false));
        data.put("punishmentStats", Map.of(
                "totalPunishments", totalPunishments,
                "activePunishments", activePunishments,
                "bans", bans,
                "mutes", mutes,
                "kicks", kicks,
                "warnings", warnings
        ));
        data.put("recentPunishments", recentPunishments);
        data.put("profileUrl", baseUrl + "/player/" + player.getMinecraftUuid());
        data.put("punishmentsUrl", baseUrl + "/player/" + player.getMinecraftUuid() + "/punishments");

        return data;
    }

    @PostMapping("/pardon")
    public ResponseEntity<Map<String, Object>> pardonPlayer(
            @RequestBody PardonPlayerRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("usernames.username").regex("^" + request.playerName() + "$", "i"));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        int pardoned = 0;

        for (Punishment punishment : player.getPunishments()) {
            if (!statusCalculator.isPunishmentActive(punishment)) continue;

            boolean shouldPardon = request.punishmentType() == null;
            if (!shouldPardon && request.punishmentType() != null) {
                String pType = request.punishmentType().toLowerCase();
                int ordinal = punishment.getType_ordinal();
                boolean isBan = types.stream().filter(t -> t.getOrdinal() == ordinal).findFirst().map(PunishmentType::isBan).orElse(false);
                boolean isMute = types.stream().filter(t -> t.getOrdinal() == ordinal).findFirst().map(PunishmentType::isMute).orElse(false);

                if (pType.equals("ban") && isBan) shouldPardon = true;
                if (pType.equals("mute") && isMute) shouldPardon = true;
            }

            if (shouldPardon) {
                Query updateQuery = Query.query(
                        Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                                .and("punishments.id").is(punishment.getId())
                );

                gg.modl.backend.player.data.punishment.PunishmentModification modification =
                        new gg.modl.backend.player.data.punishment.PunishmentModification(
                                "MANUAL_PARDON",
                                new Date(),
                                request.issuerName(),
                                request.reason() != null ? request.reason() : "",
                                null,
                                null
                        );

                Update update = new Update()
                        .push("punishments.$.modifications", modification)
                        .set("punishments.$.data.active", false);

                template.updateFirst(updateQuery, update, Player.class, CollectionName.PLAYERS);
                pardoned++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Pardoned " + pardoned + " punishment(s)"
        ));
    }

    public record LoginRequest(
            @Pattern(regexp = RegExpConstants.UUID) String minecraftUUID,
            @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String username,
            @Pattern(regexp = RegExpConstants.IP) String ip
    ) {}

    public record DisconnectRequest(String minecraftUuid) {}

    public record LookupRequest(String query) {}

    public record CreateNoteRequest(
            @NotBlank String text,
            @NotBlank String issuerName
    ) {}

    public record PardonPlayerRequest(
            String playerName,
            String issuerName,
            String punishmentType,
            String reason
    ) {}
}
