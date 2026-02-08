package gg.modl.backend.player.service;

import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlayerStatusCalculator {
    private final PunishmentTypeService punishmentTypeService;

    public PlayerStatus calculateStatus(Server server, List<Punishment> punishments) {
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);

        int socialPoints = 0;
        int gameplayPoints = 0;

        for (Punishment punishment : punishments) {
            if (!isPunishmentActive(punishment)) {
                continue;
            }

            int typeOrdinal = punishment.getType_ordinal();
            Map<String, Object> data = punishment.getData();
            String severity = data != null ? (String) data.get("severity") : null;

            Optional<PunishmentType> typeOpt = findTypeByOrdinal(types, typeOrdinal);
            if (typeOpt.isEmpty()) {
                continue;
            }

            PunishmentType type = typeOpt.get();
            int points = type.getPointsForSeverity(severity != null ? severity : "regular");

            if (type.isSocial()) {
                socialPoints += points;
            } else if (type.isGameplay()) {
                gameplayPoints += points;
            }
        }

        String socialStatus = getStatusFromPoints(socialPoints);
        String gameplayStatus = getStatusFromPoints(gameplayPoints);

        return new PlayerStatus(socialStatus, gameplayStatus, socialPoints, gameplayPoints);
    }

    public boolean isPunishmentActive(Punishment punishment) {
        String pId = punishment.getId();

        // Kicks (ordinal 0) are instant and never considered "active"
        if (punishment.getType_ordinal() == 0) {
            log.info("[isPunishmentActive] {} -> false (kick)", pId);
            return false;
        }

        Map<String, Object> data = punishment.getData();
        if (data == null) {
            log.info("[isPunishmentActive] {} -> false (no data)", pId);
            return false;
        }

        // Queued punishments (status = "Unstarted") are not yet active
        Object statusObj = data.get("status");
        String status = statusObj instanceof String ? (String) statusObj : null;
        if ("Unstarted".equals(status)) {
            log.info("[isPunishmentActive] {} -> false (Unstarted)", pId);
            return false;
        }

        for (PunishmentModification mod : punishment.getModifications()) {
            String type = mod.type();
            if ("MANUAL_PARDON".equals(type) || "APPEAL_ACCEPT".equals(type)) {
                log.info("[isPunishmentActive] {} -> false (pardoned: {})", pId, type);
                return false;
            }
        }

        // Check legacy "expires" field first
        Object expiresObj = data.get("expires");
        if (expiresObj != null) {
            Date expires;
            if (expiresObj instanceof Date) {
                expires = (Date) expiresObj;
            } else if (expiresObj instanceof Long) {
                expires = new Date((Long) expiresObj);
            } else {
                log.info("[isPunishmentActive] {} -> true (expires field non-date: {})", pId, expiresObj.getClass().getSimpleName());
                return true;
            }

            if (expires.before(new Date())) {
                log.info("[isPunishmentActive] {} -> false (expired: {})", pId, expires);
                return false;
            }
            log.info("[isPunishmentActive] {} -> true (not expired yet: {})", pId, expires);
            return true;
        }

        // Check duration-based expiry
        Date effectiveExpiry = getEffectiveExpiry(punishment);
        if (effectiveExpiry != null && effectiveExpiry.before(new Date())) {
            log.info("[isPunishmentActive] {} -> false (duration expired: {})", pId, effectiveExpiry);
            return false;
        }

        log.info("[isPunishmentActive] {} -> true (active, ordinal={}, started={}, data.status={}, data.keys={})",
                pId, punishment.getType_ordinal(), punishment.getStarted(), statusObj, data.keySet());
        return true;
    }

    public Date getEffectiveExpiry(Punishment punishment) {
        Map<String, Object> data = punishment.getData();
        if (data == null) {
            return null;
        }

        Long duration = null;
        for (PunishmentModification mod : punishment.getModifications()) {
            if (mod.effectiveDuration() != null) {
                duration = mod.effectiveDuration();
            }
        }

        if (duration == null) {
            Object durationObj = data.get("duration");
            if (durationObj instanceof Long) {
                duration = (Long) durationObj;
            } else if (durationObj instanceof Integer) {
                duration = ((Integer) durationObj).longValue();
            } else if (durationObj instanceof Double) {
                duration = ((Double) durationObj).longValue();
            } else if (durationObj instanceof Number) {
                // Catch-all for any other numeric type
                duration = ((Number) durationObj).longValue();
            }
        }

        // null, 0, or negative (-1L) indicates permanent (no expiry)
        if (duration == null || duration <= 0) {
            return null;
        }

        Date started = punishment.getStarted() != null ? punishment.getStarted() : punishment.getIssued();
        return new Date(started.getTime() + duration);
    }

    private Optional<PunishmentType> findTypeByOrdinal(List<PunishmentType> types, int ordinal) {
        return types.stream()
                .filter(t -> t.getOrdinal() == ordinal)
                .findFirst();
    }

    private String getStatusFromPoints(int points) {
        if (points == 0) {
            return "Good";
        } else if (points <= 2) {
            return "Warning";
        } else if (points <= 5) {
            return "Restricted";
        } else {
            return "Banned";
        }
    }

    public record PlayerStatus(
            String social,
            String gameplay,
            int socialPoints,
            int gameplayPoints
    ) {
    }
}
