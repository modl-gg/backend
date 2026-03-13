package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.dto.request.AcknowledgeNotificationsRequest;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.util.PaginationHelper;
import gg.modl.backend.util.PlayerDataUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MinecraftPlayerService {
    private final PlayerService playerService;
    private final PlayerMongoRepository playerRepository;
    private final TicketMongoRepository ticketRepository;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final AccountLinkingService accountLinkingService;
    private final MojangApiService mojangApiService;
    private final IssuerNameResolver issuerNameResolver;
    private final StaffMongoRepository staffRepository;

    public MinecraftPlayerService(
        PlayerService playerService,
        PlayerMongoRepository playerRepository,
        TicketMongoRepository ticketRepository,
        PlayerStatusCalculator statusCalculator,
        PunishmentTypeService punishmentTypeService,
        PunishmentLifecycleService punishmentLifecycleService,
        AccountLinkingService accountLinkingService,
        MojangApiService mojangApiService,
        IssuerNameResolver issuerNameResolver,
        StaffMongoRepository staffRepository
    ) {
        this.playerService = playerService;
        this.playerRepository = playerRepository;
        this.ticketRepository = ticketRepository;
        this.statusCalculator = statusCalculator;
        this.punishmentTypeService = punishmentTypeService;
        this.punishmentLifecycleService = punishmentLifecycleService;
        this.accountLinkingService = accountLinkingService;
        this.mojangApiService = mojangApiService;
        this.issuerNameResolver = issuerNameResolver;
        this.staffRepository = staffRepository;
    }

    public ServiceResponse login(
        Server server,
        UUID playerUuid,
        String username,
        String ip,
        Map<String, Object> ipInfo,
        String skinHash,
        String serverName
    ) {
        Player player = playerService.loginPlayer(server, playerUuid, username, ip, ipInfo, skinHash, serverName);
        accountLinkingService.findAndLinkAccounts(server, playerUuid);

        player = findPlayerByUuid(server, playerUuid.toString()).orElse(player);

        player = promotePunishments(server, player, playerUuid, username, skinHash);

        List<PunishmentType> punishmentTypes = punishmentTypeService.getPunishmentTypes(server);
        Map<String, String> resolvedIssuers = resolveIssuersForPlayer(server, player);
        List<Map<String, Object>> activePunishments = collectActivePunishments(player, punishmentTypes, resolvedIssuers);

        return buildLoginResponse(server, player, playerUuid, ip, username, activePunishments);
    }

    private Player promotePunishments(Server server, Player player, UUID playerUuid, String username, String skinHash) {
        List<String> promoted = punishmentLifecycleService.promoteUnstartedPunishments(server, player);
        List<String> autoPardoned = punishmentLifecycleService.checkRestrictionAutoPardons(server, player, username, skinHash);
        List<String> linkedBans = punishmentLifecycleService.enforceAltBlockingBans(server, player);

        if (!promoted.isEmpty() || !autoPardoned.isEmpty() || !linkedBans.isEmpty()) {
            return findPlayerByUuid(server, playerUuid.toString()).orElse(player);
        }
        return player;
    }

    private List<Map<String, Object>> collectActivePunishments(
        Player player,
        List<PunishmentType> punishmentTypes,
        Map<String, String> resolvedIssuers
    ) {
        List<Map<String, Object>> activePunishments = new ArrayList<>();

        for (Punishment punishment : player.getPunishments()) {
            if (!statusCalculator.isPunishmentActive(punishment)) {
                continue;
            }

            PunishmentType punishmentType = punishmentTypes.stream()
                .filter(type -> type.getOrdinal() == punishment.getTypeOrdinal())
                .findFirst()
                .orElse(null);
            if (punishmentType != null && punishmentType.isKick()) {
                continue;
            }

            activePunishments.add(PunishmentMapper.toSimplePunishment(punishment, punishmentTypes, statusCalculator, resolvedIssuers));
        }

        for (Punishment punishment : player.getPunishments()) {
            if (punishment.getTypeOrdinal() != 0 || punishment.getStarted() != null) {
                continue;
            }
            activePunishments.add(PunishmentMapper.toSimplePunishment(punishment, punishmentTypes, statusCalculator, resolvedIssuers));
        }

        return deduplicateActivePunishments(activePunishments);
    }

    private List<Map<String, Object>> deduplicateActivePunishments(List<Map<String, Object>> punishments) {
        Map<String, Map<String, Object>> oldestByCategory = new LinkedHashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> punishment : punishments) {
            String category = (String) punishment.get("category");
            if ("BAN".equals(category) || "MUTE".equals(category)) {
                Map<String, Object> existing = oldestByCategory.get(category);
                if (existing == null) {
                    oldestByCategory.put(category, punishment);
                } else {
                    long existingIssued = existing.get("issuedAt") instanceof Number number ? number.longValue() : 0L;
                    long currentIssued = punishment.get("issuedAt") instanceof Number number ? number.longValue() : 0L;
                    if (currentIssued < existingIssued) {
                        oldestByCategory.put(category, punishment);
                    }
                }
            } else {
                result.add(punishment);
            }
        }

        result.addAll(oldestByCategory.values());
        return result;
    }

    private ServiceResponse buildLoginResponse(
        Server server,
        Player player,
        UUID playerUuid,
        String ip,
        String username,
        List<Map<String, Object>> activePunishments
    ) {
        List<Map<String, Object>> pendingNotifications = extractPendingNotifications(player);

        List<String> pendingIpLookups = new ArrayList<>();
        if (ip != null) {
            boolean ipNeedsLookup = player.getIpAddresses()
                .stream()
                .anyMatch(playerIp -> playerIp.getIpAddress().equals(ip) && playerIp.getCountry() == null);
            if (ipNeedsLookup) {
                pendingIpLookups.add(ip);
            }
        }

        List<Map<String, Object>> pendingStatWipes = new ArrayList<>();
        for (Punishment punishment : player.getPunishments()) {
            Map<String, Object> data = punishment.getData();
            if (data == null
                || !Boolean.TRUE.equals(data.get("wipeAfterExpiry"))
                || Boolean.TRUE.equals(data.get("statWipeCompleted"))
                || !statusCalculator.isPunishmentNaturallyExpired(punishment)) {
                continue;
            }

            pendingStatWipes.add(Map.of(
                "minecraftUuid", playerUuid.toString(),
                "username", PlayerDataUtils.extractLatestUsername(safeUsernames(player)),
                "punishmentId", punishment.getId()
            ));
        }

        boolean isNewPlayer = player.getUsernames().size() == 1;
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("status", isNewPlayer ? 201 : 200);
        responseBody.put("activePunishments", activePunishments);
        responseBody.put("pendingNotifications", pendingNotifications);
        if (!pendingIpLookups.isEmpty()) {
            responseBody.put("pendingIpLookups", pendingIpLookups);
        }
        if (!pendingStatWipes.isEmpty()) {
            responseBody.put("pendingStatWipes", pendingStatWipes);
        }

        return new ServiceResponse(isNewPlayer ? HttpStatus.CREATED : HttpStatus.OK, responseBody);
    }

    private List<Map<String, Object>> extractPendingNotifications(Player player) {
        if (player.getData() == null) {
            return List.of();
        }

        Object rawPending = player.getData().getOrDefault("pendingNotifications", Collections.emptyList());
        if (!(rawPending instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
            .filter(entry -> entry instanceof Map<?, ?>)
            .map(entry -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> notification = (Map<String, Object>) entry;
                return notification;
            })
            .toList();
    }

    private List<gg.modl.backend.player.data.UsernameEntry> safeUsernames(Player player) {
        return player.getUsernames() != null ? player.getUsernames() : List.of();
    }

    private Optional<Player> findPlayerByUuid(Server server, String uuid) {
        return playerRepository.findByMinecraftUuid(server, uuid);
    }

    private Map<String, String> resolveIssuersForPlayer(Server server, Player player) {
        Set<String> ids = new HashSet<>();
        for (Punishment p : safePunishments(player)) {
            ids.addAll(PunishmentQueryService.collectIssuerIds(p));
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return issuerNameResolver.batchResolve(ids, server, staffRepository);
    }

    private List<Punishment> safePunishments(Player player) {
        return player.getPunishments() != null ? player.getPunishments() : List.of();
    }

    public Map<String, Object> disconnect(Server server, String minecraftUuid, long sessionDurationMs) {
        playerRepository.markDisconnected(server, minecraftUuid, sessionDurationMs);
        return Map.of("status", 200, "success", true);
    }

    public Map<String, Object> updateServer(Server server, String minecraftUuid, String serverName) {
        playerRepository.updateLastServer(server, minecraftUuid, serverName);
        return Map.of("status", 200, "success", true);
    }

    public Map<String, Object> getOnlinePlayers(Server server) {
        List<Map<String, Object>> players = playerRepository.findOnlinePlayers(server, 500)
            .stream()
            .map(player -> {
                Date joinedAt = player.getData() != null ? (Date) player.getData().get("lastLogin") : null;
                Object playtimeObj = player.getData() != null ? player.getData().get("totalPlaytimeSeconds") : null;
                long totalPlaytimeMs = playtimeObj instanceof Number number ? number.longValue() * 1000 : 0L;

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("uuid", player.getMinecraftUuid().toString());
                entry.put("username", PlayerDataUtils.extractLatestUsername(safeUsernames(player)));
                entry.put("joinedAt", joinedAt);
                entry.put("totalPlaytimeMs", totalPlaytimeMs);
                return entry;
            })
            .toList();

        return Map.of("status", 200, "players", players);
    }

    public ServiceResponse getPlayerByUuid(Server server, String uuid, Integer punishmentLimit, Integer noteLimit) {
        Player player = findPlayerByUuid(server, uuid).orElse(null);
        if (player == null) {
            return notFound("Player not found");
        }

        List<PunishmentType> punishmentTypes = punishmentTypeService.getPunishmentTypes(server);
        return ok(Map.of("status", 200, "profile", toPlayerProfile(server, player, punishmentTypes, punishmentLimit, noteLimit)));
    }

    private Map<String, Object> toPlayerProfile(Server server, Player player, List<PunishmentType> punishmentTypes, Integer punishmentLimit, Integer noteLimit) {
        List<Map<String, Object>> usernames = safeUsernames(player).stream()
            .map(username -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("username", username.username());
                entry.put("date", username.date());
                return entry;
            })
            .toList();

        List<Map<String, Object>> notes = safeNotes(player).stream()
            .map(note -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", note.getId());
                entry.put("text", note.getText());
                entry.put("date", note.getDate());
                entry.put("issuerName", note.getIssuerName());
                entry.put("issuerId", note.getIssuerId());
                return entry;
            })
            .toList();

        List<Map<String, Object>> ipAddresses = safeIpAddresses(player).stream()
            .map(ip -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("country", ip.getCountry());
                entry.put("region", ip.getRegion());
                entry.put("asn", ip.getAsn());
                entry.put("proxy", ip.isProxy());
                entry.put("hosting", ip.isHosting());
                entry.put("firstLogin", ip.getFirstLogin());
                entry.put("logins", ip.getLogins());
                return entry;
            })
            .toList();

        Map<String, String> resolvedIssuers = resolveIssuersForPlayer(server, player);
        List<Map<String, Object>> punishments = safePunishments(player).stream()
            .map(punishment -> PunishmentMapper.toPunishmentMap(punishment, punishmentTypes, resolvedIssuers))
            .toList();

        List<Map<String, Object>> pendingNotifications = extractPendingNotifications(player);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", player.getId());
        profile.put("minecraftUuid", player.getMinecraftUuid().toString());
        profile.put("usernames", usernames);
        profile.put("notes", notes);
        profile.put("ipAddresses", ipAddresses);
        profile.put("punishments", punishments);
        profile.put("pendingNotifications", pendingNotifications);
        profile.put("data", player.getData());
        if (punishmentLimit != null) {
            profile.put("punishmentCount", punishments.size());
        }
        if (noteLimit != null) {
            profile.put("noteCount", notes.size());
        }
        return profile;
    }

    private List<gg.modl.backend.player.data.NoteEntry> safeNotes(Player player) {
        return player.getNotes() != null ? player.getNotes() : List.of();
    }

    private List<gg.modl.backend.player.data.IPEntry> safeIpAddresses(Player player) {
        return player.getIpAddresses() != null ? player.getIpAddresses() : List.of();
    }

    private ServiceResponse ok(Map<String, Object> body) {
        return new ServiceResponse(HttpStatus.OK, body);
    }

    private ServiceResponse notFound(String message) {
        return new ServiceResponse(HttpStatus.NOT_FOUND, Map.of("status", 404, "message", message));
    }

    public ServiceResponse getPlayerByMinecraftUuid(Server server, String minecraftUuid, boolean queryMojang) {
        if (minecraftUuid == null || minecraftUuid.isBlank()) {
            return badRequest("minecraftUuid parameter required");
        }

        Player player = findPlayerByUuid(server, minecraftUuid).orElse(null);
        if (player == null && queryMojang) {
            Optional<MojangApiService.MojangProfile> profile = mojangApiService.lookupByUuid(minecraftUuid);
            if (profile.isPresent()) {
                return ok(Map.of(
                    "status", 200,
                    "message", "Player found via Mojang",
                    "player", toMojangProfile(profile.get())
                ));
            }
        }

        if (player == null) {
            return notFound("Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return ok(Map.of(
            "status", 200,
            "message", "Player found",
            "player", toPlayerProfile(server, player, types)
        ));
    }

    private Map<String, Object> toMojangProfile(MojangApiService.MojangProfile profile) {
        return Map.of(
            "minecraftUuid", profile.uuid().toString(),
            "usernames", List.of(Map.of("username", profile.name())),
            "notes", List.of(),
            "ipAddresses", List.of(),
            "punishments", List.of(),
            "pendingNotifications", List.of(),
            "data", Map.of()
        );
    }

    private Map<String, Object> toPlayerProfile(Server server, Player player, List<PunishmentType> punishmentTypes) {
        return toPlayerProfile(server, player, punishmentTypes, null, null);
    }

    private ServiceResponse badRequest(String message) {
        return new ServiceResponse(HttpStatus.BAD_REQUEST, Map.of("status", 400, "message", message));
    }

    public ServiceResponse getPlayerByUsername(Server server, String username, boolean queryMojang) {
        Player player = findByUsername(server, username).orElse(null);
        if (player == null && queryMojang) {
            Optional<MojangApiService.MojangProfile> profile = mojangApiService.lookupByUsername(username);
            if (profile.isPresent()) {
                return ok(Map.of(
                    "status", 200,
                    "message", "Player found via Mojang",
                    "player", toMojangProfile(profile.get())
                ));
            }
        }

        if (player == null) {
            return notFound("Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return ok(Map.of(
            "status", 200,
            "message", "Player found",
            "player", toPlayerProfile(server, player, types)
        ));
    }

    private Optional<Player> findByUsername(Server server, String username) {
        return playerRepository.findByUsernameIgnoreCase(server, username);
    }

    public ServiceResponse lookupPlayer(Server server, String query, boolean shouldQueryMojang) {
        boolean isUuid = query.contains("-") && query.length() == 36;
        Player player = isUuid
                        ? findPlayerByUuid(server, query).orElse(null)
                        : findByUsername(server, query).orElse(null);

        if (player == null && shouldQueryMojang) {
            Optional<MojangApiService.MojangProfile> profile = isUuid
                                                               ? mojangApiService.lookupByUuid(query)
                                                               : mojangApiService.lookupByUsername(query);
            if (profile.isPresent()) {
                MojangApiService.MojangProfile mojangProfile = profile.get();
                Map<String, Object> lookupData = new LinkedHashMap<>();
                lookupData.put("minecraftUuid", mojangProfile.uuid().toString());
                lookupData.put("currentUsername", mojangProfile.name());
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
                return ok(Map.of(
                    "status", 200,
                    "message", "Player found via Mojang",
                    "data", lookupData
                ));
            }
        }

        if (player == null) {
            return notFound("Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return ok(Map.of(
            "status", 200,
            "message", "Player found",
            "data", buildLookupResponse(server, player, types)
        ));
    }

    private Map<String, Object> buildLookupResponse(Server server, Player player, List<PunishmentType> types) {
        String currentUsername = PlayerDataUtils.extractLatestUsername(safeUsernames(player));
        List<String> previousUsernames = player.getUsernames()
            .stream()
            .map(username -> username.username())
            .skip(1)
            .toList();

        Date firstSeen = player.getUsernames().isEmpty() ? null : player.getUsernames().get(0).date();
        Date lastSeen = player.getUsernames().isEmpty() ? null : player.getUsernames().get(player.getUsernames().size() - 1).date();

        int totalPunishments = player.getPunishments().size();
        int activePunishments = (int) player.getPunishments()
            .stream()
            .filter(statusCalculator::isPunishmentActive)
            .count();

        int bans = 0;
        int mutes = 0;
        int kicks = 0;
        int warnings = 0;
        for (Punishment punishment : player.getPunishments()) {
            int ordinal = punishment.getTypeOrdinal();
            PunishmentType type = types.stream()
                .filter(candidate -> candidate.getOrdinal() == ordinal)
                .findFirst()
                .orElse(null);
            if (type != null && type.isBan()) {
                bans++;
            } else if (type != null && type.isMute()) {
                mutes++;
            } else if (type != null && type.isKick()) {
                kicks++;
            } else {
                warnings++;
            }
        }

        List<Map<String, Object>> recentPunishments = player.getPunishments()
            .stream()
            .sorted((left, right) -> right.getIssued().compareTo(left.getIssued()))
            .limit(5)
            .map(punishment -> {
                String typeName = types.stream()
                    .filter(type -> type.getOrdinal() == punishment.getTypeOrdinal())
                    .findFirst()
                    .map(PunishmentType::getName)
                    .orElse("Unknown");

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("id", punishment.getId());
                response.put("type", typeName);
                response.put("issuer", punishment.getIssuerName());
                response.put("issuedAt", punishment.getIssued());
                response.put("expiresAt", statusCalculator.getEffectiveExpiry(punishment));
                response.put("isActive", statusCalculator.isPunishmentActive(punishment));
                return response;
            })
            .toList();

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

    public ServiceResponse lookupProfile(Server server, String query, boolean shouldQueryMojang, Integer punishmentLimit, Integer noteLimit) {
        boolean isUuid = query.contains("-") && query.length() == 36;
        Player player = isUuid
                        ? findPlayerByUuid(server, query).orElse(null)
                        : findByUsername(server, query).orElse(null);

        if (player == null && shouldQueryMojang) {
            Optional<MojangApiService.MojangProfile> mojangProfile = isUuid
                                                                     ? mojangApiService.lookupByUuid(query)
                                                                     : mojangApiService.lookupByUsername(query);
            if (mojangProfile.isPresent()) {
                Player mojangPlayer = findPlayerByUuid(server, mojangProfile.get().uuid().toString()).orElse(null);
                if (mojangPlayer != null) {
                    player = mojangPlayer;
                }
            }
        }

        if (player == null) {
            return notFound("Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return ok(Map.of("status", 200, "profile", toPlayerProfile(server, player, types, punishmentLimit, noteLimit)));
    }

    public ServiceResponse createNote(Server server, String uuid, String text, String issuerName, String issuerId) {
        Player player = findPlayerByUuid(server, uuid).orElse(null);
        if (player == null) {
            return notFound("Player not found");
        }

        ensurePlayerNotes(player).add(NoteEntry.builder()
            .id(new ObjectId().toHexString())
            .text(text)
            .date(new Date())
            .issuerName(issuerId != null ? null : issuerName)
            .issuerId(issuerId)
            .build());
        playerRepository.replaceNotes(server, player);

        return ok(Map.of(
            "status", 200,
            "success", true,
            "message", "Note added"
        ));
    }

    private List<NoteEntry> ensurePlayerNotes(Player player) {
        if (player.getNotes() == null) {
            player.setNotes(new ArrayList<>());
        }
        return player.getNotes();
    }

    public ServiceResponse acknowledgeNotifications(Server server, AcknowledgeNotificationsRequest request) {
        Player player = findPlayerByUuid(server, request.playerUuid()).orElse(null);
        if (player == null) {
            return ok(Map.of(
                "status", 200,
                "success", true,
                "message", "No player found, nothing to acknowledge"
            ));
        }

        List<Map<String, Object>> pendingNotifications = extractPendingNotifications(player);
        List<Map<String, Object>> remainingNotifications = pendingNotifications.stream()
            .filter(notification -> {
                Object notificationId = notification.get("id");
                return notificationId == null || !request.notificationIds().contains(notificationId.toString());
            })
            .toList();

        ensurePlayerData(player).put("pendingNotifications", remainingNotifications);
        playerRepository.replacePendingNotifications(server, player, remainingNotifications);

        return ok(Map.of(
            "status", 200,
            "success", true,
            "message", "Acknowledged " + (pendingNotifications.size() - remainingNotifications.size()) + " notification(s)"
        ));
    }

    private Map<String, Object> ensurePlayerData(Player player) {
        if (player.getData() == null) {
            player.setData(new LinkedHashMap<>());
        }
        return player.getData();
    }

    public ServiceResponse getLinkedAccounts(Server server, String uuid, Integer page, Integer limit) {
        ServiceResponse fullResponse = getLinkedAccountsFull(server, uuid);
        if (fullResponse.status() != HttpStatus.OK || page == null || limit == null) {
            return fullResponse;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allAccounts = (List<Map<String, Object>>) fullResponse.body().get("linkedAccounts");
        PaginationHelper.PageResult<Map<String, Object>> result = PaginationHelper.paginate(allAccounts, page, limit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("linkedAccounts", result.items());
        response.put("totalCount", result.totalCount());
        response.put("page", result.page());
        response.put("hasMore", result.hasMore());
        return ok(response);
    }

    private ServiceResponse getLinkedAccountsFull(Server server, String uuid) {
        Player player = findPlayerByUuid(server, uuid).orElse(null);
        if (player == null) {
            return notFound("Player not found");
        }

        List<String> ips = safeIpAddresses(player).stream()
            .map(ip -> ip.getIpAddress())
            .toList();

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        Set<String> addedUuids = new HashSet<>();
        List<Map<String, Object>> linkedAccounts = new ArrayList<>();

        if (!ips.isEmpty()) {
            List<Player> relatedPlayers = playerRepository.findByIpAddressesExcludingUuid(server, ips, uuid, 20);
            for (Player related : relatedPlayers) {
                linkedAccounts.add(toPlayerProfile(server, related, types));
                addedUuids.add(related.getMinecraftUuid().toString());
            }
        }

        if (player.getData() != null && player.getData().containsKey("linkedAccounts")) {
            Object rawLinked = player.getData().get("linkedAccounts");
            List<String> storedLinkedUuids = rawLinked instanceof List<?> list
                                             ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                                             : List.of();

            List<String> missingUuids = storedLinkedUuids.stream()
                .filter(linkedUuid -> !addedUuids.contains(linkedUuid))
                .toList();
            if (!missingUuids.isEmpty()) {
                List<Player> linkedPlayers = playerRepository.findByMinecraftUuids(server, missingUuids);
                for (Player linkedPlayer : linkedPlayers) {
                    linkedAccounts.add(toPlayerProfile(server, linkedPlayer, types));
                }
            }
        }

        return ok(Map.of("status", 200, "linkedAccounts", linkedAccounts));
    }

    public ServiceResponse getPlayerPunishments(Server server, String uuid, int page, int limit) {
        Player player = findPlayerByUuid(server, uuid).orElse(null);
        if (player == null) {
            return notFound("Player not found");
        }

        List<PunishmentType> punishmentTypes = punishmentTypeService.getPunishmentTypes(server);
        Map<String, String> resolvedIssuers = resolveIssuersForPlayer(server, player);

        List<Map<String, Object>> allPunishments = player.getPunishments()
            .stream()
            .sorted((a, b) -> b.getIssued().compareTo(a.getIssued()))
            .map(p -> PunishmentMapper.toPunishmentMap(p, punishmentTypes, resolvedIssuers))
            .toList();

        PaginationHelper.PageResult<Map<String, Object>> result = PaginationHelper.paginate(allPunishments, page, limit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("punishments", result.items());
        response.put("totalCount", result.totalCount());
        response.put("page", result.page());
        response.put("hasMore", result.hasMore());
        return ok(response);
    }

    public ServiceResponse getPlayerNotes(Server server, String uuid, int page, int limit) {
        Player player = findPlayerByUuid(server, uuid).orElse(null);
        if (player == null) {
            return notFound("Player not found");
        }

        List<Map<String, Object>> allNotes = player.getNotes()
            .stream()
            .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
            .map(note -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", note.getId());
                entry.put("text", note.getText());
                entry.put("date", note.getDate());
                entry.put("issuerName", note.getIssuerName());
                entry.put("issuerId", note.getIssuerId());
                return entry;
            })
            .toList();

        PaginationHelper.PageResult<Map<String, Object>> result = PaginationHelper.paginate(allNotes, page, limit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("notes", result.items());
        response.put("totalCount", result.totalCount());
        response.put("page", result.page());
        response.put("hasMore", result.hasMore());
        return ok(response);
    }

    public Map<String, Object> getPlayerReports(Server server, String uuid) {
        List<Map<String, Object>> reports = ticketRepository.findReportedPlayerTickets(server, uuid, 50)
            .stream()
            .map(ticket -> {
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("id", ticket.getId());
                report.put("type", ticket.getType() != null ? ticket.getType().getId() : null);
                report.put("reporterName", ticket.getCreatorName());
                report.put("reporterUuid", ticket.getCreatorUuid());
                report.put("subject", ticket.getSubject());
                report.put("status", ticket.getStatus() != null ? ticket.getStatus().getId() : null);
                report.put("priority", ticket.getPriority() != null ? ticket.getPriority().getId() : null);
                report.put("createdAt", ticket.getCreated());
                return report;
            })
            .toList();

        return Map.of("status", 200, "reports", reports);
    }

    public Map<String, Object> submitIpInfo(
        Server server,
        String minecraftUuid,
        String ip,
        String country,
        String region,
        String asn,
        boolean proxy,
        boolean hosting
    ) {
        playerService.updateIpGeoData(server, minecraftUuid, ip, Map.of(
            "country", country != null ? country : "",
            "region", region != null ? region : "",
            "asn", asn != null ? asn : "",
            "proxy", proxy,
            "hosting", hosting
        ));
        return Map.of("status", 200, "success", true);
    }

    public Map<String, Object> pardonPlayer(Server server, String playerName, String punishmentType, String issuerName, String issuerId, String reason) {
        Player player = findByUsername(server, playerName).orElse(null);
        if (player == null) {
            return Map.of("status", 404, "message", "Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        int pardoned = 0;

        for (Punishment punishment : player.getPunishments()) {
            if (isAlreadyPardoned(punishment)) {
                continue;
            }

            boolean isActive = statusCalculator.isPunishmentActive(punishment);
            Map<String, Object> punishmentData = punishment.getData();
            boolean isUnstarted = punishmentData != null && "Unstarted".equals(punishmentData.get("status"));

            boolean shouldPardon;
            if (punishmentType == null) {
                shouldPardon = isActive || isUnstarted;
            } else {
                String requestedType = punishmentType.toLowerCase();
                String effectiveCategory = statusCalculator.getEffectiveCategory(punishment, types);
                shouldPardon = ("ban".equals(requestedType) && "BAN".equals(effectiveCategory) && (isActive || isUnstarted))
                               || ("mute".equals(requestedType) && "MUTE".equals(effectiveCategory) && (isActive || isUnstarted));
            }

            if (!shouldPardon) {
                continue;
            }

            PunishmentQueryService.PunishmentOperationResult result = punishmentLifecycleService.pardonPunishment(
                server,
                punishment.getId(),
                issuerName,
                issuerId,
                reason
            );
            if (result.status() == PunishmentQueryService.PunishmentOperationStatus.SUCCESS) {
                pardoned++;
            }
        }

        return Map.of(
            "status", 200,
            "success", pardoned > 0,
            "pardonedCount", pardoned,
            "message", pardoned > 0
                       ? "Pardoned " + pardoned + " punishment(s)"
                       : "No punishments found to pardon"
        );
    }

    private boolean isAlreadyPardoned(Punishment punishment) {
        return punishment.getModifications() != null && punishment.getModifications()
            .stream()
            .anyMatch(modification ->
                "MANUAL_PARDON".equals(modification.type())
                || "APPEAL_ACCEPT".equals(modification.type())
                || "SYSTEM_PARDON".equals(modification.type()));
    }

    public record ServiceResponse(HttpStatus status, Map<String, Object> body) {
    }
}
