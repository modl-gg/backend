package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IssuerNameResolver {

    private final StaffMongoRepository staffRepository;

    public String resolve(@Nullable String issuerId, @Nullable String issuerName, Server server) {
        if (issuerId != null) {
            Staff staff = staffRepository.findById(server, issuerId).orElse(null);
            if (staff != null && staff.getUsername() != null) {
                return staff.getUsername();
            }
            return issuerName != null ? issuerName : "Unknown Staff";
        }
        if (issuerName != null) {
            return issuerName;
        }
        return "Console";
    }

    public Map<String, String> batchResolve(Set<String> issuerIds, Server server) {
        if (issuerIds == null || issuerIds.isEmpty()) {
            return Map.of();
        }
        return staffRepository.findUsernamesByIds(server, issuerIds);
    }

    public Map<String, String> resolveForPunishments(Server server, Collection<Punishment> punishments) {
        Set<String> ids = new HashSet<>();
        for (Punishment punishment : punishments) {
            ids.addAll(PunishmentQueryService.collectIssuerIds(punishment));
        }
        return batchResolve(ids, server);
    }

    public Map<String, String> resolveForPlayers(Server server, Collection<Player> players) {
        Set<String> ids = new HashSet<>();
        for (Player player : players) {
            List<Punishment> punishments = player.getPunishments();
            if (punishments == null) {
                continue;
            }
            for (Punishment punishment : punishments) {
                ids.addAll(PunishmentQueryService.collectIssuerIds(punishment));
            }
        }
        return batchResolve(ids, server);
    }
}
