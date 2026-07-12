package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.infrastructure.util.PaginationHelper;
import gg.modl.backend.infrastructure.util.UuidUtils;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.EnforcementCategory;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.dto.response.LinkedAccountsResult;
import gg.modl.backend.player.dto.response.PlayerFetchResult;
import gg.modl.backend.player.dto.response.PlayerLookupResult;
import gg.modl.backend.player.dto.response.PlayerProfileResult;
import gg.modl.backend.player.dto.response.PunishmentView;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeIndex;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerLookupService {
    private final PlayerMongoRepository playerRepository;
    private final PunishmentTypeService punishmentTypeService;
    private final MojangApiService mojangApiService;
    private final PlayerStatusCalculator statusCalculator;
    private final IssuerNameResolver issuerNameResolver;
    private final PlayerService playerService;

    public PlayerProfileResult getPlayerByUuid(Server server, String uuid, Integer punishmentLimit, Integer noteLimit) {
        Player player = findPlayerByUuid(server, uuid).orElse(null);
        if (player == null) {
            return new PlayerProfileResult.NotFound("Player not found");
        }

        List<PunishmentType> punishmentTypes = punishmentTypeService.getPunishmentTypes(server);
        return new PlayerProfileResult.Found(toPlayerProfile(server, player, punishmentTypes, punishmentLimit, noteLimit));
    }

    public PlayerFetchResult getPlayerByMinecraftUuid(Server server, String minecraftUuid, boolean queryMojang) {
        if (minecraftUuid == null || minecraftUuid.isBlank()) {
            return new PlayerFetchResult.InvalidRequest("minecraftUuid parameter required");
        }

        Player player = findPlayerByUuid(server, minecraftUuid).orElse(null);
        if (player == null && queryMojang) {
            Optional<MojangApiService.MojangProfile> profile = mojangApiService.lookupByUuid(minecraftUuid);
            if (profile.isPresent()) {
                return new PlayerFetchResult.Found("Player found via Mojang", toMojangProfile(profile.get()));
            }
        }

        if (player == null) {
            return new PlayerFetchResult.NotFound("Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return new PlayerFetchResult.Found("Player found", toPlayerProfile(server, player, types));
    }

    public PlayerFetchResult getPlayerByUsername(Server server, String username, boolean queryMojang) {
        Player player = findByUsername(server, username).orElse(null);
        if (player == null && queryMojang) {
            Optional<MojangApiService.MojangProfile> profile = mojangApiService.lookupByUsername(username);
            if (profile.isPresent()) {
                return new PlayerFetchResult.Found("Player found via Mojang", toMojangProfile(profile.get()));
            }
        }

        if (player == null) {
            return new PlayerFetchResult.NotFound("Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return new PlayerFetchResult.Found("Player found", toPlayerProfile(server, player, types));
    }

    public PlayerLookupResult lookupPlayer(Server server, String query, boolean shouldQueryMojang) {
        boolean isUuid = looksLikeUuid(query);
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
                return new PlayerLookupResult.Found("Player found via Mojang", lookupData);
            }
        }

        if (player == null) {
            return new PlayerLookupResult.NotFound("Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return new PlayerLookupResult.Found("Player found", buildLookupResponse(server, player, types));
    }

    public PlayerProfileResult lookupProfile(Server server, String query, boolean shouldQueryMojang, Integer punishmentLimit, Integer noteLimit) {
        boolean isUuid = looksLikeUuid(query);
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
            return new PlayerProfileResult.NotFound("Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return new PlayerProfileResult.Found(toPlayerProfile(server, player, types, punishmentLimit, noteLimit));
    }

    Map<String, Object> toPlayerProfile(Server server, Player player, List<PunishmentType> punishmentTypes) {
        return toPlayerProfile(server, player, punishmentTypes, null, null);
    }

    Map<String, Object> toPlayerProfile(Server server, Player player, List<PunishmentType> punishmentTypes, Integer punishmentLimit, Integer noteLimit) {
        return toPlayerProfile(server, player, punishmentTypes, punishmentLimit, noteLimit,
            issuerNameResolver.resolveForPunishments(server, safePunishments(player)));
    }

    Map<String, Object> toPlayerProfile(Server server, Player player, List<PunishmentType> punishmentTypes, Integer punishmentLimit, Integer noteLimit, Map<String, String> resolvedIssuers) {
        List<Map<String, Object>> usernames = safeUsernames(player).stream()
            .map(username -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("username", username.username());
                entry.put("date", username.date());
                return entry;
            })
            .toList();

        List<NoteEntry> allNotes = safeNotes(player);
        List<NoteEntry> limitedNotes = limitNewestFirst(allNotes, noteLimit,
            Comparator.comparing(NoteEntry::getDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<Map<String, Object>> notes = limitedNotes.stream()
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

        List<Punishment> allPunishments = safePunishments(player);
        List<Punishment> limitedPunishments = limitNewestFirst(allPunishments, punishmentLimit,
            Comparator.comparing(Punishment::getIssued, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<PunishmentView> punishments = limitedPunishments.stream()
            .map(punishment -> PunishmentMapper.toPunishmentView(punishment, punishmentTypes, resolvedIssuers))
            .toList();

        List<Map<String, Object>> pendingNotifications = player.data().pendingNotifications();

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", player.getId());
        profile.put("minecraftUuid", player.getMinecraftUuid().toString());
        profile.put("usernames", usernames);
        profile.put("notes", notes);
        profile.put("ipAddresses", ipAddresses);
        profile.put("punishments", punishments);
        profile.put("pendingNotifications", pendingNotifications);
        profile.put("data", player.data().asMap());
        if (punishmentLimit != null) {
            profile.put("punishmentCount", allPunishments.size());
        }
        if (noteLimit != null) {
            profile.put("noteCount", allNotes.size());
        }
        return profile;
    }

    private Map<String, Object> buildLookupResponse(Server server, Player player, List<PunishmentType> types) {
        String currentUsername = PlayerDataUtils.extractLatestUsername(safeUsernames(player));
        List<String> previousUsernames = player.getUsernames()
            .stream()
            .map(UsernameEntry::username)
            .skip(1)
            .toList();

        Date firstSeen = player.getUsernames().isEmpty() ? null : player.getUsernames().get(0).date();
        Date lastSeen = player.getUsernames().isEmpty() ? null : player.getUsernames().get(player.getUsernames().size() - 1).date();

        int totalPunishments = player.getPunishments().size();
        int activePunishments = (int) player.getPunishments()
            .stream()
            .filter(statusCalculator::isPunishmentActive)
            .count();

        Map<Integer, PunishmentType> typesByOrdinal = PunishmentTypeIndex.byOrdinal(types);

        int bans = 0;
        int mutes = 0;
        int kicks = 0;
        int warnings = 0;
        for (Punishment punishment : player.getPunishments()) {
            PunishmentType type = typesByOrdinal.get(punishment.getTypeOrdinal());
            if (type != null && type.isKick()) {
                kicks++;
                continue;
            }
            String category = statusCalculator.getEffectiveCategory(punishment, typesByOrdinal);
            if (EnforcementCategory.BAN.name().equals(category)) {
                bans++;
            } else if (EnforcementCategory.MUTE.name().equals(category)) {
                mutes++;
            } else {
                warnings++;
            }
        }

        List<Map<String, Object>> recentPunishments = player.getPunishments()
            .stream()
            .sorted(Comparator.comparing(Punishment::getIssued, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(5)
            .map(punishment -> {
                PunishmentType matchedType = typesByOrdinal.get(punishment.getTypeOrdinal());
                String typeName = matchedType != null ? matchedType.getName() : "Unknown";

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
        data.put("isOnline", player.data().isOnline());
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

    private Optional<Player> findPlayerByUuid(Server server, String uuid) {
        return playerRepository.findByMinecraftUuid(server, UuidUtils.normalize(uuid));
    }

    private Optional<Player> findByUsername(Server server, String username) {
        return playerService.findBestByUsername(server, username);
    }

    private boolean looksLikeUuid(String query) {
        return query.contains("-") && query.length() == 36;
    }

    private List<UsernameEntry> safeUsernames(Player player) {
        return player.getUsernames() != null ? player.getUsernames() : List.of();
    }

    private List<NoteEntry> safeNotes(Player player) {
        return player.getNotes() != null ? player.getNotes() : List.of();
    }

    private List<Punishment> safePunishments(Player player) {
        return player.getPunishments() != null ? player.getPunishments() : List.of();
    }

    private List<IPEntry> safeIpAddresses(Player player) {
        return player.getIpAddresses() != null ? player.getIpAddresses() : List.of();
    }

    private <T> List<T> limitNewestFirst(List<T> values, Integer limit, Comparator<? super T> newestFirst) {
        if (limit == null) {
            return values;
        }
        return values.stream()
            .sorted(newestFirst)
            .limit(Math.max(0, limit))
            .toList();
    }


    public LinkedAccountsResult getLinkedAccounts(Server server, String uuid, Integer page, Integer limit) {
        LinkedAccountsResult fullResult = getLinkedAccountsFull(server, uuid);
        if (!(fullResult instanceof LinkedAccountsResult.Found found) || page == null || limit == null) {
            return fullResult;
        }

        PaginationHelper.PageResult<Map<String, Object>> result = PaginationHelper.paginate(found.linkedAccounts(), page, limit);
        return new LinkedAccountsResult.Found(result.items(), result.totalCount(), result.page(), result.hasMore());
    }

    private LinkedAccountsResult getLinkedAccountsFull(Server server, String uuid) {
        Player player = findPlayerByUuid(server, uuid).orElse(null);
        if (player == null) {
            return new LinkedAccountsResult.NotFound("Player not found");
        }

        List<String> ips = safeIpAddresses(player).stream()
            .map(IPEntry::getIpAddress)
            .toList();

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        Set<String> addedUuids = new HashSet<>();
        List<Player> linkedPlayers = new ArrayList<>();

        if (!ips.isEmpty()) {
            for (Player related : playerRepository.findByIpAddressesExcludingUuid(server, ips, UuidUtils.normalize(uuid), 20)) {
                linkedPlayers.add(related);
                addedUuids.add(related.getMinecraftUuid().toString());
            }
        }

        List<String> storedLinkedUuids = player.data().linkedAccountUuids();
        List<String> missingUuids = storedLinkedUuids.stream()
            .filter(linkedUuid -> !addedUuids.contains(linkedUuid))
            .toList();
        if (!missingUuids.isEmpty()) {
            linkedPlayers.addAll(playerRepository.findByMinecraftUuids(server, missingUuids));
        }

        Map<String, String> resolvedIssuers = issuerNameResolver.resolveForPlayers(server, linkedPlayers);
        List<Map<String, Object>> linkedAccounts = linkedPlayers.stream()
            .map(linked -> toPlayerProfile(server, linked, types, null, null, resolvedIssuers))
            .toList();

        return new LinkedAccountsResult.Found(linkedAccounts, null, null, null);
    }
}
