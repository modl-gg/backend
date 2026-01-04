package gg.modl.backend.player;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.dto.response.PlayerDetailResponse;
import gg.modl.backend.player.dto.response.PlayerSearchResult;
import gg.modl.backend.player.dto.response.PunishmentResponse;
import gg.modl.backend.player.external.IpGeolocationService;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final IpGeolocationService ipGeolocationService;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;

    public List<PlayerSearchResult> searchPlayers(Server server, String searchTerm) {
        MongoTemplate template = getTemplate(server);
        List<PlayerSearchResult> results = new ArrayList<>();

        Query query = new Query();
        if (isUuid(searchTerm)) {
            query.addCriteria(Criteria.where("minecraftUuid").is(searchTerm));
        } else {
            Pattern pattern = Pattern.compile(Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE);
            query.addCriteria(Criteria.where("usernames.username").regex(pattern));
        }
        query.limit(20);

        List<Player> players = template.find(query, Player.class, CollectionName.PLAYERS);

        for (Player player : players) {
            String username = player.getUsernames().isEmpty() ? "Unknown" :
                    player.getUsernames().get(player.getUsernames().size() - 1).username();

            String status = calculatePlayerStatus(server, player);
            Date lastOnline = getLastOnline(player);

            results.add(new PlayerSearchResult(
                    player.getMinecraftUuid().toString(),
                    username,
                    status,
                    lastOnline
            ));
        }

        return results;
    }

    public Optional<PlayerDetailResponse> getPlayerDetails(Server server, UUID minecraftUuid) {
        return findByMinecraftUuid(server, minecraftUuid)
                .map(player -> buildPlayerDetailResponse(server, player));
    }

    public Player createPlayer(Server server, UUID minecraftUuid, String username) {
        MongoTemplate template = getTemplate(server);

        Player player = Player.builder()
                .id(new ObjectId().toHexString())
                .minecraftUuid(minecraftUuid)
                .usernames(new ArrayList<>(List.of(new UsernameEntry(username, new Date()))))
                .notes(new ArrayList<>())
                .ipAddresses(new ArrayList<>())
                .punishments(new ArrayList<>())
                .build();

        return template.save(player, CollectionName.PLAYERS);
    }

    public Player loginPlayer(Server server, UUID minecraftUuid, String username, String ip) {
        MongoTemplate template = getTemplate(server);
        Optional<Player> existingPlayer = findByMinecraftUuid(server, minecraftUuid);

        if (existingPlayer.isPresent()) {
            Player player = existingPlayer.get();
            updatePlayerOnLogin(template, player, username, ip);
            return findByMinecraftUuid(server, minecraftUuid).orElse(player);
        } else {
            Player player = createPlayer(server, minecraftUuid, username);
            addIpToPlayer(template, player, ip);
            updatePlayerDataOnLogin(template, player);
            return findByMinecraftUuid(server, minecraftUuid).orElse(player);
        }
    }

    public Player addUsername(Server server, UUID minecraftUuid, String username) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("minecraftUuid").is(minecraftUuid.toString()));

        UsernameEntry entry = new UsernameEntry(username, new Date());
        Update update = new Update().push("usernames", entry);

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
        return findByMinecraftUuid(server, minecraftUuid).orElse(null);
    }

    public Player addNote(Server server, UUID minecraftUuid, String text, String issuerName, String issuerId) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("minecraftUuid").is(minecraftUuid.toString()));

        NoteEntry entry = new NoteEntry(
                new ObjectId().toHexString(),
                text,
                new Date(),
                issuerName,
                issuerId
        );
        Update update = new Update().push("notes", entry);

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
        return findByMinecraftUuid(server, minecraftUuid).orElse(null);
    }

    public Player addIp(Server server, UUID minecraftUuid, String ipAddress) {
        MongoTemplate template = getTemplate(server);
        Optional<Player> playerOpt = findByMinecraftUuid(server, minecraftUuid);
        if (playerOpt.isEmpty()) {
            return null;
        }

        Player player = playerOpt.get();
        addIpToPlayer(template, player, ipAddress);
        return findByMinecraftUuid(server, minecraftUuid).orElse(null);
    }

    public Optional<Player> findByMinecraftUuid(Server server, UUID minecraftUuid) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("minecraftUuid").is(minecraftUuid.toString()));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);
        return Optional.ofNullable(player);
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }

    private void updatePlayerOnLogin(MongoTemplate template, Player player, String username, String ip) {
        Query query = Query.query(Criteria.where("_id").is(player.getId()));

        String currentUsername = player.getUsernames().isEmpty() ? null :
                player.getUsernames().get(player.getUsernames().size() - 1).username();

        if (!username.equals(currentUsername)) {
            UsernameEntry entry = new UsernameEntry(username, new Date());
            template.updateFirst(query, new Update().push("usernames", entry), Player.class, CollectionName.PLAYERS);
        }

        addIpToPlayer(template, player, ip);
        updatePlayerDataOnLogin(template, player);
    }

    private void addIpToPlayer(MongoTemplate template, Player player, String ipAddress) {
        Query query = Query.query(Criteria.where("_id").is(player.getId()));
        Date now = new Date();

        Optional<IPEntry> existingIp = player.getIpAddresses().stream()
                .filter(ip -> ip.getIpAddress().equals(ipAddress))
                .findFirst();

        if (existingIp.isPresent()) {
            Query ipQuery = Query.query(
                    Criteria.where("_id").is(player.getId())
                            .and("ipAddresses.ipAddress").is(ipAddress)
            );
            Update update = new Update().push("ipAddresses.$.logins", now);
            template.updateFirst(ipQuery, update, Player.class, CollectionName.PLAYERS);
        } else {
            IpGeolocationService.IpGeolocationResult geoResult = ipGeolocationService.getIpInfo(ipAddress);

            IPEntry newIp = IPEntry.builder()
                    .ipAddress(ipAddress)
                    .country(geoResult.country())
                    .region(geoResult.region())
                    .asn(geoResult.asn())
                    .proxy(geoResult.proxy())
                    .hosting(geoResult.hosting())
                    .firstLogin(now)
                    .logins(new ArrayList<>(List.of(now)))
                    .build();

            Update update = new Update().push("ipAddresses", newIp);
            template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
        }
    }

    private void updatePlayerDataOnLogin(MongoTemplate template, Player player) {
        Query query = Query.query(Criteria.where("_id").is(player.getId()));
        Date now = new Date();

        Update update = new Update()
                .set("data.lastLogin", now)
                .set("data.isOnline", true);

        if (player.getData() == null || !player.getData().containsKey("firstJoin")) {
            update.set("data.firstJoin", now);
        }

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
    }

    private PlayerDetailResponse buildPlayerDetailResponse(Server server, Player player) {
        PlayerStatusCalculator.PlayerStatus status = statusCalculator.calculateStatus(server, player.getPunishments());

        List<PunishmentResponse> punishmentResponses = player.getPunishments().stream()
                .map(p -> toPunishmentResponse(server, p))
                .toList();

        IPEntry latestIp = player.getIpAddresses().isEmpty() ? null :
                player.getIpAddresses().get(player.getIpAddresses().size() - 1);

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
                player.getIpAddresses(),
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
        int ordinal = punishment.getType_ordinal();

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
                punishment.getModifications(),
                punishment.getNotes(),
                punishment.getEvidence(),
                punishment.getAttachedTicketIds()
        );
    }

    private String calculatePlayerStatus(Server server, Player player) {
        for (Punishment punishment : player.getPunishments()) {
            if (statusCalculator.isPunishmentActive(punishment)) {
                int ordinal = punishment.getType_ordinal();
                boolean isBan = punishmentTypeService.getPunishmentTypeByOrdinal(server, ordinal)
                        .map(type -> type.isBan())
                        .orElse(false);
                if (isBan) {
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
