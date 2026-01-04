package gg.modl.backend.player.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.dto.response.LinkedAccountResponse;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLinkingService {
    private static final long SIX_HOURS_MS = 6 * 60 * 60 * 1000;

    private final DynamicMongoTemplateProvider mongoProvider;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;

    public List<LinkedAccountResponse> getLinkedAccounts(Server server, UUID playerUuid) {
        MongoTemplate template = getTemplate(server);
        Player player = findPlayerByUuid(template, playerUuid);
        if (player == null) {
            return new ArrayList<>();
        }

        Map<String, Object> data = player.getData();
        if (data == null || !data.containsKey("linkedAccounts")) {
            return new ArrayList<>();
        }

        @SuppressWarnings("unchecked")
        List<String> linkedUuids = (List<String>) data.get("linkedAccounts");
        if (linkedUuids == null || linkedUuids.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> validUuids = linkedUuids.stream()
                .filter(uuid -> {
                    try {
                        UUID.fromString(uuid);
                        return true;
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid UUID in linked accounts: {}", uuid);
                        return false;
                    }
                })
                .toList();

        if (validUuids.isEmpty()) {
            return new ArrayList<>();
        }

        Query batchQuery = new Query(Criteria.where("minecraftUuid").in(validUuids));
        List<Player> linkedPlayers = template.find(batchQuery, Player.class, CollectionName.PLAYERS);

        return linkedPlayers.stream()
                .map(p -> buildLinkedAccountResponse(server, p))
                .toList();
    }

    public LinkingResult findAndLinkAccounts(Server server, UUID playerUuid) {
        MongoTemplate template = getTemplate(server);
        Player player = findPlayerByUuid(template, playerUuid);
        if (player == null) {
            return new LinkingResult(false, "Player not found", 0);
        }

        Set<String> linkedUuids = new HashSet<>();
        Set<String> playerIps = new HashSet<>();

        for (IPEntry ipEntry : player.getIpAddresses()) {
            if (!ipEntry.isProxy()) {
                playerIps.add(ipEntry.getIpAddress());
            }
        }

        if (playerIps.isEmpty()) {
            for (IPEntry ipEntry : player.getIpAddresses()) {
                if (hasRecentLogin(ipEntry, player)) {
                    playerIps.add(ipEntry.getIpAddress());
                }
            }
        }

        if (playerIps.isEmpty()) {
            return new LinkingResult(true, "No valid IPs to match", 0);
        }

        Query query = new Query(Criteria.where("ipAddresses.ipAddress").in(playerIps));
        List<Player> potentialMatches = template.find(query, Player.class, CollectionName.PLAYERS);

        for (Player match : potentialMatches) {
            if (match.getMinecraftUuid().equals(playerUuid)) {
                continue;
            }

            boolean shouldLink = false;
            for (IPEntry matchIp : match.getIpAddresses()) {
                if (playerIps.contains(matchIp.getIpAddress())) {
                    if (!matchIp.isProxy()) {
                        shouldLink = true;
                        break;
                    } else {
                        if (hasRecentMatchingLogin(player, match, matchIp.getIpAddress())) {
                            shouldLink = true;
                            break;
                        }
                    }
                }
            }

            if (shouldLink) {
                linkedUuids.add(match.getMinecraftUuid().toString());
            }
        }

        if (!linkedUuids.isEmpty()) {
            updateLinkedAccounts(template, player, linkedUuids);

            for (String linkedUuid : linkedUuids) {
                try {
                    UUID uuid = UUID.fromString(linkedUuid);
                    Player linkedPlayer = findPlayerByUuid(template, uuid);
                    if (linkedPlayer != null) {
                        Set<String> reverseLinks = new HashSet<>();
                        reverseLinks.add(playerUuid.toString());
                        updateLinkedAccounts(template, linkedPlayer, reverseLinks);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return new LinkingResult(true, "Linking complete", linkedUuids.size());
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }

    private Player findPlayerByUuid(MongoTemplate template, UUID uuid) {
        Query query = Query.query(Criteria.where("minecraftUuid").is(uuid.toString()));
        return template.findOne(query, Player.class, CollectionName.PLAYERS);
    }

    private LinkedAccountResponse buildLinkedAccountResponse(Server server, Player player) {
        String username = player.getUsernames().isEmpty() ? "Unknown" :
                player.getUsernames().get(player.getUsernames().size() - 1).username();

        int activeBans = 0;
        int activeMutes = 0;

        for (Punishment punishment : player.getPunishments()) {
            if (statusCalculator.isPunishmentActive(punishment)) {
                int ordinal = punishment.getType_ordinal();
                boolean isBan = punishmentTypeService.getPunishmentTypeByOrdinal(server, ordinal)
                        .map(type -> type.isBan())
                        .orElse(false);
                boolean isMute = punishmentTypeService.getPunishmentTypeByOrdinal(server, ordinal)
                        .map(type -> type.isMute())
                        .orElse(false);
                if (isBan) {
                    activeBans++;
                } else if (isMute) {
                    activeMutes++;
                }
            }
        }

        Date lastLinkedUpdate = null;
        if (player.getData() != null) {
            Object lastUpdate = player.getData().get("lastLinkedUpdate");
            if (lastUpdate instanceof Date) {
                lastLinkedUpdate = (Date) lastUpdate;
            }
        }

        return new LinkedAccountResponse(
                player.getMinecraftUuid().toString(),
                username,
                activeBans,
                activeMutes,
                lastLinkedUpdate
        );
    }

    private boolean hasRecentLogin(IPEntry ipEntry, Player player) {
        if (ipEntry.getLogins() == null || ipEntry.getLogins().isEmpty()) {
            return false;
        }
        Date lastLogin = ipEntry.getLogins().get(ipEntry.getLogins().size() - 1);
        return System.currentTimeMillis() - lastLogin.getTime() < SIX_HOURS_MS;
    }

    private boolean hasRecentMatchingLogin(Player player1, Player player2, String ipAddress) {
        Optional<IPEntry> ip1 = player1.getIpAddresses().stream()
                .filter(ip -> ip.getIpAddress().equals(ipAddress))
                .findFirst();

        Optional<IPEntry> ip2 = player2.getIpAddresses().stream()
                .filter(ip -> ip.getIpAddress().equals(ipAddress))
                .findFirst();

        if (ip1.isEmpty() || ip2.isEmpty()) {
            return false;
        }

        List<Date> logins1 = ip1.get().getLogins();
        List<Date> logins2 = ip2.get().getLogins();

        if (logins1 == null || logins1.isEmpty() || logins2 == null || logins2.isEmpty()) {
            return false;
        }

        for (Date login1 : logins1) {
            for (Date login2 : logins2) {
                if (Math.abs(login1.getTime() - login2.getTime()) < SIX_HOURS_MS) {
                    return true;
                }
            }
        }

        return false;
    }

    private void updateLinkedAccounts(MongoTemplate template, Player player, Set<String> newLinks) {
        Query query = Query.query(Criteria.where("_id").is(player.getId()));

        @SuppressWarnings("unchecked")
        List<String> existingLinks = player.getData() != null ?
                (List<String>) player.getData().get("linkedAccounts") : null;

        Set<String> allLinks = new HashSet<>();
        if (existingLinks != null) {
            allLinks.addAll(existingLinks);
        }
        allLinks.addAll(newLinks);

        Update update = new Update()
                .set("data.linkedAccounts", new ArrayList<>(allLinks))
                .set("data.lastLinkedUpdate", new Date());

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
    }

    public record LinkingResult(boolean success, String message, int linkedAccountsFound) {
    }
}
