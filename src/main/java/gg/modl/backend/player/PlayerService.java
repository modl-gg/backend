package gg.modl.backend.player;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.dto.response.PlayerDetailResponse;
import gg.modl.backend.player.dto.response.PlayerSearchResult;
import gg.modl.backend.player.dto.response.PunishmentResponse;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerService {
    private static final int SEARCH_RESULT_LIMIT = 20;
    private static final int SEARCH_CANDIDATE_LIMIT = 100;

    private final PlayerMongoRepository playerRepository;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;

    public List<PlayerSearchResult> searchPlayers(Server server, String searchTerm) {
        String normalizedSearch = searchTerm == null ? "" : searchTerm.trim();
        if (normalizedSearch.isEmpty()) {
            return List.of();
        }

        List<Player> players;
        if (isUuid(normalizedSearch)) {
            players = playerRepository.findByMinecraftUuid(server, normalizedSearch)
                    .map(List::of)
                    .orElseGet(List::of);
        } else {
            players = playerRepository.searchByUsernamePattern(server, normalizedSearch, SEARCH_CANDIDATE_LIMIT);
            String normalizedSearchLower = normalizedSearch.toLowerCase(Locale.ROOT);
            players = players.stream()
                    .sorted(Comparator
                            .comparingInt((Player player) -> computeMatchRank(player, normalizedSearchLower))
                            .thenComparing((Player a, Player b) -> Long.compare(getLastLoginMillis(b), getLastLoginMillis(a)))
                            .thenComparing(player -> player.getMinecraftUuid().toString()))
                    .limit(SEARCH_RESULT_LIMIT)
                    .toList();
        }

        return players.stream()
                .map(player -> toPlayerSearchResult(server, player))
                .toList();
    }

    private PlayerSearchResult toPlayerSearchResult(Server server, Player player) {
        List<UsernameEntry> usernames = player.getUsernames();
        String username = usernames == null || usernames.isEmpty() ? "Unknown" :
                usernames.get(usernames.size() - 1).username();

        String status = calculatePlayerStatus(server, player);
        Date lastOnline = getLastOnline(player);
        boolean isOnline = Boolean.TRUE.equals(
                player.getData() != null ? player.getData().get("isOnline") : null);

        return new PlayerSearchResult(
                player.getMinecraftUuid().toString(),
                username,
                status,
                lastOnline,
                isOnline
        );
    }

    private int computeMatchRank(Player player, String normalizedSearchLower) {
        List<UsernameEntry> usernames = player.getUsernames();
        if (usernames == null || usernames.isEmpty()) {
            return 6;
        }

        String currentUsername = usernames.get(usernames.size() - 1).username();
        if (equalsIgnoreCase(currentUsername, normalizedSearchLower)) {
            return 0;
        }

        for (int i = 0; i < usernames.size() - 1; i++) {
            if (equalsIgnoreCase(usernames.get(i).username(), normalizedSearchLower)) {
                return 1;
            }
        }

        if (startsWithIgnoreCase(currentUsername, normalizedSearchLower)) {
            return 2;
        }

        for (int i = 0; i < usernames.size() - 1; i++) {
            if (startsWithIgnoreCase(usernames.get(i).username(), normalizedSearchLower)) {
                return 3;
            }
        }

        if (containsIgnoreCase(currentUsername, normalizedSearchLower)) {
            return 4;
        }

        for (int i = 0; i < usernames.size() - 1; i++) {
            if (containsIgnoreCase(usernames.get(i).username(), normalizedSearchLower)) {
                return 5;
            }
        }

        return 6;
    }

    private long getLastLoginMillis(Player player) {
        Date lastOnline = getLastOnline(player);
        return lastOnline != null ? lastOnline.getTime() : Long.MIN_VALUE;
    }

    private boolean equalsIgnoreCase(String value, String normalizedSearchLower) {
        return value != null && value.toLowerCase(Locale.ROOT).equals(normalizedSearchLower);
    }

    private boolean startsWithIgnoreCase(String value, String normalizedSearchLower) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith(normalizedSearchLower);
    }

    private boolean containsIgnoreCase(String value, String normalizedSearchLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearchLower);
    }

    public Optional<PlayerDetailResponse> getPlayerDetails(Server server, UUID minecraftUuid) {
        return findByMinecraftUuid(server, minecraftUuid)
                .map(player -> buildPlayerDetailResponse(server, player));
    }

    public Player createPlayer(Server server, UUID minecraftUuid, String username) {
        return playerRepository.saveEntity(server, newPlayer(minecraftUuid, username));
    }

    public Player loginPlayer(Server server, UUID minecraftUuid, String username, String ip) {
        return loginPlayer(server, minecraftUuid, username, ip, null, null, null);
    }

    public Player loginPlayer(Server server, UUID minecraftUuid, String username, String ip, Map<String, Object> ipInfo) {
        return loginPlayer(server, minecraftUuid, username, ip, ipInfo, null, null);
    }

    public Player loginPlayer(Server server, UUID minecraftUuid, String username, String ip, Map<String, Object> ipInfo, String skinHash) {
        return loginPlayer(server, minecraftUuid, username, ip, ipInfo, skinHash, null);
    }

    public Player loginPlayer(Server server, UUID minecraftUuid, String username, String ip, Map<String, Object> ipInfo, String skinHash, String serverName) {
        Optional<Player> existingPlayer = findByMinecraftUuid(server, minecraftUuid);

        if (existingPlayer.isPresent()) {
            Player player = existingPlayer.get();
            updatePlayerOnLogin(player, username, ip, ipInfo, skinHash, serverName);
            playerRepository.updateLoginState(server, player);
            return player;
        }

        Player player = newPlayer(minecraftUuid, username);
        addIpToPlayer(player, ip, ipInfo);
        updatePlayerDataOnLogin(player, skinHash, serverName);
        return playerRepository.saveEntity(server, player);
    }

    public Player addUsername(Server server, UUID minecraftUuid, String username) {
        Player player = findByMinecraftUuid(server, minecraftUuid).orElse(null);
        if (player == null) {
            return null;
        }

        ensureUsernames(player).add(new UsernameEntry(username, new Date()));
        playerRepository.replaceUsernames(server, player);
        return player;
    }

    public Player addNote(Server server, UUID minecraftUuid, String text, String issuerName, String issuerId) {
        Player player = findByMinecraftUuid(server, minecraftUuid).orElse(null);
        if (player == null) {
            return null;
        }

        NoteEntry entry = NoteEntry.builder()
                .id(new ObjectId().toHexString())
                .text(text)
                .date(new Date())
                .issuerName(issuerName)
                .issuerId(issuerId)
                .build();

        ensureNotes(player).add(entry);
        playerRepository.replaceNotes(server, player);
        return player;
    }

    public Player addIp(Server server, UUID minecraftUuid, String ipAddress) {
        Optional<Player> playerOpt = findByMinecraftUuid(server, minecraftUuid);
        if (playerOpt.isEmpty()) {
            return null;
        }

        Player player = playerOpt.get();
        addIpToPlayer(player, ipAddress, null);
        playerRepository.replaceIpAddresses(server, player);
        return player;
    }

    public void updateIpGeoData(Server server, String minecraftUuid, String ipAddress, Map<String, Object> ipInfo) {
        if (minecraftUuid == null || minecraftUuid.isBlank() || ipAddress == null || ipAddress.isBlank()) {
            return;
        }

        Player player = playerRepository.findByMinecraftUuid(server, minecraftUuid).orElse(null);
        if (player == null) {
            return;
        }

        IPEntry ipEntry = ensureIpAddresses(player).stream()
                .filter(entry -> ipAddress.equals(entry.getIpAddress()))
                .findFirst()
                .orElse(null);
        if (ipEntry == null) {
            return;
        }

        applyIpMetadata(ipEntry, ipInfo);
        playerRepository.replaceIpAddresses(server, player);
    }

    public Optional<Player> findByMinecraftUuid(Server server, UUID minecraftUuid) {
        return playerRepository.findByMinecraftUuid(server, minecraftUuid);
    }

    private Player newPlayer(UUID minecraftUuid, String username) {
        return Player.builder()
                .id(new ObjectId().toHexString())
                .minecraftUuid(minecraftUuid)
                .usernames(new ArrayList<>(List.of(new UsernameEntry(username, new Date()))))
                .notes(new ArrayList<>())
                .ipAddresses(new ArrayList<>())
                .punishments(new ArrayList<>())
                .data(new HashMap<>())
                .build();
    }

    private void updatePlayerOnLogin(Player player, String username, String ip, Map<String, Object> ipInfo, String skinHash, String serverName) {
        List<UsernameEntry> usernames = ensureUsernames(player);
        String currentUsername = usernames.isEmpty() ? null :
                usernames.get(usernames.size() - 1).username();

        if (!username.equals(currentUsername)) {
            usernames.add(new UsernameEntry(username, new Date()));
        }

        addIpToPlayer(player, ip, ipInfo);
        updatePlayerDataOnLogin(player, skinHash, serverName);
    }

    private void addIpToPlayer(Player player, String ipAddress, Map<String, Object> ipInfo) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return;
        }

        Date now = new Date();
        List<IPEntry> ipAddresses = ensureIpAddresses(player);

        Optional<IPEntry> existingIp = ipAddresses.stream()
                .filter(ip -> ipAddress.equals(ip.getIpAddress()))
                .findFirst();

        if (existingIp.isPresent()) {
            IPEntry entry = existingIp.get();
            if (entry.getLogins() == null) {
                entry.setLogins(new ArrayList<>());
            }
            entry.getLogins().add(now);
            if (ipInfo != null) {
                applyIpMetadata(entry, ipInfo);
            }
            return;
        }

        IPEntry.IPEntryBuilder builder = IPEntry.builder()
                .ipAddress(ipAddress)
                .firstLogin(now)
                .logins(new ArrayList<>(List.of(now)));

        if (ipInfo != null) {
            builder.country((String) ipInfo.get("country"))
                    .region((String) ipInfo.get("region"))
                    .asn((String) ipInfo.get("asn"))
                    .proxy(Boolean.TRUE.equals(ipInfo.get("proxy")))
                    .hosting(Boolean.TRUE.equals(ipInfo.get("hosting")));
        }

        ipAddresses.add(builder.build());
    }

    private void updatePlayerDataOnLogin(Player player, String skinHash, String serverName) {
        Date now = new Date();
        Map<String, Object> data = ensureData(player);
        data.put("lastLogin", now);
        data.put("isOnline", true);

        if (!data.containsKey("firstJoin")) {
            data.put("firstJoin", now);
        }

        if (skinHash != null && !skinHash.isBlank()) {
            data.put("lastSkinHash", skinHash);
        }

        if (serverName != null && !serverName.isBlank()) {
            data.put("lastServer", serverName);
        }
    }

    private void applyIpMetadata(IPEntry ipEntry, Map<String, Object> ipInfo) {
        if (ipInfo == null) {
            return;
        }
        ipEntry.setCountry((String) ipInfo.get("country"));
        ipEntry.setRegion((String) ipInfo.get("region"));
        ipEntry.setAsn((String) ipInfo.get("asn"));
        ipEntry.setProxy(Boolean.TRUE.equals(ipInfo.get("proxy")));
        ipEntry.setHosting(Boolean.TRUE.equals(ipInfo.get("hosting")));
    }

    private List<UsernameEntry> ensureUsernames(Player player) {
        if (player.getUsernames() == null) {
            player.setUsernames(new ArrayList<>());
        }
        return player.getUsernames();
    }

    private List<NoteEntry> ensureNotes(Player player) {
        if (player.getNotes() == null) {
            player.setNotes(new ArrayList<>());
        }
        return player.getNotes();
    }

    private List<IPEntry> ensureIpAddresses(Player player) {
        if (player.getIpAddresses() == null) {
            player.setIpAddresses(new ArrayList<>());
        }
        return player.getIpAddresses();
    }

    private Map<String, Object> ensureData(Player player) {
        if (player.getData() == null) {
            player.setData(new HashMap<>());
        }
        return player.getData();
    }

    private PlayerDetailResponse buildPlayerDetailResponse(Server server, Player player) {
        List<Punishment> punishments = player.getPunishments() != null ? player.getPunishments() : List.of();
        PlayerStatusCalculator.PlayerStatus status = statusCalculator.calculateStatus(server, punishments);

        List<PunishmentResponse> punishmentResponses = punishments.stream()
                .map(p -> toPunishmentResponse(server, p))
                .toList();

        // Strip raw IP addresses from response — only keep metadata (country, region, etc.)
        List<IPEntry> sanitizedIps = (player.getIpAddresses() != null ? player.getIpAddresses() : List.<IPEntry>of()).stream()
                .map(ip -> IPEntry.builder()
                        .ipAddress(null)
                        .country(ip.getCountry())
                        .region(ip.getRegion())
                        .asn(ip.getAsn())
                        .proxy(ip.isProxy())
                        .hosting(ip.isHosting())
                        .firstLogin(ip.getFirstLogin())
                        .logins(ip.getLogins())
                        .build())
                .toList();

        IPEntry latestIp = sanitizedIps.isEmpty() ? null :
                sanitizedIps.get(sanitizedIps.size() - 1);

        String lastServer = player.getData() != null ? (String) player.getData().get("lastServer") : null;

        Object playtimeObj = player.getData() != null ? player.getData().get("totalPlaytimeSeconds") : null;
        double playtimeHours = 0;
        if (playtimeObj instanceof Number) {
            playtimeHours = ((Number) playtimeObj).doubleValue() / 3600.0;
        }

        return new PlayerDetailResponse(
                player.getId(),
                player.getMinecraftUuid().toString(),
                player.getUsernames(),
                player.getNotes(),
                sanitizedIps,
                punishmentResponses,
                player.getData(),
                status.social(),
                status.gameplay(),
                status.socialPoints(),
                status.gameplayPoints(),
                latestIp,
                lastServer,
                playtimeHours
        );
    }

    private PunishmentResponse toPunishmentResponse(Server server, Punishment punishment) {
        Map<String, Object> data = punishment.getData();
        boolean active = statusCalculator.isPunishmentActive(punishment);
        Date expires = statusCalculator.getEffectiveExpiry(punishment);
        int ordinal = punishment.getTypeOrdinal();

        // Compute effective category (BAN, MUTE, or null) using the uniform calculation
        PunishmentType punishmentType = punishmentTypeService.getPunishmentTypeByOrdinal(server, ordinal).orElse(null);
        String effectiveCategory = statusCalculator.getEffectiveCategory(punishmentType, data);

        return new PunishmentResponse(
                punishment.getId(),
                punishmentTypeService.getPunishmentTypeName(server, ordinal),
                ordinal,
                punishment.getIssuerName(),
                punishment.getIssued(),
                punishment.getStarted(),
                punishmentTypeService.isAppealable(server, ordinal),
                data != null ? (String) data.get("reason") : null,
                data != null ? (String) data.get("severity") : null,
                data != null ? (String) data.get("status") : null,
                active,
                expires,
                null,
                null,
                data != null ? (Boolean) data.get("altBlocking") : null,
                data != null ? (Boolean) data.get("wipeAfterExpiry") : null,
                data != null ? (String) data.get("offenseLevel") : null,
                effectiveCategory,
                punishment.getModifications(),
                punishment.getNotes(),
                punishment.getEvidence(),
                punishment.getAttachedTicketIds()
        );
    }

    private String calculatePlayerStatus(Server server, Player player) {
        List<Punishment> punishments = player.getPunishments() != null ? player.getPunishments() : List.of();
        for (Punishment punishment : punishments) {
            if (statusCalculator.isPunishmentActive(punishment)) {
                PunishmentType pt = punishmentTypeService.getPunishmentTypeByOrdinal(server, punishment.getTypeOrdinal()).orElse(null);
                String category = statusCalculator.getEffectiveCategory(pt, punishment.getData());
                if ("BAN".equals(category)) {
                    return "Banned";
                }
            }
        }

        Object isOnline = player.getData() != null ? player.getData().get("isOnline") : null;
        if (Boolean.TRUE.equals(isOnline)) {
            return "Online";
        }
        return "Offline";
    }

    private Date getLastOnline(Player player) {
        if (player.getData() == null) {
            return null;
        }
        Object lastLogin = player.getData().get("lastLogin");
        if (lastLogin instanceof Date) {
            return (Date) lastLogin;
        }
        return null;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

