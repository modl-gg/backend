package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.dto.response.LinkedAccountResponse;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLinkingService {
    private final PlayerMongoRepository playerRepository;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private static final long SIX_HOURS_MS = 6 * 60 * 60 * 1000;

    public List<LinkedAccountResponse> getLinkedAccounts(Server server, UUID playerUuid) {
        Player player = findPlayerByUuid(server, playerUuid);
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
                } catch (IllegalArgumentException exception) {
                    log.warn("Invalid UUID in linked accounts: {}", uuid);
                    return false;
                }
            })
            .toList();

        if (validUuids.isEmpty()) {
            return new ArrayList<>();
        }

        List<Player> linkedPlayers = playerRepository.findByMinecraftUuids(server, validUuids);

        return linkedPlayers.stream()
            .map(playerEntry -> buildLinkedAccountResponse(server, playerEntry))
            .toList();
    }

    private Player findPlayerByUuid(Server server, UUID uuid) {
        return playerRepository.findByMinecraftUuid(server, uuid).orElse(null);
    }

    private LinkedAccountResponse buildLinkedAccountResponse(Server server, Player player) {
        String username = PlayerDataUtils.extractLatestUsername(player.getUsernames());

        int activeBans = 0;
        int activeMutes = 0;
        for (Punishment punishment : player.getPunishments()) {
            if (statusCalculator.isPunishmentActive(punishment)) {
                PunishmentType punishmentType = punishmentTypeService.getPunishmentTypeByOrdinal(server, punishment.getTypeOrdinal()).orElse(null);
                String category = statusCalculator.getEffectiveCategory(punishmentType, punishment.getData());
                if ("BAN".equals(category)) {
                    activeBans++;
                } else if ("MUTE".equals(category)) {
                    activeMutes++;
                }
            }
        }

        Date lastLinkedUpdate = null;
        if (player.getData() != null) {
            Object lastUpdate = player.getData().get("lastLinkedUpdate");
            if (lastUpdate instanceof Date date) {
                lastLinkedUpdate = date;
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

    public LinkingResult findAndLinkAccounts(Server server, UUID playerUuid) {
        Player player = findPlayerByUuid(server, playerUuid);
        if (player == null) {
            return new LinkingResult(false, "Player not found", 0);
        }

        Set<String> playerIps = new HashSet<>();

        for (IPEntry ipEntry : player.getIpAddresses()) {
            if (!ipEntry.isProxy()) {
                playerIps.add(ipEntry.getIpAddress());
            }
        }

        if (playerIps.isEmpty()) {
            for (IPEntry ipEntry : player.getIpAddresses()) {
                if (hasRecentLogin(ipEntry)) {
                    playerIps.add(ipEntry.getIpAddress());
                }
            }
        }

        if (playerIps.isEmpty()) {
            return new LinkingResult(true, "No valid IPs to match", 0);
        }

        List<Player> potentialMatches = playerRepository.findByIpAddresses(server, playerIps);

        List<Player> linkedPlayers = new ArrayList<>();
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
                    }
                    if (hasRecentMatchingLogin(player, match, matchIp.getIpAddress())) {
                        shouldLink = true;
                        break;
                    }
                }
            }

            if (shouldLink) {
                linkedPlayers.add(match);
            }
        }

        if (!linkedPlayers.isEmpty()) {
            Set<String> linkedUuids = new HashSet<>();
            for (Player linked : linkedPlayers) {
                linkedUuids.add(linked.getMinecraftUuid().toString());
            }

            updateLinkedAccounts(server, player, linkedUuids);

            for (Player linkedPlayer : linkedPlayers) {
                updateLinkedAccounts(server, linkedPlayer, Set.of(playerUuid.toString()));
            }
        }

        return new LinkingResult(true, "Linking complete", linkedPlayers.size());
    }

    private boolean hasRecentLogin(IPEntry ipEntry) {
        if (ipEntry.getLogins() == null || ipEntry.getLogins().isEmpty()) {
            return false;
        }
        Date lastLogin = ipEntry.getLogins().get(ipEntry.getLogins().size() - 1);
        return System.currentTimeMillis() - lastLogin.getTime() < SIX_HOURS_MS;
    }

    private boolean hasRecentMatchingLogin(Player player1, Player player2, String ipAddress) {
        Optional<IPEntry> ip1 = player1.getIpAddresses()
            .stream()
            .filter(ip -> ip.getIpAddress().equals(ipAddress))
            .findFirst();
        Optional<IPEntry> ip2 = player2.getIpAddresses()
            .stream()
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

    private void updateLinkedAccounts(Server server, Player player, Set<String> newLinks) {
        Map<String, Object> data = player.getData();
        if (data == null) {
            data = new LinkedHashMap<>();
            player.setData(data);
        }

        @SuppressWarnings("unchecked")
        List<String> existingLinks = (List<String>) data.get("linkedAccounts");

        Set<String> allLinks = new HashSet<>();
        if (existingLinks != null) {
            allLinks.addAll(existingLinks);
        }
        allLinks.addAll(newLinks);

        data.put("linkedAccounts", new ArrayList<>(allLinks));
        data.put("lastLinkedUpdate", new Date());
        playerRepository.replaceLinkedAccounts(server, player);
    }

    public record LinkingResult(boolean success, String message, int linkedAccountsFound) {
    }
}
