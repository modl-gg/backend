package gg.modl.backend.player.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.PlayerResponseMessage;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.service.AccountLinkingService;
import gg.modl.backend.player.service.MojangApiService;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.player.service.PunishmentMapper;
import gg.modl.backend.player.service.PunishmentService;
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
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
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
@Slf4j
public class MinecraftPlayerController {
    private final PlayerService playerService;
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final PunishmentService punishmentService;
    private final AccountLinkingService accountLinkingService;
    private final MojangApiService mojangApiService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody @Valid LoginRequest request,
            BindingResult bindingResult,
            HttpServletRequest httpRequest
    ) {
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getFieldErrors().stream()
                    .map(e -> e.getField() + ": '" + e.getRejectedValue() + "' - " + e.getDefaultMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Unknown validation error");
            System.err.println("[LOGIN] Validation failed: " + errors);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "success", false,
                    "message", PlayerResponseMessage.LOGIN_INVALID_SCHEMA,
                    "errors", errors
            ));
        }

        Server server = RequestUtil.getRequestServer(httpRequest);
        UUID playerUuid = UUID.fromString(request.minecraftUUID());

        Player player = playerService.loginPlayer(
                server,
                playerUuid,
                request.username(),
                request.ip(),
                request.ipInfo(),
                request.skinHash(),
                request.serverName()
        );

        // Link accounts by shared IPs
        accountLinkingService.findAndLinkAccounts(server, playerUuid);

        // Re-fetch player to get updated linked accounts data
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query refetchQuery = Query.query(Criteria.where("minecraftUuid").is(request.minecraftUUID()));
        player = template.findOne(refetchQuery, Player.class, CollectionName.PLAYERS);

        // Promote queued punishments if previous ones expired or were pardoned
        List<String> promoted = punishmentService.promoteUnstartedPunishments(server, player);

        // Check restriction auto-pardons (Bad Username / Bad Skin)
        List<String> autoPardoned = punishmentService.checkRestrictionAutoPardons(
                server, player, request.username(), request.skinHash());

        // Enforce alt-blocking bans: create LinkedBan if linked account has alt-blocking ban
        List<String> linkedBans = punishmentService.enforceAltBlockingBans(server, player);

        if (!promoted.isEmpty() || !autoPardoned.isEmpty() || !linkedBans.isEmpty()) {
            // Re-fetch player to get updated punishment data
            player = template.findOne(refetchQuery, Player.class, CollectionName.PLAYERS);
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        List<Map<String, Object>> activePunishments = new ArrayList<>();

        for (Punishment punishment : player.getPunishments()) {
            boolean isActive = statusCalculator.isPunishmentActive(punishment);

            if (isActive) {
                // Skip kicks - they are instant punishments and shouldn't be "active"
                PunishmentType punishmentType = types.stream()
                        .filter(t -> t.getOrdinal() == punishment.getType_ordinal())
                        .findFirst()
                        .orElse(null);
                if (punishmentType != null && punishmentType.isKick()) {
                    continue;
                }
                activePunishments.add(PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator));
            }
        }

        // Include unexecuted kicks (started == null means plugin hasn't acknowledged)
        for (Punishment punishment : player.getPunishments()) {
            if (punishment.getType_ordinal() != 0) continue;
            if (punishment.getStarted() != null) continue; // Already executed
            activePunishments.add(PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator));
        }

        // Deduplicate: keep only the oldest active punishment per category (BAN, MUTE)
        activePunishments = deduplicateActivePunishments(activePunishments);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pendingNotifications = (List<Map<String, Object>>)
                player.getData().getOrDefault("pendingNotifications", List.of());

        // Ask the plugin to do IP geo lookup if the IP has no geo data yet
        List<String> pendingIpLookups = new ArrayList<>();
        if (request.ip() != null) {
            boolean ipNeedsLookup = player.getIpAddresses().stream()
                    .anyMatch(ip -> ip.getIpAddress().equals(request.ip()) && ip.getCountry() == null);
            if (ipNeedsLookup) {
                pendingIpLookups.add(request.ip());
            }
        }

        boolean isNewPlayer = player.getUsernames().size() == 1;
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("status", isNewPlayer ? 201 : 200);
        responseBody.put("activePunishments", activePunishments);
        responseBody.put("pendingNotifications", pendingNotifications);
        if (!pendingIpLookups.isEmpty()) {
            responseBody.put("pendingIpLookups", pendingIpLookups);
        }

        return ResponseEntity.status(isNewPlayer ? HttpStatus.CREATED : HttpStatus.OK)
                .body(responseBody);
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

        if (request.sessionDurationMs() > 0) {
            long sessionSeconds = request.sessionDurationMs() / 1000;
            update.inc("data.totalPlaytimeSeconds", sessionSeconds);
        }

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of("status", 200, "success", true));
    }

    @PostMapping("/update-server")
    public ResponseEntity<Map<String, Object>> updateServer(
            @RequestBody UpdateServerRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("minecraftUuid").is(request.minecraftUuid()));
        Update update = new Update().set("data.lastServer", request.serverName());
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of("status", 200, "success", true));
    }

    @GetMapping("/online")
    public ResponseEntity<Map<String, Object>> getOnlinePlayers(
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("data.isOnline").is(true));
        query.limit(500);

        List<Player> onlinePlayers = template.find(query, Player.class, CollectionName.PLAYERS);

        List<Map<String, Object>> players = onlinePlayers.stream().map(player -> {
            String username = player.getUsernames().isEmpty() ? "Unknown"
                    : player.getUsernames().get(player.getUsernames().size() - 1).username();
            Date joinedAt = player.getData() != null
                    ? (Date) player.getData().get("lastLogin")
                    : null;

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("uuid", player.getMinecraftUuid().toString());
            p.put("username", username);
            p.put("joinedAt", joinedAt);
            return p;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "players", players
        ));
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

        // Get punishment types for name lookup
        List<PunishmentType> punishmentTypes = punishmentTypeService.getPunishmentTypes(server);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "profile", toPlayerProfile(player, punishmentTypes)
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPlayerByQuery(
            @RequestParam(required = false) String minecraftUuid,
            @RequestParam(defaultValue = "true") boolean queryMojang,
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

        if (player == null && queryMojang) {
            Optional<MojangApiService.MojangProfile> profile = mojangApiService.lookupByUuid(minecraftUuid);
            if (profile.isPresent()) {
                MojangApiService.MojangProfile mojang = profile.get();
                return ResponseEntity.ok(Map.of(
                        "status", 200,
                        "message", "Player found via Mojang",
                        "player", Map.of(
                                "minecraftUuid", mojang.uuid().toString(),
                                "usernames", List.of(Map.of("username", mojang.name())),
                                "notes", List.of(),
                                "ipList", List.of(),
                                "punishments", List.of(),
                                "pendingNotifications", List.of(),
                                "data", Map.of()
                        )
                ));
            }
        }

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Player found",
                "player", toPlayerProfile(player, types)
        ));
    }

    @GetMapping("/by-name")
    public ResponseEntity<Map<String, Object>> getPlayerByUsername(
            @RequestParam String username,
            @RequestParam(defaultValue = "true") boolean queryMojang,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("usernames.username").regex("^" + username + "$", "i"));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null && queryMojang) {
            Optional<MojangApiService.MojangProfile> profile = mojangApiService.lookupByUsername(username);
            if (profile.isPresent()) {
                MojangApiService.MojangProfile mojang = profile.get();
                return ResponseEntity.ok(Map.of(
                        "status", 200,
                        "message", "Player found via Mojang",
                        "player", Map.of(
                                "minecraftUuid", mojang.uuid().toString(),
                                "usernames", List.of(Map.of("username", mojang.name())),
                                "notes", List.of(),
                                "ipList", List.of(),
                                "punishments", List.of(),
                                "pendingNotifications", List.of(),
                                "data", Map.of()
                        )
                ));
            }
        }

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found"
            ));
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Player found",
                "player", toPlayerProfile(player, types)
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
        boolean isUuid = queryStr.contains("-") && queryStr.length() == 36;
        Player player;

        if (isUuid) {
            Query query = Query.query(Criteria.where("minecraftUuid").is(queryStr));
            player = template.findOne(query, Player.class, CollectionName.PLAYERS);
        } else {
            Query query = Query.query(Criteria.where("usernames.username").regex("^" + queryStr + "$", "i"));
            player = template.findOne(query, Player.class, CollectionName.PLAYERS);
        }

        if (player == null && request.shouldQueryMojang()) {
            Optional<MojangApiService.MojangProfile> profile = isUuid
                    ? mojangApiService.lookupByUuid(queryStr)
                    : mojangApiService.lookupByUsername(queryStr);
            if (profile.isPresent()) {
                MojangApiService.MojangProfile mojang = profile.get();
                Map<String, Object> lookupData = new LinkedHashMap<>();
                lookupData.put("minecraftUuid", mojang.uuid().toString());
                lookupData.put("currentUsername", mojang.name());
                lookupData.put("previousUsernames", List.of());
                lookupData.put("firstSeen", null);
                lookupData.put("lastSeen", null);
                lookupData.put("isOnline", false);
                lookupData.put("punishmentStats", Map.of(
                        "totalPunishments", 0,
                        "activePunishments", 0,
                        "bans", 0,
                        "mutes", 0,
                        "kicks", 0,
                        "warnings", 0
                ));
                lookupData.put("recentPunishments", List.of());
                return ResponseEntity.ok(Map.of(
                        "status", 200,
                        "message", "Player found via Mojang",
                        "data", lookupData
                ));
            }
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

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        // Use a set to track UUIDs we've already added (dedup between IP-based and data.linkedAccounts)
        Set<String> addedUuids = new HashSet<>();
        List<Map<String, Object>> linkedAccounts = new ArrayList<>();

        // 1. IP-based matching
        if (!ips.isEmpty()) {
            Query ipQuery = Query.query(
                    Criteria.where("ipAddresses.ipAddress").in(ips)
                            .and("minecraftUuid").ne(uuid)
            );
            ipQuery.limit(20);

            List<Player> related = template.find(ipQuery, Player.class, CollectionName.PLAYERS);
            for (Player p : related) {
                linkedAccounts.add(toPlayerProfile(p, types));
                addedUuids.add(p.getMinecraftUuid().toString());
            }
        }

        // 2. Also include accounts from data.linkedAccounts field
        if (player.getData() != null && player.getData().containsKey("linkedAccounts")) {
            @SuppressWarnings("unchecked")
            List<String> storedLinkedUuids = (List<String>) player.getData().get("linkedAccounts");
            if (storedLinkedUuids != null && !storedLinkedUuids.isEmpty()) {
                List<String> missingUuids = storedLinkedUuids.stream()
                        .filter(u -> !addedUuids.contains(u))
                        .toList();
                if (!missingUuids.isEmpty()) {
                    Query linkedQuery = Query.query(Criteria.where("minecraftUuid").in(missingUuids));
                    List<Player> linkedPlayers = template.find(linkedQuery, Player.class, CollectionName.PLAYERS);
                    for (Player p : linkedPlayers) {
                        linkedAccounts.add(toPlayerProfile(p, types));
                    }
                }
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

    private Map<String, Object> toPlayerProfile(Player player, List<PunishmentType> punishmentTypes) {
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
                .map(p -> PunishmentMapper.toPunishmentMap(p, punishmentTypes)).toList();

        // Get pending notifications from player data
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pendingNotifications = (List<Map<String, Object>>) player.getData()
                .getOrDefault("pendingNotifications", Collections.emptyList());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("_id", player.getId());
        profile.put("minecraftUuid", player.getMinecraftUuid().toString());
        profile.put("usernames", usernames);
        profile.put("notes", notes);
        profile.put("ipList", ipList);
        profile.put("punishments", punishments);
        profile.put("pendingNotifications", pendingNotifications);
        profile.put("data", player.getData());

        return profile;
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

    @PostMapping("/submit-ip-info")
    public ResponseEntity<Map<String, Object>> submitIpInfo(
            @RequestBody @Valid SubmitIpInfoRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        Map<String, Object> ipInfo = Map.of(
                "country", request.country() != null ? request.country() : "",
                "region", request.region() != null ? request.region() : "",
                "asn", request.asn() != null ? request.asn() : "",
                "proxy", request.proxy(),
                "hosting", request.hosting()
        );
        playerService.updateIpGeoData(server, request.minecraftUUID(), request.ip(), ipInfo);
        return ResponseEntity.ok(Map.of("status", 200, "success", true));
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
            // Check if already pardoned
            boolean alreadyPardoned = punishment.getModifications() != null && punishment.getModifications().stream()
                    .anyMatch(m -> "MANUAL_PARDON".equals(m.type()) || "APPEAL_ACCEPT".equals(m.type()) || "SYSTEM_PARDON".equals(m.type()));
            if (alreadyPardoned) continue;

            boolean isActive = statusCalculator.isPunishmentActive(punishment);
            Map<String, Object> pData = punishment.getData();
            boolean isUnstarted = pData != null && "Unstarted".equals(pData.get("status"));

            boolean shouldPardon = false;
            if (request.punishmentType() == null) {
                // General pardon - pardon active and unstarted punishments
                shouldPardon = isActive || isUnstarted;
            } else {
                // Specific type pardon (unban/unmute) - pardon both active and expired punishments of matching type
                String pType = request.punishmentType().toLowerCase();
                int ordinal = punishment.getType_ordinal();
                boolean isBan = types.stream().filter(t -> t.getOrdinal() == ordinal).findFirst().map(PunishmentType::isBan).orElse(false);
                boolean isMute = types.stream().filter(t -> t.getOrdinal() == ordinal).findFirst().map(PunishmentType::isMute).orElse(false);

                if (pType.equals("ban") && isBan) shouldPardon = true;
                if (pType.equals("mute") && isMute) shouldPardon = true;
            }

            if (shouldPardon) {
                Date now = new Date();

                Query updateQuery = Query.query(
                        Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                                .and("punishments.id").is(punishment.getId())
                );

                gg.modl.backend.player.data.punishment.PunishmentModification modification =
                        new gg.modl.backend.player.data.punishment.PunishmentModification(
                                new ObjectId().toHexString(),
                                "MANUAL_PARDON",
                                now,
                                request.issuerName(),
                                request.reason() != null ? request.reason() : "",
                                null,
                                null,
                                null
                        );

                // Create automatic note for pardon
                gg.modl.backend.player.data.punishment.PunishmentNote pardonNote =
                        new gg.modl.backend.player.data.punishment.PunishmentNote(
                                new ObjectId().toHexString(),
                                "pardoned punishment",
                                now,
                                request.issuerName()
                        );

                List<gg.modl.backend.player.data.punishment.PunishmentNote> notesToAdd = new java.util.ArrayList<>();
                notesToAdd.add(pardonNote);
                if (request.reason() != null && !request.reason().isBlank()) {
                    notesToAdd.add(new gg.modl.backend.player.data.punishment.PunishmentNote(
                            new ObjectId().toHexString(),
                            request.reason(),
                            now,
                            request.issuerName()
                    ));
                }

                Update update = new Update()
                        .push("punishments.$.modifications", modification)
                        .push("punishments.$.notes").each(notesToAdd.toArray())
                        .set("punishments.$.data.status", "Pardoned");

                template.updateFirst(updateQuery, update, Player.class, CollectionName.PLAYERS);
                pardoned++;

                // Cascade pardon linked bans if this was an alt-blocking ban
                if (pData != null && Boolean.TRUE.equals(pData.get("altBlocking"))) {
                    punishmentService.cascadePardonLinkedBans(server, punishment.getId());
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", pardoned > 0,
                "pardonedCount", pardoned,
                "message", pardoned > 0 ? "Pardoned " + pardoned + " punishment(s)" : "No punishments found to pardon"
        ));
    }

    public record LoginRequest(
            @Pattern(regexp = RegExpConstants.UUID) String minecraftUUID,
            @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String username,
            @Pattern(regexp = RegExpConstants.IP) String ip,
            Map<String, Object> ipInfo,
            String skinHash,
            String serverName
    ) {}

    public record SubmitIpInfoRequest(
            @Pattern(regexp = RegExpConstants.UUID) String minecraftUUID,
            @Pattern(regexp = RegExpConstants.IP) String ip,
            String country,
            String region,
            String asn,
            boolean proxy,
            boolean hosting
    ) {}

    public record DisconnectRequest(String minecraftUuid, long sessionDurationMs) {}

    public record UpdateServerRequest(String minecraftUuid, String serverName) {}

    public record LookupRequest(String query, Boolean queryMojang) {
        public boolean shouldQueryMojang() {
            return queryMojang == null || queryMojang;
        }
    }

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

    /**
     * Deduplicate active punishments: keep only the oldest active punishment per category (BAN, MUTE).
     * OTHER category punishments pass through unchanged.
     */
    private List<Map<String, Object>> deduplicateActivePunishments(List<Map<String, Object>> punishments) {
        Map<String, Map<String, Object>> oldestByCategory = new LinkedHashMap<>();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> p : punishments) {
            String category = (String) p.get("category");
            if ("BAN".equals(category) || "MUTE".equals(category)) {
                Map<String, Object> existing = oldestByCategory.get(category);
                if (existing == null) {
                    oldestByCategory.put(category, p);
                } else {
                    // Keep the one with the older issuedAt timestamp
                    long existingIssued = (Long) existing.get("issuedAt");
                    long currentIssued = (Long) p.get("issuedAt");
                    if (currentIssued < existingIssued) {
                        oldestByCategory.put(category, p);
                    }
                }
            } else {
                result.add(p);
            }
        }
        result.addAll(oldestByCategory.values());
        return result;
    }

}
